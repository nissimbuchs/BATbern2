package ch.batbern.events.service;

import ch.batbern.events.domain.InvitationStatus;
import ch.batbern.events.domain.ResponseType;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.domain.SpeakerInvitation;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.PreferredTimeSlot;
import ch.batbern.events.domain.TechnicalRequirement;
import ch.batbern.events.domain.TravelRequirement;
import ch.batbern.events.dto.BulkInvitationResponse;
import ch.batbern.events.dto.BulkSendInvitationRequest;
import ch.batbern.events.dto.InvitationResponse;
import ch.batbern.events.dto.RespondToInvitationRequest;
import ch.batbern.events.dto.SendInvitationRequest;
import ch.batbern.events.dto.SpeakerResponsePreferences;
import ch.batbern.events.event.InvitationRespondedEvent;
import ch.batbern.events.event.SpeakerInvitedEvent;
import ch.batbern.events.exception.InvitationExpiredException;
import ch.batbern.events.exception.InvitationNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerInvitationRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.util.InvitationTokenGenerator;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service for managing speaker invitations - Story 6.1.
 *
 * Provides operations for:
 * - Sending invitations with unique response tokens
 * - Processing speaker responses
 * - Tracking invitation status
 * - Publishing domain events
 *
 * ADR-003/ADR-004 compliant: uses meaningful identifiers (username, event_code).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InvitationService {

    private final SpeakerInvitationRepository invitationRepository;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final SpeakerService speakerService;
    private final InvitationTokenGenerator tokenGenerator;
    private final DomainEventPublisher domainEventPublisher;
    private final InvitationEmailService invitationEmailService;
    private final TransactionTemplate transactionTemplate;

    private static final int DEFAULT_EXPIRATION_DAYS = 14;

    /**
     * Send an invitation to a speaker for an event.
     *
     * @param request The invitation request
     * @param organizerUsername The organizer sending the invitation
     * @return The created invitation response
     * @throws IllegalStateException if an active invitation already exists
     */
    public InvitationResponse sendInvitation(SendInvitationRequest request, String organizerUsername) {
        return sendInvitationInternal(request, organizerUsername);
    }

    /**
     * Internal implementation of sending an invitation.
     * Separated to allow use from bulk operations with separate transactions.
     *
     * Supports two flows:
     * 1. By speakerPoolId (preferred) - for speakers with or without user accounts
     * 2. By username (legacy) - for backward compatibility
     */
    private InvitationResponse sendInvitationInternal(SendInvitationRequest request, String organizerUsername) {
        // Determine which identifier to use
        boolean useSpeakerPoolId = request.getSpeakerPoolId() != null;

        log.info("Sending invitation to speaker {} for event {} (using {})",
                useSpeakerPoolId ? request.getSpeakerPoolId() : request.getUsername(),
                request.getEventCode(),
                useSpeakerPoolId ? "speakerPoolId" : "username");

        // Get event for validation
        var event = eventRepository.findByEventCode(request.getEventCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Event not found: " + request.getEventCode()));

        // Variables to populate from either flow
        String speakerEmail;
        String speakerName;
        String username = request.getUsername();
        SpeakerPool speakerPoolEntry = null;
        Speaker speaker = null;

        if (useSpeakerPoolId) {
            // NEW FLOW: Use speakerPoolId to find speaker pool entry directly
            speakerPoolEntry = speakerPoolRepository.findById(request.getSpeakerPoolId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Speaker pool entry not found: " + request.getSpeakerPoolId()));

            // Validate speaker pool entry belongs to this event
            if (!speakerPoolEntry.getEventId().equals(event.getId())) {
                throw new IllegalArgumentException(
                        "Speaker pool entry does not belong to event " + request.getEventCode());
            }

            // Get email and name from speaker pool
            speakerEmail = speakerPoolEntry.getEmail();
            speakerName = speakerPoolEntry.getSpeakerName();
            username = speakerPoolEntry.getUsername(); // May be null

            if (speakerEmail == null || speakerEmail.isBlank()) {
                throw new IllegalStateException(
                        String.format("Speaker %s does not have an email address. "
                                + "Please add an email before sending an invitation.",
                                speakerName));
            }

            // Check for existing active invitation by speakerPoolId
            if (invitationRepository.existsActiveInvitationBySpeakerPoolId(
                    request.getSpeakerPoolId(), request.getEventCode())) {
                throw new IllegalStateException(
                        String.format("An active invitation already exists for speaker %s and event %s",
                                speakerName, request.getEventCode()));
            }

            // If speaker has username, also get Speaker entity for workflow state update
            if (username != null && !username.isBlank()) {
                try {
                    speaker = speakerService.getSpeakerEntityByUsername(username);
                } catch (Exception e) {
                    log.debug("Speaker entity not found for username {}, proceeding without workflow update",
                            username);
                }
            }
        } else {
            // LEGACY FLOW: Use username to find speaker
            speaker = speakerService.getSpeakerEntityByUsername(request.getUsername());

            // Get speaker pool entry for email
            var speakerPoolOpt = speakerPoolRepository.findByEventIdAndUsername(
                    event.getId(), request.getUsername());

            if (speakerPoolOpt.isPresent()) {
                speakerPoolEntry = speakerPoolOpt.get();
                speakerEmail = speakerPoolEntry.getEmail();
                speakerName = speakerPoolEntry.getSpeakerName();

                if (speakerEmail == null || speakerEmail.isBlank()) {
                    throw new IllegalStateException(
                            String.format("Speaker %s does not have an email address. "
                                    + "Please add an email before sending an invitation.",
                                    request.getUsername()));
                }
            } else {
                // Fallback to username as identifier
                speakerEmail = null;
                speakerName = request.getUsername();
            }

            // Check for existing active invitation by username
            if (invitationRepository.existsActiveInvitation(
                    request.getUsername(), request.getEventCode())) {
                throw new IllegalStateException(
                        String.format("An active invitation already exists for speaker %s and event %s",
                                request.getUsername(), request.getEventCode()));
            }
        }

        // Generate unique response token
        String responseToken = tokenGenerator.generateToken();

        // Calculate expiration date
        int expirationDays = request.getExpirationDays() != null
                ? request.getExpirationDays()
                : DEFAULT_EXPIRATION_DAYS;
        Instant expiresAt = Instant.now().plus(expirationDays, ChronoUnit.DAYS);
        Instant sentAt = Instant.now();

        // Create invitation with all fields (Story 6.2 + speakerPoolId support)
        SpeakerInvitation invitation = SpeakerInvitation.builder()
                .username(username)
                .speakerPoolId(request.getSpeakerPoolId())
                .speakerEmail(speakerEmail)
                .speakerName(speakerName)
                .eventCode(request.getEventCode())
                .responseToken(responseToken)
                .invitationStatus(InvitationStatus.SENT)
                .sentAt(sentAt)
                .expiresAt(expiresAt)
                .createdBy(organizerUsername)
                .personalMessage(request.getPersonalMessage())
                .build();

        // Save invitation
        SpeakerInvitation saved = invitationRepository.save(invitation);

        // Update speaker workflow state to CONTACTED (if we have a Speaker entity)
        if (speaker != null) {
            speaker.setWorkflowState(SpeakerWorkflowState.CONTACTED);
        }

        // Update speaker pool status to CONTACTED
        if (speakerPoolEntry != null) {
            speakerPoolEntry.setStatus(SpeakerWorkflowState.CONTACTED);
            speakerPoolRepository.save(speakerPoolEntry);
        }

        // Publish domain event (use speakerName if username is null)
        String speakerIdentifier = username != null ? username : speakerName;
        domainEventPublisher.publish(new SpeakerInvitedEvent(
                saved.getId(),
                speakerIdentifier,
                saved.getEventCode(),
                saved.getExpiresAt(),
                saved.getSentAt(),
                organizerUsername
        ));

        // Send invitation email (AC4 - AWS SES integration)
        invitationEmailService.sendInvitationEmail(saved, organizerUsername, request.getPersonalMessage());

        log.info("Invitation sent successfully with ID {}", saved.getId());

        return toResponse(saved);
    }

    /**
     * Get invitation by response token.
     * Used for speaker response portal (public, no auth required).
     *
     * @param token The unique response token
     * @return The invitation response
     * @throws InvitationNotFoundException if token is invalid
     */
    @Transactional(readOnly = true)
    public InvitationResponse getInvitationByToken(String token) {
        log.debug("Fetching invitation by token");

        SpeakerInvitation invitation = invitationRepository.findByResponseToken(token)
                .orElseThrow(() -> new InvitationNotFoundException(token));

        // Mark as opened if first access
        if (invitation.getInvitationStatus() == InvitationStatus.SENT) {
            invitation.setInvitationStatus(InvitationStatus.OPENED);
            invitation.setEmailOpenedAt(Instant.now());
            invitationRepository.save(invitation);
        }

        return toResponse(invitation);
    }

    /**
     * Process speaker response to invitation.
     *
     * @param token The unique response token
     * @param request The response details
     * @return The updated invitation response
     * @throws InvitationNotFoundException if token is invalid
     * @throws InvitationExpiredException if invitation has expired
     */
    public InvitationResponse respondToInvitation(String token, RespondToInvitationRequest request) {
        log.info("Processing response for invitation token");

        SpeakerInvitation invitation = invitationRepository.findByResponseToken(token)
                .orElseThrow(() -> new InvitationNotFoundException(token));

        // Check if expired
        if (invitation.isExpired()) {
            throw new InvitationExpiredException(token);
        }

        // Check if already responded
        if (invitation.getInvitationStatus() == InvitationStatus.RESPONDED) {
            log.warn("Attempt to respond to already responded invitation");
            return toResponse(invitation);
        }

        Instant respondedAt = Instant.now();

        // Update invitation
        invitation.setInvitationStatus(InvitationStatus.RESPONDED);
        invitation.setResponseType(request.getResponseType());
        invitation.setRespondedAt(respondedAt);
        invitation.setNotes(request.getNotes());

        if (request.getResponseType() == ResponseType.DECLINED) {
            invitation.setDeclineReason(request.getDeclineReason());
        }

        // Story 6.2: Persist preferences when accepting
        if (request.getResponseType() == ResponseType.ACCEPTED && request.getPreferences() != null) {
            SpeakerResponsePreferences prefs = request.getPreferences();
            if (prefs.getPreferredTimeSlot() != null) {
                invitation.setPreferredTimeSlot(prefs.getPreferredTimeSlot().name().toLowerCase());
            }
            if (prefs.getTravelRequirements() != null) {
                invitation.setTravelRequirements(prefs.getTravelRequirements().name().toLowerCase());
            }
            if (prefs.getTechnicalRequirements() != null && !prefs.getTechnicalRequirements().isEmpty()) {
                invitation.setTechnicalRequirements(
                        prefs.getTechnicalRequirements().stream()
                                .map(req -> req.name().toLowerCase())
                                .reduce((a, b) -> a + "," + b)
                                .orElse(null)
                );
            }
            invitation.setInitialPresentationTitle(prefs.getInitialPresentationTitle());
            invitation.setCommentsForOrganizer(prefs.getCommentsForOrganizer());
        }

        SpeakerInvitation saved = invitationRepository.save(invitation);

        // Update speaker workflow state (only if we have a username)
        SpeakerWorkflowState newState = mapResponseToWorkflowState(request.getResponseType());
        if (invitation.getUsername() != null && !invitation.getUsername().isBlank()) {
            try {
                Speaker speaker = speakerService.getSpeakerEntityByUsername(invitation.getUsername());
                speaker.setWorkflowState(newState);
            } catch (Exception e) {
                log.debug("Speaker entity not found for username {}, skipping workflow update",
                        invitation.getUsername());
            }
        }

        // Update speaker pool entry status (if we have speakerPoolId)
        if (invitation.getSpeakerPoolId() != null) {
            speakerPoolRepository.findById(invitation.getSpeakerPoolId())
                    .ifPresent(poolEntry -> {
                        poolEntry.setStatus(newState);
                        speakerPoolRepository.save(poolEntry);
                    });
        }

        // Use speakerName as fallback if username is null
        String speakerIdentifier = invitation.getUsername() != null
                ? invitation.getUsername()
                : invitation.getSpeakerName();

        // Publish domain event
        domainEventPublisher.publish(new InvitationRespondedEvent(
                saved.getId(),
                speakerIdentifier,
                saved.getEventCode(),
                saved.getResponseType().name(),
                saved.getDeclineReason(),
                respondedAt,
                speakerIdentifier // Speaker identifier as triggeredBy
        ));

        log.info("Invitation response recorded: {} for speaker {}",
                request.getResponseType(), speakerIdentifier);

        return toResponse(saved);
    }

    /**
     * List invitations for an event.
     *
     * @param eventCode The event code
     * @param pageable Pagination parameters
     * @return Page of invitation responses
     */
    @Transactional(readOnly = true)
    public Page<InvitationResponse> listInvitationsByEvent(String eventCode, Pageable pageable) {
        return invitationRepository.findByEventCode(eventCode, pageable)
                .map(this::toResponse);
    }

    /**
     * List invitations for a speaker.
     *
     * @param username The speaker's username
     * @return List of invitation responses
     */
    @Transactional(readOnly = true)
    public List<InvitationResponse> listInvitationsBySpeaker(String username) {
        return invitationRepository.findByUsername(username).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Send bulk invitations to multiple speakers for an event.
     * Story 6.1 AC7: Bulk invitation support.
     *
     * @param eventCode The event code
     * @param request The bulk invitation request
     * @param organizerUsername The organizer sending the invitations
     * @return Summary of successful and failed invitations
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BulkInvitationResponse sendBulkInvitations(
            String eventCode,
            BulkSendInvitationRequest request,
            String organizerUsername) {
        log.info("Sending bulk invitations to {} speakers for event {}", request.getUsernames().size(), eventCode);

        List<InvitationResponse> successful = new ArrayList<>();
        List<BulkInvitationResponse.BulkInvitationFailure> failures = new ArrayList<>();

        for (String username : request.getUsernames()) {
            try {
                // Execute each invitation in its own transaction to prevent rollback propagation
                InvitationResponse response = transactionTemplate.execute(status -> {
                    SendInvitationRequest singleRequest = SendInvitationRequest.builder()
                            .username(username)
                            .eventCode(eventCode)
                            .personalMessage(request.getPersonalMessage())
                            .expirationDays(request.getExpirationDays())
                            .build();

                    return sendInvitationInternal(singleRequest, organizerUsername);
                });
                successful.add(response);
            } catch (Exception e) {
                log.warn("Failed to send invitation to {}: {}", username, e.getMessage());
                failures.add(BulkInvitationResponse.BulkInvitationFailure.builder()
                        .username(username)
                        .reason(e.getMessage())
                        .build());
            }
        }

        log.info("Bulk invitations completed: {} successful, {} failed", successful.size(), failures.size());

        return BulkInvitationResponse.builder()
                .totalRequested(request.getUsernames().size())
                .successCount(successful.size())
                .failureCount(failures.size())
                .successful(successful)
                .failures(failures)
                .build();
    }

    /**
     * Mark expired invitations as expired.
     * Called by scheduled job.
     */
    public void markExpiredInvitations() {
        List<SpeakerInvitation> expired = invitationRepository.findExpiredInvitations(Instant.now());
        for (SpeakerInvitation invitation : expired) {
            invitation.setInvitationStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            log.info("Marked invitation {} as expired", invitation.getId());
        }
    }

    /**
     * Map response type to speaker workflow state.
     */
    private SpeakerWorkflowState mapResponseToWorkflowState(ResponseType responseType) {
        return switch (responseType) {
            case ACCEPTED -> SpeakerWorkflowState.ACCEPTED;
            case DECLINED -> SpeakerWorkflowState.DECLINED;
            case TENTATIVE -> SpeakerWorkflowState.CONTACTED; // Stay in contacted for tentative
        };
    }

    /**
     * Convert entity to response DTO.
     */
    private InvitationResponse toResponse(SpeakerInvitation invitation) {
        return InvitationResponse.builder()
                .id(invitation.getId())
                .username(invitation.getUsername())
                .speakerPoolId(invitation.getSpeakerPoolId())
                .speakerName(invitation.getSpeakerName())
                .speakerEmail(invitation.getSpeakerEmail())
                .eventCode(invitation.getEventCode())
                .invitationStatus(invitation.getInvitationStatus())
                .sentAt(invitation.getSentAt())
                .respondedAt(invitation.getRespondedAt())
                .responseType(invitation.getResponseType())
                .declineReason(invitation.getDeclineReason())
                .expiresAt(invitation.getExpiresAt())
                .reminderCount(invitation.getReminderCount())
                .lastReminderAt(invitation.getLastReminderAt())
                .createdAt(invitation.getCreatedAt())
                .createdBy(invitation.getCreatedBy())
                // Story 6.2: Add personalMessage, notes, and preferences
                .personalMessage(invitation.getPersonalMessage())
                .notes(invitation.getNotes())
                .preferences(buildPreferencesDto(invitation))
                .build();
    }

    /**
     * Build preferences DTO from entity fields - Story 6.2.
     */
    private SpeakerResponsePreferences buildPreferencesDto(SpeakerInvitation invitation) {
        // Only populate if any preference field is set
        if (invitation.getPreferredTimeSlot() == null
                && invitation.getTravelRequirements() == null
                && invitation.getTechnicalRequirements() == null
                && invitation.getInitialPresentationTitle() == null
                && invitation.getCommentsForOrganizer() == null) {
            return null;
        }

        return SpeakerResponsePreferences.builder()
                .preferredTimeSlot(invitation.getPreferredTimeSlot() != null
                        ? PreferredTimeSlot.valueOf(invitation.getPreferredTimeSlot().toUpperCase())
                        : null)
                .travelRequirements(invitation.getTravelRequirements() != null
                        ? TravelRequirement.valueOf(invitation.getTravelRequirements().toUpperCase())
                        : null)
                .technicalRequirements(invitation.getTechnicalRequirements() != null
                        && !invitation.getTechnicalRequirements().isBlank()
                        ? Arrays.stream(invitation.getTechnicalRequirements().split(","))
                                .map(String::trim)
                                .map(String::toUpperCase)
                                .map(TechnicalRequirement::valueOf)
                                .toList()
                        : null)
                .initialPresentationTitle(invitation.getInitialPresentationTitle())
                .commentsForOrganizer(invitation.getCommentsForOrganizer())
                .build();
    }
}
