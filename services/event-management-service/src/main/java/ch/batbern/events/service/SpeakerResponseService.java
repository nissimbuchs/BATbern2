package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerResponsePreferences;
import ch.batbern.events.dto.SpeakerResponseRequest;
import ch.batbern.events.dto.SpeakerResponseResult;
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.exception.AlreadyRespondedException;
import ch.batbern.events.exception.InvalidTokenException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.domain.SpeakerAvailability;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.repository.SpeakerRepository;
import ch.batbern.shared.events.SpeakerResponseReceivedEvent;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.types.SpeakerResponseType;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for processing speaker responses to event invitations.
 * Story 6.2a: Invitation Response Portal
 *
 * Handles Accept, Decline, and Tentative responses from speakers
 * accessed through magic link tokens.
 */
@Service
@RequiredArgsConstructor
public class SpeakerResponseService {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerResponseService.class);

    private final SpeakerPoolRepository speakerPoolRepository;
    private final SpeakerRepository speakerRepository;
    private final EventRepository eventRepository;
    private final MagicLinkService magicLinkService;
    private final ApplicationEventPublisher eventPublisher;
    private final OrganizerNotificationService notificationService;
    private final UserApiClient userApiClient;

    @Value("${app.base-url:http://localhost:8100}")
    private String appBaseUrl;

    /**
     * Process a speaker's response to an invitation.
     *
     * @param request the response request containing token, response type, and optional preferences
     * @return the result with confirmation details and next steps
     * @throws InvalidTokenException if token is invalid/expired/used
     * @throws ValidationException if request validation fails
     * @throws AlreadyRespondedException if speaker already responded
     */
    @Transactional
    public SpeakerResponseResult processResponse(SpeakerResponseRequest request) {
        LOG.info("Processing speaker response: type={}", request.getResponse());

        // Step 1: Validate token
        TokenValidationResult tokenResult = magicLinkService.validateToken(request.getToken());
        validateTokenResult(tokenResult);

        // Step 2: Load speaker pool entry
        SpeakerPool speaker = speakerPoolRepository.findById(tokenResult.speakerPoolId())
                .orElseThrow(() -> InvalidTokenException.notFound());

        // Step 3: Check if already responded (not applicable for tentative speakers)
        checkAlreadyResponded(speaker);

        // Step 4: Validate request based on response type
        validateRequest(request);

        // Step 5: Load event for context
        Event event = eventRepository.findById(speaker.getEventId())
                .orElseThrow(() -> new IllegalStateException("Event not found for speaker pool"));

        // Step 6: Process based on response type
        switch (request.getResponse()) {
            case ACCEPT -> processAcceptResponse(speaker, request);
            case DECLINE -> processDeclineResponse(speaker, request);
            case TENTATIVE -> processTentativeResponse(speaker, request);
        }

        // Step 7: Save updated speaker
        speaker = speakerPoolRepository.save(speaker);

        // Step 8: Publish domain event
        publishResponseEvent(speaker, event, request);

        // Step 9: Notify organizer
        notificationService.notifyOrganizerOfResponse(speaker, event, request.getResponse());

        // Step 10: Build and return result
        return buildResult(speaker, event, request.getResponse());
    }

    /**
     * Validate token result and throw appropriate exception if invalid.
     */
    private void validateTokenResult(TokenValidationResult result) {
        if (!result.valid()) {
            String errorCode = result.error();
            throw switch (errorCode) {
                case "NOT_FOUND" -> InvalidTokenException.notFound();
                case "EXPIRED" -> InvalidTokenException.expired();
                case "ALREADY_USED" -> InvalidTokenException.alreadyUsed();
                default -> new InvalidTokenException(errorCode);
            };
        }
    }

    /**
     * Check if speaker has already responded (ACCEPTED or DECLINED).
     * Tentative speakers can still respond.
     */
    private void checkAlreadyResponded(SpeakerPool speaker) {
        SpeakerWorkflowState status = speaker.getStatus();

        if (status == SpeakerWorkflowState.ACCEPTED) {
            throw new AlreadyRespondedException(status, speaker.getAcceptedAt());
        }

        if (status == SpeakerWorkflowState.DECLINED) {
            throw new AlreadyRespondedException(status, speaker.getDeclinedAt());
        }
        // INVITED (including tentative) speakers can respond
    }

    /**
     * Validate request based on response type.
     * - DECLINE requires a reason
     * - TENTATIVE requires a reason
     * - ACCEPT does not require a reason
     */
    private void validateRequest(SpeakerResponseRequest request) {
        SpeakerResponseType responseType = request.getResponse();
        String reason = request.getReason();

        if (responseType == SpeakerResponseType.DECLINE) {
            if (reason == null || reason.isBlank()) {
                throw new ValidationException("Reason is required for decline");
            }
        }

        if (responseType == SpeakerResponseType.TENTATIVE) {
            if (reason == null || reason.isBlank()) {
                throw new ValidationException("Reason is required for tentative response");
            }
        }
    }

    /**
     * Process ACCEPT response.
     * - Create/link User account via UserApiClient
     * - Create Speaker record if needed
     * - Transition to ACCEPTED state
     * - Set accepted_at timestamp
     * - Clear tentative flag
     * - Store preferences if provided
     * - Consume token (single-use)
     */
    private void processAcceptResponse(SpeakerPool speaker, SpeakerResponseRequest request) {
        // Create/get User account for the speaker (enables profile management)
        String username = createOrLinkUser(speaker);
        speaker.setUsername(username);

        // Create Speaker record if it doesn't exist
        createSpeakerIfNeeded(username);

        speaker.setStatus(SpeakerWorkflowState.ACCEPTED);
        speaker.setAcceptedAt(Instant.now());
        speaker.setIsTentative(false);
        speaker.setTentativeReason(null);

        // Store preferences if provided
        if (request.getPreferences() != null) {
            storePreferences(speaker, request.getPreferences());
        }

        // Consume token (single-use for ACCEPT)
        magicLinkService.markTokenAsUsed(request.getToken());

        LOG.info("Speaker {} (username: {}) accepted invitation for event {}",
                speaker.getSpeakerName(), username, speaker.getEventId());
    }

    /**
     * Create or link a User account for the speaker.
     * Uses the speaker's email and name to create an anonymous user.
     *
     * @param speaker the speaker pool entry
     * @return the username of the created/linked user
     */
    private String createOrLinkUser(SpeakerPool speaker) {
        if (speaker.getEmail() == null || speaker.getEmail().isBlank()) {
            throw new ValidationException("Speaker email is required for acceptance");
        }

        // Split speaker name into first/last name
        String[] nameParts = splitName(speaker.getSpeakerName());
        String firstName = nameParts[0];
        String lastName = nameParts[1];

        GetOrCreateUserRequest userRequest = new GetOrCreateUserRequest();
        userRequest.setFirstName(firstName);
        userRequest.setLastName(lastName);
        userRequest.setEmail(speaker.getEmail());
        userRequest.setCognitoSync(false); // Anonymous user

        GetOrCreateUserResponse userResponse = userApiClient.getOrCreateUser(userRequest);
        LOG.info("Got/created user for speaker: email={}, username={}, created={}",
                speaker.getEmail(), userResponse.getUsername(), userResponse.getCreated());

        return userResponse.getUsername();
    }

    /**
     * Split a full name into first and last name parts.
     */
    private String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{"Speaker", "Unknown"};
        }
        String[] parts = fullName.trim().split("\\s+", 2);
        if (parts.length == 1) {
            return new String[]{parts[0], ""};
        }
        return parts;
    }

    /**
     * Create a Speaker record if it doesn't exist for this username.
     */
    private void createSpeakerIfNeeded(String username) {
        if (speakerRepository.findByUsername(username).isEmpty()) {
            Speaker speaker = Speaker.builder()
                    .username(username)
                    .availability(SpeakerAvailability.AVAILABLE)
                    .workflowState(SpeakerWorkflowState.ACCEPTED)
                    .build();
            speakerRepository.save(speaker);
            LOG.info("Created Speaker record for username: {}", username);
        }
    }

    /**
     * Process DECLINE response.
     * - Transition to DECLINED state (terminal)
     * - Set declined_at timestamp
     * - Store decline reason
     * - Consume token (single-use)
     */
    private void processDeclineResponse(SpeakerPool speaker, SpeakerResponseRequest request) {
        speaker.setStatus(SpeakerWorkflowState.DECLINED);
        speaker.setDeclinedAt(Instant.now());
        speaker.setDeclineReason(request.getReason());

        // Consume token (single-use for DECLINE)
        magicLinkService.markTokenAsUsed(request.getToken());

        LOG.info("Speaker {} declined invitation for event {}. Reason: {}",
                speaker.getSpeakerName(), speaker.getEventId(), request.getReason());
    }

    /**
     * Process TENTATIVE response.
     * - Keep INVITED state
     * - Set is_tentative flag
     * - Store tentative reason
     * - DO NOT consume token (speaker can return and change response)
     */
    private void processTentativeResponse(SpeakerPool speaker, SpeakerResponseRequest request) {
        // Keep status as INVITED
        speaker.setIsTentative(true);
        speaker.setTentativeReason(request.getReason());

        // DO NOT consume token for TENTATIVE - speaker can return

        LOG.info("Speaker {} marked tentative for event {}. Reason: {}",
                speaker.getSpeakerName(), speaker.getEventId(), request.getReason());
    }

    /**
     * Store speaker preferences on the speaker pool entity.
     */
    private void storePreferences(SpeakerPool speaker, SpeakerResponsePreferences prefs) {
        if (prefs.getTimeSlot() != null) {
            speaker.setPreferredTimeSlot(prefs.getTimeSlot());
        }
        if (prefs.getTravelRequirements() != null) {
            speaker.setTravelRequirements(prefs.getTravelRequirements());
        }
        if (prefs.getTechnicalRequirements() != null && prefs.getTechnicalRequirements().length > 0) {
            // Store as comma-separated string
            speaker.setTechnicalRequirements(String.join(",", prefs.getTechnicalRequirements()));
        }
        if (prefs.getInitialTitle() != null) {
            speaker.setInitialPresentationTitle(prefs.getInitialTitle());
        }
        if (prefs.getComments() != null) {
            speaker.setPreferenceComments(prefs.getComments());
        }
    }

    /**
     * Publish domain event for the response.
     */
    private void publishResponseEvent(SpeakerPool speaker, Event event, SpeakerResponseRequest request) {
        SpeakerResponseReceivedEvent domainEvent = SpeakerResponseReceivedEvent.builder()
                .speakerPoolId(speaker.getId())
                .username(speaker.getUsername())
                .eventCode(event.getEventCode())
                .responseType(request.getResponse())
                .reason(request.getReason())
                .respondedAt(Instant.now())
                .build();

        eventPublisher.publishEvent(domainEvent);
        LOG.debug("Published SpeakerResponseReceivedEvent for speaker {}", speaker.getId());
    }

    /**
     * Build the result with confirmation details and next steps.
     */
    private SpeakerResponseResult buildResult(SpeakerPool speaker, Event event, SpeakerResponseType responseType) {
        List<String> nextSteps = new ArrayList<>();
        String profileUrl = null;

        if (responseType == SpeakerResponseType.ACCEPT) {
            nextSteps.add("Complete your speaker profile");
            if (speaker.getContentDeadline() != null) {
                nextSteps.add("Submit your presentation title and abstract by " + speaker.getContentDeadline());
            }

            // Generate a VIEW token for profile access (reusable, 30-day expiry)
            String viewToken = magicLinkService.generateToken(speaker.getId(), TokenAction.VIEW, 30);
            profileUrl = appBaseUrl + "/speaker-portal/profile?token=" + viewToken;
            LOG.info("Generated profile URL for speaker {}: {}", speaker.getSpeakerName(), profileUrl);
        } else if (responseType == SpeakerResponseType.TENTATIVE) {
            nextSteps.add("Return to this link when you're ready to confirm");
            nextSteps.add("Contact the organizer if you have questions");
        }

        return SpeakerResponseResult.builder()
                .success(true)
                .speakerName(speaker.getSpeakerName())
                .eventName(event.getTitle())
                .nextSteps(nextSteps)
                .contentDeadline(speaker.getContentDeadline())
                .profileUrl(profileUrl)
                .build();
    }
}
