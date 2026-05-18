package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerResponsePreferences;
import ch.batbern.events.dto.SpeakerResponseRequest;
import ch.batbern.events.dto.SpeakerResponseResult;
import ch.batbern.events.exception.AlreadyRespondedException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.workflow.SecurityPrincipal;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.shared.events.SpeakerResponseReceivedEvent;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.types.SpeakerResponseType;
import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>Story 11.B.2 (ADR-009): state mutations delegate to
 * {@link SpeakerWorkflowService#transition} — the sole writer of {@code speaker_pool.status}.
 * Provisioning at READY (User lookup-or-create + SPEAKER role grant) is now upstream at
 * {@code CONTACTED → READY}; this service no longer creates Users or Speakers.
 *
 * <p>Story 11.E.3 (ADR-009 §Decision 3): the speaker portal is Cognito-secured. The actor is
 * read from Spring's {@code SecurityContext} by the controller and the pool row is resolved
 * via {@link SpeakerPortalAuthorizationService}; the magic-link token bridge is gone.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerResponseService {

    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SpeakerWorkflowService speakerWorkflowService;

    /**
     * Story 11.E.3: process a Cognito-authenticated speaker's response to an invitation.
     *
     * @param actor   the Cognito-derived principal (built by the controller via
     *                {@link SecurityPrincipal#fromAuthentication})
     * @param speaker the speaker_pool row already resolved by
     *                {@link SpeakerPortalAuthorizationService} (caller has verified ownership)
     * @param request the response body
     * @return confirmation details and next steps
     * @throws ValidationException        if request validation fails (e.g. DECLINE without reason)
     * @throws AlreadyRespondedException  if the speaker already responded
     */
    @Transactional
    public SpeakerResponseResult processResponse(
            SecurityPrincipal actor, SpeakerPool speaker, SpeakerResponseRequest request) {
        log.info("Processing speaker response: type={} username={} speakerPoolId={}",
                request.getResponse(), actor.username(), speaker.getId());

        checkAlreadyResponded(speaker);
        validateRequest(request);

        Event event = eventRepository.findById(speaker.getEventId())
                .orElseThrow(() -> new IllegalStateException("Event not found for speaker pool"));

        switch (request.getResponse()) {
            case ACCEPT -> processAcceptResponse(actor, speaker, request);
            case DECLINE -> processDeclineResponse(actor, speaker, request);
            default -> throw new IllegalArgumentException(
                    "Unsupported response type: " + request.getResponse());
        }

        // Reload from DB — transition() persists the updated SpeakerPool.
        speaker = speakerPoolRepository.findById(speaker.getId())
                .orElseThrow(() -> new IllegalStateException("Speaker pool entry vanished mid-response"));

        publishResponseEvent(speaker, event, request);

        return buildResult(speaker, event, request.getResponse());
    }

    /**
     * Speakers who have already pressed ACCEPT or DECLINE cannot respond again via the
     * portal. A speaker who changes their mind after ACCEPT transitions through DECLINED
     * (organizer-triggered) — that's the unified path under ADR-009 §0.7.
     */
    private void checkAlreadyResponded(SpeakerPool speaker) {
        SpeakerWorkflowState status = speaker.getStatus();

        if (status == SpeakerWorkflowState.ACCEPTED) {
            throw new AlreadyRespondedException(status, speaker.getAcceptedAt());
        }

        if (status == SpeakerWorkflowState.DECLINED) {
            throw new AlreadyRespondedException(status, speaker.getDeclinedAt());
        }
    }

    /**
     * Validate request based on response type.
     * - DECLINE requires a reason
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
    }

    /**
     * Process ACCEPT response — delegates to {@link SpeakerWorkflowService#transition}.
     * The hook sets {@code acceptedAt}, clears tentative flags, and fires the
     * acceptance email. Provisioning (User + SPEAKER role) happened upstream at
     * {@code CONTACTED → READY}.
     */
    private void processAcceptResponse(
            SecurityPrincipal actor, SpeakerPool speaker, SpeakerResponseRequest request) {
        // Code review 2026-05-18 (P9): tighten the provisioning-invariant guard. The canonical
        // path through SpeakerPortalAuthorizationService.resolveSpeakerPool already rejects
        // null/blank usernames with 409, so this is belt-and-suspenders for any future direct
        // service caller. Promoting log.warn → IllegalStateException ensures the workflow
        // transition never persists with a stale display-name fallback.
        if (speaker.getUsername() == null || speaker.getUsername().isBlank()) {
            throw new IllegalStateException(
                    "Speaker pool row id=" + speaker.getId()
                            + " has no canonical username — provisioning invariant from"
                            + " CONTACTED → READY was bypassed; cannot record ACCEPT");
        }

        TransitionPayload payload = TransitionPayload.builder()
                .email(speaker.getEmail())
                .reason("Accepted invitation via speaker portal")
                .responsePreferences(request.getPreferences())
                .build();

        speakerWorkflowService.transition(
                speaker.getId(), SpeakerWorkflowState.ACCEPTED, actor, payload);

        // Re-fetch and persist optional preferences (status persisted by transition()).
        if (request.getPreferences() != null) {
            SpeakerPool reloaded = speakerPoolRepository.findById(speaker.getId())
                    .orElseThrow(() -> new IllegalStateException("Speaker pool vanished mid-response"));
            storePreferences(reloaded, request.getPreferences());
            speakerPoolRepository.save(reloaded);
        }

        log.info("Speaker {} accepted invitation for event {}",
                speaker.getSpeakerName(), speaker.getEventId());
    }

    /**
     * Process DECLINE response — delegates to {@link SpeakerWorkflowService#transition}.
     * The hook sets {@code declinedAt}, {@code declineReason}, deletes any assigned session,
     * and notifies the organizer (since DECLINE from INVITED is post-invitation).
     */
    private void processDeclineResponse(
            SecurityPrincipal actor, SpeakerPool speaker, SpeakerResponseRequest request) {
        TransitionPayload payload = TransitionPayload.builder()
                .reason(request.getReason())
                .build();

        speakerWorkflowService.transition(
                speaker.getId(), SpeakerWorkflowState.DECLINED, actor, payload);

        log.info("Speaker {} declined invitation for event {}. Reason: {}",
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
     * Publish a domain event signalling that a speaker (not an organizer) explicitly
     * responded. This is independent of {@code SpeakerWorkflowStateChangeEvent} which
     * captures the state machine transition.
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
        log.debug("Published SpeakerResponseReceivedEvent for speaker {}", speaker.getId());
    }

    private SpeakerResponseResult buildResult(SpeakerPool speaker, Event event, SpeakerResponseType responseType) {
        List<String> nextSteps = new ArrayList<>();

        if (responseType == SpeakerResponseType.ACCEPT) {
            // Code review 2026-05-18 (D1): drop the dedicated profile URL. Story 11.C.1 already
            // consolidated profile editing into the CUMS /users/me endpoints; the speaker portal
            // no longer carries a per-event profile page. The "complete your profile" wording is
            // kept as guidance — the user reaches it via the standard nav (or via a generic
            // /profile route that calls CUMS), not via a custom event-scoped URL.
            nextSteps.add("Complete your speaker profile");
            if (speaker.getContentDeadline() != null) {
                nextSteps.add("Submit your presentation title and abstract by " + speaker.getContentDeadline());
            }
        }

        return SpeakerResponseResult.builder()
                .success(true)
                .speakerName(speaker.getSpeakerName())
                .eventName(event.getTitle())
                .nextSteps(nextSteps)
                .contentDeadline(speaker.getContentDeadline())
                .build();
    }
}
