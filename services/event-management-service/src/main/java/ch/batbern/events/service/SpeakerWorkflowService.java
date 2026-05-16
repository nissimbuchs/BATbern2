package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.exception.SlotCapacityReachedException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.events.service.workflow.SecurityPrincipal;
import ch.batbern.events.service.workflow.SpeakerProvisioningHook;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.events.service.workflow.TransitionResult;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.events.SpeakerAcceptedEvent;
import ch.batbern.shared.events.SpeakerPromotedToReadyEvent;
import ch.batbern.shared.events.SpeakerWorkflowStateChangeEvent;
import ch.batbern.shared.exception.InvalidStateTransitionException;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.types.SpeakerResponseType;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Speaker workflow service — sole writer of {@code speaker_pool.status} per ADR-009.
 *
 * <p>Every mutation to a speaker's workflow state flows through {@link #transition} which:
 * <ol>
 *   <li>Validates the transition against a single allow-list (no other validator exists)</li>
 *   <li>Enforces state-specific preconditions (email at READY, slot-capacity at INVITED,
 *       reason at DECLINED from INVITED+)</li>
 *   <li>Runs the state-specific side-effect hook (provisioning seam, invitation email,
 *       organizer notification, session cleanup)</li>
 *   <li>Persists the new state + writes a {@link SpeakerStatusHistory} row + publishes
 *       {@link SpeakerWorkflowStateChangeEvent} and state-specific domain events</li>
 * </ol>
 *
 * <p>Same-state calls write a self-transition history row (audit "re-affirm") but skip
 * preconditions, hooks, and state-specific events — see ADR-009 §0.1.
 *
 * @see <a href="../../../../../../../docs/architecture/ADR-009-unified-speaker-workflow.md">ADR-009</a>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerWorkflowService {

    /**
     * Allow-list of legal forward transitions per ADR-009. Same-state transitions and
     * (any non-terminal) → DECLINED are also legal but handled explicitly in {@link #transition}.
     */
    private static final Map<SpeakerWorkflowState, Set<SpeakerWorkflowState>> ALLOWED = Map.ofEntries(
            Map.entry(SpeakerWorkflowState.IDENTIFIED,
                    Set.of(SpeakerWorkflowState.CONTACTED, SpeakerWorkflowState.DECLINED)),
            Map.entry(SpeakerWorkflowState.CONTACTED,
                    Set.of(SpeakerWorkflowState.READY, SpeakerWorkflowState.DECLINED)),
            Map.entry(SpeakerWorkflowState.READY,
                    Set.of(SpeakerWorkflowState.INVITED, SpeakerWorkflowState.DECLINED)),
            Map.entry(SpeakerWorkflowState.INVITED,
                    Set.of(SpeakerWorkflowState.ACCEPTED, SpeakerWorkflowState.DECLINED)),
            Map.entry(SpeakerWorkflowState.ACCEPTED,
                    Set.of(SpeakerWorkflowState.CONTENT_SUBMITTED, SpeakerWorkflowState.DECLINED)),
            Map.entry(SpeakerWorkflowState.CONTENT_SUBMITTED,
                    Set.of(SpeakerWorkflowState.QUALITY_REVIEWED, SpeakerWorkflowState.DECLINED)),
            Map.entry(SpeakerWorkflowState.QUALITY_REVIEWED,
                    Set.of(SpeakerWorkflowState.DECLINED))
            // DECLINED → terminal (no outgoing transitions)
    );

    private final SpeakerPoolRepository speakerPoolRepository;
    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final SpeakerStatusHistoryRepository statusHistoryRepository;
    private final EventTypeService eventTypeService;
    private final UserApiClient userApiClient;
    private final SpeakerProvisioningHook speakerProvisioningHook;
    private final SpeakerInvitationEmailService invitationEmailService;
    private final SpeakerAcceptanceEmailService acceptanceEmailService;
    private final OrganizerNotificationService organizerNotificationService;
    private final MagicLinkService magicLinkService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * Sole entry point for mutating {@code speaker_pool.status}. See class-level docs for
     * the body ordering, allow-list, and side-effect contract.
     *
     * @param speakerPoolId speaker pool primary key
     * @param target target workflow state
     * @param actor authenticated principal that triggered the change
     * @param payload optional fields used by side-effect hooks (email, reason, preferences, …)
     * @return persisted speaker pool + status-history row
     * @throws NotFoundException speaker pool entry not found
     * @throws InvalidStateTransitionException target not reachable from current state
     * @throws ValidationException precondition failed (e.g., missing email at READY)
     * @throws SlotCapacityReachedException slot-capacity gate blocks READY → INVITED
     */
    @Transactional
    public TransitionResult transition(
            UUID speakerPoolId,
            SpeakerWorkflowState target,
            SecurityPrincipal actor,
            TransitionPayload payload
    ) {
        TransitionPayload safePayload = payload != null ? payload : TransitionPayload.builder().build();

        // 1. Load speaker pool
        SpeakerPool speaker = speakerPoolRepository.findById(speakerPoolId)
                .orElseThrow(() -> new NotFoundException("Speaker pool entry not found: " + speakerPoolId));

        // 2. Capture current state
        SpeakerWorkflowState current = speaker.getStatus();

        // 3. Allow-list check
        if (current == target) {
            return handleSameStateTransition(speaker, current, actor, safePayload);
        }

        if (!isAllowed(current, target)) {
            throw new InvalidStateTransitionException(
                    String.format("Invalid state transition for speaker pool %s: %s → %s",
                            speakerPoolId, current.name(), target.name()));
        }

        // 4. Preconditions (target-specific)
        Event event = loadEvent(speaker.getEventId());
        enforcePrecondition(target, current, event, safePayload);

        // 5. Side-effect hook (target-specific) — may mutate the in-memory speaker
        runSideEffectHook(speaker, event, current, target, actor, safePayload);

        // 6. Persist new state — the ONLY production-code call to SpeakerPool#setStatus.
        speaker.setStatus(target);
        SpeakerPool persisted = speakerPoolRepository.save(speaker);

        // 7. Write status-history row
        SpeakerStatusHistory historyRow = writeHistoryRow(persisted, current, target, actor, safePayload);

        // 8. Publish SpeakerWorkflowStateChangeEvent (best-effort — failure does NOT roll back)
        publishWorkflowStateChangeEvent(persisted, current, target, actor);

        // 9. State-specific domain events
        publishStateSpecificEvents(persisted, event, current, target, actor);

        return new TransitionResult(persisted, historyRow);
    }

    private boolean isAllowed(SpeakerWorkflowState from, SpeakerWorkflowState to) {
        Set<SpeakerWorkflowState> allowed = ALLOWED.get(from);
        return allowed != null && allowed.contains(to);
    }

    private TransitionResult handleSameStateTransition(
            SpeakerPool speaker,
            SpeakerWorkflowState state,
            SecurityPrincipal actor,
            TransitionPayload payload
    ) {
        // Preconditions, side-effects, and state-specific events are all SKIPPED on same-state.
        SpeakerStatusHistory historyRow = writeHistoryRow(speaker, state, state, actor, payload);
        publishWorkflowStateChangeEvent(speaker, state, state, actor);
        return new TransitionResult(speaker, historyRow);
    }

    private void enforcePrecondition(
            SpeakerWorkflowState target,
            SpeakerWorkflowState current,
            Event event,
            TransitionPayload payload
    ) {
        switch (target) {
            case READY -> requireEmail(payload);
            case INVITED -> enforceSlotCapacity(event);
            case DECLINED -> requireDeclineReasonIfPostInvitation(current, payload);
            default -> { /* no precondition */ }
        }
    }

    private void requireEmail(TransitionPayload payload) {
        if (payload.email() == null || payload.email().isBlank()) {
            throw new ValidationException("email is required to promote speaker to READY");
        }
    }

    private void enforceSlotCapacity(Event event) {
        int maxSlots = eventTypeService.getEventType(event.getEventType()).getMaxSlots();
        long acceptedCount = speakerPoolRepository.countByEventIdAndStatus(
                event.getId(), SpeakerWorkflowState.ACCEPTED);
        long invitedCount = speakerPoolRepository.countByEventIdAndStatus(
                event.getId(), SpeakerWorkflowState.INVITED);
        if (acceptedCount + invitedCount >= maxSlots) {
            throw new SlotCapacityReachedException(event.getId(), acceptedCount, invitedCount, maxSlots);
        }
    }

    private void requireDeclineReasonIfPostInvitation(SpeakerWorkflowState current, TransitionPayload payload) {
        if (isPostInvitation(current)
                && (payload.reason() == null || payload.reason().isBlank())) {
            throw new ValidationException(
                    "decline reason is required after a speaker has been invited");
        }
    }

    private boolean isPostInvitation(SpeakerWorkflowState state) {
        return state == SpeakerWorkflowState.INVITED
                || state == SpeakerWorkflowState.ACCEPTED
                || state == SpeakerWorkflowState.CONTENT_SUBMITTED
                || state == SpeakerWorkflowState.QUALITY_REVIEWED;
    }

    private void runSideEffectHook(
            SpeakerPool speaker,
            Event event,
            SpeakerWorkflowState current,
            SpeakerWorkflowState target,
            SecurityPrincipal actor,
            TransitionPayload payload
    ) {
        switch (target) {
            case READY -> runReadyHook(speaker, payload);
            case INVITED -> runInvitedHook(speaker, event, payload);
            case ACCEPTED -> runAcceptedHook(speaker, event);
            case DECLINED -> runDeclinedHook(speaker, event, current, payload);
            case CONTENT_SUBMITTED -> {
                // No-op: content persistence is owned by ContentSubmissionService (11.C.2).
            }
            case QUALITY_REVIEWED -> {
                // No-op: review persistence is owned by QualityReviewService.
            }
            default -> { /* IDENTIFIED/CONTACTED have no hooks */ }
        }
    }

    private void runReadyHook(SpeakerPool speaker, TransitionPayload payload) {
        // CONTACTED → READY: provisioning seam. Cognito provisioning lands in 11.E.2; for
        // 11.B.2 we look up / create the User (cognitoSync=false) and stub the SPEAKER role grant.
        // The payload's email becomes the canonical speaker_pool.email — it's the one used to
        // provision the User and the one the SpeakerPromotedToReadyEvent will carry downstream.
        GetOrCreateUserRequest userRequest = new GetOrCreateUserRequest();
        userRequest.setEmail(payload.email());
        userRequest.setFirstName(payload.firstName() != null ? payload.firstName() : firstNameFallback(speaker));
        userRequest.setLastName(payload.lastName() != null ? payload.lastName() : lastNameFallback(speaker));
        userRequest.setCognitoSync(false);

        GetOrCreateUserResponse userResponse = userApiClient.getOrCreateUser(userRequest);

        // Identity-rebind guard: if the speaker is already bound to a different username/email,
        // the lookup result may point at a wholly different User account (e.g. organizer corrected
        // a typo and the new email already belonged to someone else). Reject the implicit re-bind
        // rather than silently overwriting the audit trail.
        String existingUsername = speaker.getUsername();
        if (existingUsername != null && !existingUsername.isBlank()
                && !existingUsername.equals(userResponse.getUsername())) {
            throw new ValidationException(String.format(
                    "Cannot rebind speaker %s from user '%s' to user '%s' implicitly — "
                            + "explicit identity change requires a dedicated organizer action.",
                    speaker.getId(), existingUsername, userResponse.getUsername()));
        }
        String existingEmail = speaker.getEmail();
        if (existingEmail != null && !existingEmail.isBlank()
                && !existingEmail.equalsIgnoreCase(payload.email())) {
            log.warn("Speaker {} email change at READY: '{}' → '{}' (resolved to user '{}')",
                    speaker.getId(), existingEmail, payload.email(), userResponse.getUsername());
        }

        speaker.setUsername(userResponse.getUsername());
        speaker.setEmail(payload.email());

        speakerProvisioningHook.grantSpeakerRole(userResponse.getUsername(), payload.email());
    }

    private void runInvitedHook(SpeakerPool speaker, Event event, TransitionPayload payload) {
        // READY → INVITED: generate magic-link tokens + send invitation email. The magic-link
        // token system remains until Phase F (11.F.1); Phase E (11.E.2) rewires the email
        // template to a Cognito login URL + temp password.
        //
        // NB: Email send currently fires synchronously inside the @Transactional boundary; if the
        // transition rolls back after this point, the email has already been sent. Moving to
        // AFTER_COMMIT semantics is tracked in deferred-work (code review 11.B.2, P2).
        String respondToken = magicLinkService.generateToken(speaker.getId(), TokenAction.RESPOND);
        String dashboardToken = magicLinkService.generateToken(speaker.getId(), TokenAction.VIEW);

        Locale locale = resolveLocale(payload);
        invitationEmailService.sendInvitationEmail(speaker, event, respondToken, dashboardToken, locale);

        speaker.setInvitedAt(Instant.now());
    }

    private void runAcceptedHook(SpeakerPool speaker, Event event) {
        // INVITED → ACCEPTED: stamp acceptedAt, send confirmation email, notify organizer.
        // SpeakerAcceptedEvent is published below in publishStateSpecificEvents — its listener
        // auto-creates the session.
        //
        // NB: External side effects (email, organizer notify) fire synchronously inside the
        // @Transactional boundary; rollback after this point leaks the email. AFTER_COMMIT
        // refactor tracked in deferred-work (code review 11.B.2, P2).
        //
        // Story 11.B.3: dropped the residual setIsTentative(false)/setTentativeReason(null)
        // calls left over from 11.B.2 — those columns no longer exist (V93 dropped them).
        speaker.setAcceptedAt(Instant.now());

        try {
            String viewToken = magicLinkService.generateToken(speaker.getId(), TokenAction.VIEW, 30);
            acceptanceEmailService.sendAcceptanceConfirmationEmail(
                    speaker, event, viewToken, Locale.GERMAN);
        } catch (Exception ex) {
            log.warn("Failed to send acceptance confirmation email for speaker {}: {}",
                    speaker.getId(), ex.getMessage());
        }

        // Notify organizer of response — symmetric with runDeclinedHook's notify for post-invitation
        // declines. Restoring behaviour from pre-11.B.2 SpeakerResponseService.notifyOrganizerOfResponse.
        try {
            organizerNotificationService.notifyOrganizerOfResponse(
                    speaker, event, SpeakerResponseType.ACCEPT);
        } catch (Exception ex) {
            log.warn("Failed to notify organizer of ACCEPT for speaker {}: {}",
                    speaker.getId(), ex.getMessage());
        }
    }

    private void runDeclinedHook(
            SpeakerPool speaker,
            Event event,
            SpeakerWorkflowState current,
            TransitionPayload payload
    ) {
        // (any non-terminal) → DECLINED: stamp declinedAt + reason, notify organizer for
        // post-invitation declines, clear assigned session.
        speaker.setDeclinedAt(Instant.now());
        if (payload.reason() != null) {
            speaker.setDeclineReason(payload.reason());
        }

        if (isPostInvitation(current)) {
            organizerNotificationService.notifyOrganizerOfResponse(
                    speaker, event, SpeakerResponseType.DECLINE);

            if (speaker.getSessionId() != null) {
                UUID sessionId = speaker.getSessionId();
                log.info("Declining speaker {} - deleting associated session {}",
                        speaker.getId(), sessionId);
                speaker.setSessionId(null);
                sessionRepository.deleteById(sessionId);
            }
        }
    }

    private SpeakerStatusHistory writeHistoryRow(
            SpeakerPool speaker,
            SpeakerWorkflowState previous,
            SpeakerWorkflowState next,
            SecurityPrincipal actor,
            TransitionPayload payload
    ) {
        SpeakerStatusHistory history = new SpeakerStatusHistory();
        history.setSpeakerPoolId(speaker.getId());
        history.setEventId(speaker.getEventId());
        history.setSessionId(speaker.getSessionId());
        history.setPreviousStatus(previous);
        history.setNewStatus(next);
        history.setChangedByUsername(actor.username());
        history.setChangeReason(payload.reason());
        history.setChangedAt(Instant.now());
        return statusHistoryRepository.save(history);
    }

    private void publishWorkflowStateChangeEvent(
            SpeakerPool speaker,
            SpeakerWorkflowState from,
            SpeakerWorkflowState to,
            SecurityPrincipal actor
    ) {
        try {
            SpeakerWorkflowStateChangeEvent event = new SpeakerWorkflowStateChangeEvent(
                    speaker.getId(),
                    speaker.getEventId(),
                    from,
                    to,
                    actor.username()
            );
            domainEventPublisher.publish(event);
            log.info("Published SpeakerWorkflowStateChangeEvent: {} -> {} for speaker {}",
                    from, to, speaker.getId());
        } catch (Exception ex) {
            log.warn("Failed to publish SpeakerWorkflowStateChangeEvent for speaker {}: {}",
                    speaker.getId(), ex.getMessage());
        }
    }

    private void publishStateSpecificEvents(
            SpeakerPool speaker,
            Event event,
            SpeakerWorkflowState from,
            SpeakerWorkflowState to,
            SecurityPrincipal actor
    ) {
        // CONTACTED → READY → SpeakerPromotedToReadyEvent (consumed by Phase E for Cognito email).
        if (from == SpeakerWorkflowState.CONTACTED && to == SpeakerWorkflowState.READY) {
            SpeakerPromotedToReadyEvent promoted = SpeakerPromotedToReadyEvent.builder()
                    .speakerPoolId(speaker.getId())
                    .eventCode(event.getEventCode())
                    .username(speaker.getUsername())
                    .email(speaker.getEmail())
                    .promotedAt(Instant.now())
                    .promotedByUsername(actor.username())
                    .build();
            applicationEventPublisher.publishEvent(promoted);
        }

        // INVITED → ACCEPTED → SpeakerAcceptedEvent (consumed by SpeakerAcceptedEventListener).
        if (from == SpeakerWorkflowState.INVITED && to == SpeakerWorkflowState.ACCEPTED) {
            SpeakerAcceptedEvent accepted = SpeakerAcceptedEvent.builder()
                    .eventId(event.getId())
                    .eventCode(event.getEventCode())
                    .speakerPoolId(speaker.getId())
                    .speakerName(speaker.getSpeakerName())
                    .company(speaker.getCompany())
                    .expertise(speaker.getExpertise())
                    .acceptedBy(actor.username())
                    .build();
            applicationEventPublisher.publishEvent(accepted);
        }
    }

    private Event loadEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
    }

    private Locale resolveLocale(TransitionPayload payload) {
        if (payload.inviteContext() != null) {
            Object locale = payload.inviteContext().get("locale");
            if (locale instanceof Locale l) {
                return l;
            }
            if (locale instanceof String tag && !tag.isBlank()) {
                return Locale.forLanguageTag(tag);
            }
        }
        return Locale.GERMAN;
    }

    private String firstNameFallback(SpeakerPool speaker) {
        String name = speaker.getSpeakerName();
        if (name == null || name.isBlank()) {
            return "Speaker";
        }
        String[] parts = name.trim().split("\\s+", 2);
        return parts[0];
    }

    private String lastNameFallback(SpeakerPool speaker) {
        String name = speaker.getSpeakerName();
        if (name == null || name.isBlank()) {
            return "Unknown";
        }
        String[] parts = name.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1] : "";
    }
}
