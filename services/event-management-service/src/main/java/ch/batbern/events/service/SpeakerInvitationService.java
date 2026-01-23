package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.BatchInviteRequest;
import ch.batbern.events.dto.BatchInviteResponse;
import ch.batbern.events.dto.InviteSpeakerRequest;
import ch.batbern.events.dto.InviteSpeakerResponse;
import ch.batbern.events.dto.SendInvitationRequest;
import ch.batbern.events.dto.SendInvitationResponse;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.exception.SpeakerNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.shared.events.SpeakerInvitationSentEvent;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Service for speaker invitation management.
 * Story 6.1b: Speaker Invitation System
 *
 * Provides:
 * - Speaker invitation with auto-user creation (AC1, AC2)
 * - Invitation email sending with magic links (AC3)
 * - Batch invitation processing (AC5)
 * - Idempotency for duplicate invitations (AC7)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerInvitationService {

    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final UserApiClient userApiClient;
    private final MagicLinkService magicLinkService;
    private final SpeakerInvitationEmailService emailService;
    private final SecurityContextHelper securityContextHelper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Invite a speaker to an event.
     * AC1: Creates SpeakerPool entry
     * AC2: Auto-creates User via UserApiClient if needed
     * AC7: Returns existing entry if speaker already invited (idempotency)
     *
     * @param eventCode the event code
     * @param request the invitation request
     * @return invitation response with speaker details
     */
    @Transactional
    public InviteSpeakerResponse inviteSpeaker(String eventCode, InviteSpeakerRequest request) {
        log.info("Inviting speaker {} to event {}", request.email(), eventCode);

        // 1. Find the event
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        // 2. Check for existing speaker pool entry (AC7: idempotency)
        Optional<SpeakerPool> existingSpeaker = speakerPoolRepository
                .findByEventIdAndEmail(event.getId(), request.email());

        if (existingSpeaker.isPresent()) {
            SpeakerPool speaker = existingSpeaker.get();
            log.info("Speaker {} already exists in pool for event {}", request.email(), eventCode);
            return InviteSpeakerResponse.existing(
                    speaker.getId(),
                    speaker.getUsername(),
                    speaker.getEmail(),
                    speaker.getSpeakerName(),
                    speaker.getStatus(),
                    speaker.getCreatedAt()
            );
        }

        // 3. Get or create user via User Management Service (AC2)
        GetOrCreateUserRequest userRequest = new GetOrCreateUserRequest();
        userRequest.setEmail(request.email());
        userRequest.setFirstName(request.firstName());
        userRequest.setLastName(request.lastName());
        userRequest.setCompanyId(request.company());
        userRequest.setCognitoSync(false); // Speakers don't need Cognito accounts initially

        GetOrCreateUserResponse userResponse = userApiClient.getOrCreateUser(userRequest);
        boolean userCreated = userResponse.getCreated();

        log.debug("User {} for speaker {}, username: {}",
                userCreated ? "created" : "found", request.email(), userResponse.getUsername());

        // 4. Create SpeakerPool entry (AC1)
        SpeakerPool speakerPool = SpeakerPool.builder()
                .eventId(event.getId())
                .username(userResponse.getUsername())
                .email(request.email())
                .speakerName(request.getDisplayName())
                .company(request.company())
                .sessionId(request.sessionId())
                .notes(request.notes())
                .status(SpeakerWorkflowState.IDENTIFIED)
                .build();

        SpeakerPool saved = speakerPoolRepository.save(speakerPool);

        log.info("Created SpeakerPool entry {} for speaker {} in event {}",
                saved.getId(), request.email(), eventCode);

        return InviteSpeakerResponse.created(
                saved.getId(),
                saved.getUsername(),
                saved.getEmail(),
                saved.getSpeakerName(),
                userCreated,
                saved.getCreatedAt()
        );
    }

    /**
     * Send invitation email to a speaker.
     * AC3: Sends personalized email with magic links
     * AC4: Supports i18n (German/English)
     * AC6: Publishes SpeakerInvitationSentEvent
     *
     * @param eventCode the event code
     * @param username the speaker's username
     * @param request the send invitation request
     * @return response with updated speaker details
     */
    @Transactional
    public SendInvitationResponse sendInvitation(String eventCode, String username, SendInvitationRequest request) {
        log.info("Sending invitation to speaker {} for event {}", username, eventCode);

        // Validate content deadline if provided
        if (request.contentDeadline() != null && !request.areDeadlinesValid()) {
            throw new IllegalArgumentException("Content deadline must be after response deadline");
        }

        // 1. Find the event
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        // 2. Find the speaker pool entry
        SpeakerPool speaker = speakerPoolRepository.findByEventIdAndUsername(event.getId(), username)
                .orElseThrow(() -> new SpeakerNotFoundException(username, eventCode));

        // 3. Generate magic link token for response (RESPOND action)
        String respondToken = magicLinkService.generateToken(speaker.getId(), TokenAction.RESPOND);

        // 4. Update speaker pool entry with invitation details
        Instant invitedAt = Instant.now();
        speaker.setInvitedAt(invitedAt);
        speaker.setResponseDeadline(request.responseDeadline());
        speaker.setContentDeadline(request.contentDeadline());
        speaker.setStatus(SpeakerWorkflowState.INVITED);

        SpeakerPool updated = speakerPoolRepository.save(speaker);

        // 5. Send invitation email asynchronously (AC3, AC4)
        Locale locale = request.locale() != null
                ? Locale.forLanguageTag(request.locale())
                : Locale.GERMAN;

        emailService.sendInvitationEmail(
                updated,
                event,
                respondToken,
                locale
        );

        // 6. Publish domain event (AC6)
        String currentUser = securityContextHelper.getCurrentUsername();
        SpeakerInvitationSentEvent sentEvent = new SpeakerInvitationSentEvent(
                updated.getId(),
                eventCode,
                username,
                updated.getEmail(),
                currentUser
        );
        eventPublisher.publishEvent(sentEvent);
        log.debug("Published SpeakerInvitationSentEvent for speaker {} in event {}", username, eventCode);

        log.info("Invitation sent to speaker {} for event {}", username, eventCode);

        return new SendInvitationResponse(
                updated.getId(),
                updated.getUsername(),
                updated.getEmail(),
                updated.getStatus(),
                updated.getInvitedAt(),
                updated.getResponseDeadline(),
                updated.getContentDeadline()
        );
    }

    /**
     * Batch invite speakers to an event.
     * AC5: Handles multiple invitations with partial failure support
     *
     * @param eventCode the event code
     * @param request the batch invite request
     * @return response with results and any errors
     */
    @Transactional
    public BatchInviteResponse inviteBatch(String eventCode, BatchInviteRequest request) {
        log.info("Batch inviting {} speakers to event {}", request.speakers().size(), eventCode);

        // Verify event exists first
        if (!eventRepository.existsByEventCode(eventCode)) {
            throw new EventNotFoundException(eventCode);
        }

        List<InviteSpeakerResponse> results = new ArrayList<>();
        List<BatchInviteResponse.BatchInviteError> errors = new ArrayList<>();

        for (InviteSpeakerRequest speakerRequest : request.speakers()) {
            try {
                InviteSpeakerResponse response = inviteSpeaker(eventCode, speakerRequest);
                results.add(response);
            } catch (Exception e) {
                log.warn("Failed to invite speaker {}: {}", speakerRequest.email(), e.getMessage());
                errors.add(new BatchInviteResponse.BatchInviteError(
                        speakerRequest.email(),
                        getErrorCode(e),
                        e.getMessage()
                ));
            }
        }

        log.info("Batch invitation complete for event {}: {} success, {} failed",
                eventCode, results.size(), errors.size());

        return BatchInviteResponse.partial(request.speakers().size(), results, errors);
    }

    /**
     * Map exception to error code for batch processing.
     */
    private String getErrorCode(Exception e) {
        if (e instanceof IllegalArgumentException) {
            return "INVALID_REQUEST";
        } else if (e instanceof EventNotFoundException) {
            return "EVENT_NOT_FOUND";
        } else if (isUserServiceException(e)) {
            return "USER_SERVICE_ERROR";
        } else {
            return "INTERNAL_ERROR";
        }
    }

    /**
     * Check if exception originated from UserApiClient communication.
     */
    private boolean isUserServiceException(Exception e) {
        // Check exception class name for Feign/HTTP client errors
        String className = e.getClass().getName();
        if (className.contains("Feign") || className.contains("HttpClient")) {
            return true;
        }
        // Check cause chain for user service related errors
        Throwable cause = e.getCause();
        while (cause != null) {
            String causeName = cause.getClass().getName();
            if (causeName.contains("Feign") || causeName.contains("HttpClient")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
