package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.OutreachHistory;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.speakers.dto.generated.BatchInviteRequest;
import ch.batbern.events.speakers.dto.generated.BatchInviteResponse;
import ch.batbern.events.speakers.dto.generated.BatchInviteResponseErrorsInner;
import ch.batbern.events.speakers.dto.generated.InviteSpeakerRequest;
import ch.batbern.events.speakers.dto.generated.InviteSpeakerResponse;
import ch.batbern.events.speakers.dto.generated.SendInvitationRequest;
import ch.batbern.events.speakers.dto.generated.SendInvitationResponse;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.exception.SpeakerNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.OutreachHistoryRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.shared.events.SpeakerInvitationSentEvent;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final SecurityContextHelper securityContextHelper;
    private final ApplicationEventPublisher eventPublisher;
    private final OutreachHistoryRepository outreachHistoryRepository;
    private final SpeakerWorkflowService speakerWorkflowService;
    private final PrimarySpeakerResolver primarySpeakerResolver;

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
        log.info("Inviting speaker {} to event {}", LoggingUtils.maskEmail(request.getEmail()), eventCode);

        // 1. Find the event
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        // 2. Get or create user via User Management Service (AC2). Story 11.E.9: the
        // email-based idempotency check is gone — speaker_pool no longer stores email,
        // and there's no session_users row yet at IDENTIFIED to dedupe against. Multiple
        // pool rows per User for the same event are acceptable in the brainstorm UX.
        GetOrCreateUserRequest userRequest = new GetOrCreateUserRequest();
        userRequest.setEmail(request.getEmail());
        userRequest.setFirstName(request.getFirstName());
        userRequest.setLastName(request.getLastName());
        userRequest.setCompanyId(request.getCompany());
        userRequest.setCognitoSync(false); // Speakers don't need Cognito accounts initially

        GetOrCreateUserResponse userResponse = userApiClient.getOrCreateUser(userRequest);
        boolean userCreated = userResponse.getCreated();

        log.debug("User {} for speaker {}, username: {}",
                userCreated ? "created" : "found", request.getEmail(), userResponse.getUsername());

        // 3. Create SpeakerPool entry (AC1) — initial status assignment on INSERT bypasses
        // transition() by design (ADR-009: speakers enter the workflow at IDENTIFIED).
        // Story 11.E.9: username/email are no longer columns on speaker_pool — they live
        // in CUMS (just provisioned above) and on session_users (created at READY).
        SpeakerPool speakerPool = SpeakerPool.builder()
                .eventId(event.getId())
                .speakerName(displayName(request))
                .company(request.getCompany())
                .sessionId(request.getSessionId())
                .notes(request.getNotes())
                .status(SpeakerWorkflowState.IDENTIFIED)
                .build();

        SpeakerPool saved = speakerPoolRepository.save(speakerPool);

        log.info("Created SpeakerPool entry {} for speaker {} in event {}",
                saved.getId(), LoggingUtils.maskEmail(request.getEmail()), eventCode);

        // Response carries the just-provisioned CUMS identity for the FE to display.
        return new InviteSpeakerResponse()
                .speakerPoolId(saved.getId())
                .username(userResponse.getUsername())
                .email(request.getEmail())
                .speakerName(saved.getSpeakerName())
                .status(InviteSpeakerResponse.StatusEnum.IDENTIFIED)
                .created(true)
                .userCreated(userCreated)
                .createdAt(toOffset(saved.getCreatedAt()));
    }

    /**
     * Display name for a speaker, falling back to email when no name is given
     * (replaces the hand DTO's getDisplayName() helper after the generated-DTO swap).
     */
    private static String displayName(InviteSpeakerRequest r) {
        if (r.getFirstName() != null && r.getLastName() != null) {
            return r.getFirstName() + " " + r.getLastName();
        } else if (r.getFirstName() != null) {
            return r.getFirstName();
        } else if (r.getLastName() != null) {
            return r.getLastName();
        }
        return r.getEmail();
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    /**
     * Send invitation email to a speaker.
     * AC3: Sends personalized email with magic links
     * AC4: Supports i18n (German/English)
     * AC6: Publishes SpeakerInvitationSentEvent
     * Story 6.1c: Accepts email in request for speakers without email in database
     *
     * @param eventCode the event code
     * @param username the speaker's username
     * @param request the send invitation request
     * @return response with updated speaker details
     */
    @Transactional
    public SendInvitationResponse sendInvitation(String eventCode, String username, SendInvitationRequest request) {
        log.info("Sending invitation to speaker {} for event {}", username, eventCode);

        // Validate response deadline is in the future. The hand DTO enforced this with
        // @Future; OpenAPI has no future-date keyword, so the generated DTO can't carry it
        // and the check moves here (preserves the deployed 400 on a past deadline).
        if (request.getResponseDeadline() == null
                || !request.getResponseDeadline().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Response deadline must be in the future");
        }

        // Validate content deadline if provided
        if (request.getContentDeadline() != null && !deadlinesValid(request)) {
            throw new IllegalArgumentException("Content deadline must be after response deadline");
        }

        // 1. Find the event
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        // 2. Find the speaker pool entry. Story 11.E.9: the JOIN-based
        // findByEventIdAndUsername navigates session_users → session → speaker_pool;
        // brainstormed (pre-READY) speakers won't match the JOIN, so the ID fallback
        // remains for the case where the FE has the pool ID and calls this endpoint
        // with the UUID in the username slot.
        SpeakerPool speaker = speakerPoolRepository.findByEventIdAndUsername(event.getId(), username)
                .or(() -> {
                    try {
                        java.util.UUID speakerId = java.util.UUID.fromString(username);
                        return speakerPoolRepository.findById(speakerId)
                                .filter(s -> s.getEventId().equals(event.getId()));
                    } catch (IllegalArgumentException e) {
                        return java.util.Optional.empty();
                    }
                })
                .orElseThrow(() -> new SpeakerNotFoundException(username, eventCode));

        // 3. Resolve recipient email. Story 11.E.9: the canonical email is on the User
        // (CUMS) record provisioned at CONTACTED → READY. The request.email() override
        // lets the organizer send to a one-shot address (e.g. a forwarder) without
        // changing CUMS; the override does NOT persist anywhere — it flows through the
        // TransitionPayload to the INVITED hook's email service only.
        String resolvedEmail = request.getEmail() != null && !request.getEmail().isBlank()
                ? request.getEmail()
                : primarySpeakerResolver.resolveEmail(speaker).orElse(null);
        if (resolvedEmail == null) {
            throw new IllegalArgumentException("Speaker email is required to send invitation");
        }

        // 4. Pre-mutate invitation params on the speaker (organizer-supplied; not state changes).
        speaker.setResponseDeadline(request.getResponseDeadline());
        speaker.setContentDeadline(request.getContentDeadline());

        // 5. Delegate to SpeakerWorkflowService.transition() — sole writer per ADR-009.
        //    The INVITED side-effect hook generates magic-link tokens, sends the invitation
        //    email, sets invitedAt, and writes the speaker_status_history row.
        //    The slot-capacity gate (READY → INVITED) is enforced inside transition()
        //    and surfaces as SlotCapacityReachedException → HTTP 409 from GlobalExceptionHandler.
        String currentUser = securityContextHelper.getCurrentUsername();
        String auditActor = currentUser != null ? currentUser : "system";
        TransitionPayload payload = TransitionPayload.builder()
                .email(resolvedEmail)
                .reason("Invitation email sent")
                .inviteContext(Map.of("locale", request.getLocale() != null ? request.getLocale() : "de"))
                .build();

        speakerWorkflowService.transition(speaker.getId(), SpeakerWorkflowState.INVITED, auditActor, payload);

        // 5. Reload to pick up invitedAt (set by INVITED hook) for the response DTO.
        SpeakerPool updated = speakerPoolRepository.findById(speaker.getId())
                .orElseThrow(() -> new SpeakerNotFoundException(username, eventCode));

        // 6. Record outreach history for the automated email invitation (audit, not state).
        OutreachHistory outreach = new OutreachHistory();
        outreach.setSpeakerPoolId(updated.getId());
        outreach.setContactDate(updated.getInvitedAt() != null ? updated.getInvitedAt() : Instant.now());
        outreach.setContactMethod("email");
        outreach.setNotes("Automated invitation email sent via speaker portal");
        outreach.setOrganizerUsername(currentUser != null ? currentUser : "system");
        outreachHistoryRepository.save(outreach);
        log.debug("Created outreach history for invitation to speaker {}", username);

        // 7. Publish domain event (AC6). Story 11.E.9: email comes from the resolved
        // address used above (may be the request override or CUMS-resolved).
        SpeakerInvitationSentEvent sentEvent = new SpeakerInvitationSentEvent(
                updated.getId(),
                eventCode,
                username,
                resolvedEmail,
                currentUser
        );
        eventPublisher.publishEvent(sentEvent);
        log.debug("Published SpeakerInvitationSentEvent for speaker {} in event {}", username, eventCode);

        log.info("Invitation sent to speaker {} for event {}", username, eventCode);

        // Response identity: resolver fetches the current primary speaker
        // (session_users + CUMS). Username comes from session_users; email may
        // differ from resolvedEmail if the organizer used a one-shot override.
        PrimarySpeakerResolver.PrimarySpeakerProfile profile =
                primarySpeakerResolver.resolve(updated).orElse(null);
        return new SendInvitationResponse()
                .speakerPoolId(updated.getId())
                .username(profile != null ? profile.username() : username)
                .email(profile != null && profile.email() != null ? profile.email() : resolvedEmail)
                .status(SendInvitationResponse.StatusEnum.fromValue(updated.getStatus().name()))
                .invitedAt(toOffset(updated.getInvitedAt()))
                .responseDeadline(updated.getResponseDeadline())
                .contentDeadline(updated.getContentDeadline());
    }

    /**
     * Content deadline must be after the response deadline when both are present
     * (replaces the hand DTO's areDeadlinesValid() helper after the generated-DTO swap).
     */
    private static boolean deadlinesValid(SendInvitationRequest r) {
        if (r.getContentDeadline() == null) {
            return true;
        }
        return r.getContentDeadline().isAfter(r.getResponseDeadline());
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
        log.info("Batch inviting {} speakers to event {}", request.getSpeakers().size(), eventCode);

        // Verify event exists first
        if (!eventRepository.existsByEventCode(eventCode)) {
            throw new EventNotFoundException(eventCode);
        }

        List<InviteSpeakerResponse> results = new ArrayList<>();
        List<BatchInviteResponseErrorsInner> errors = new ArrayList<>();

        for (InviteSpeakerRequest speakerRequest : request.getSpeakers()) {
            try {
                InviteSpeakerResponse response = inviteSpeaker(eventCode, speakerRequest);
                results.add(response);
            } catch (Exception e) {
                log.warn("Failed to invite speaker {}: {}",
                        LoggingUtils.maskEmail(speakerRequest.getEmail()), e.getMessage());
                errors.add(new BatchInviteResponseErrorsInner()
                        .email(speakerRequest.getEmail())
                        .errorCode(getErrorCode(e))
                        .errorMessage(e.getMessage()));
            }
        }

        log.info("Batch invitation complete for event {}: {} success, {} failed",
                eventCode, results.size(), errors.size());

        return new BatchInviteResponse()
                .totalRequested(request.getSpeakers().size())
                .successCount(results.size())
                .failedCount(errors.size())
                .results(results)
                .errors(errors);
    }

    /**
     * Map exception to error code for batch processing.
     */
    private BatchInviteResponseErrorsInner.ErrorCodeEnum getErrorCode(Exception e) {
        if (e instanceof IllegalArgumentException) {
            return BatchInviteResponseErrorsInner.ErrorCodeEnum.INVALID_REQUEST;
        } else if (e instanceof EventNotFoundException) {
            return BatchInviteResponseErrorsInner.ErrorCodeEnum.EVENT_NOT_FOUND;
        } else if (isUserServiceException(e)) {
            return BatchInviteResponseErrorsInner.ErrorCodeEnum.USER_SERVICE_ERROR;
        } else {
            return BatchInviteResponseErrorsInner.ErrorCodeEnum.INTERNAL_ERROR;
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
