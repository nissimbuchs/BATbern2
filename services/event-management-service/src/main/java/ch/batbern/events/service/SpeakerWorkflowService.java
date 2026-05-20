package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.generated.users.InvitationCredentialsResponse;
import ch.batbern.events.dto.generated.users.ProvisionUserRequest;
import ch.batbern.events.dto.generated.users.ProvisionUserResponse;
import ch.batbern.events.exception.SlotCapacityReachedException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
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
import org.springframework.beans.factory.annotation.Value;
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

    // Story 11.E.2: speaker-portal login URL embedded in the Cognito-flow invitation email.
    // The fallback default is the production URL — keep for backward compatibility but warn
    // at startup if the property is unset (review patch D4) so misconfigured staging deploys
    // surface in CloudWatch before the first invitation email leaves SES.
    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;

    @Value("${app.base-url:#{null}}")
    private String baseUrlRawForStartupCheck;

    @jakarta.annotation.PostConstruct
    void warnIfBaseUrlUnset() {
        if (baseUrlRawForStartupCheck == null || baseUrlRawForStartupCheck.isBlank()) {
            log.warn("app.base-url is NOT explicitly configured — falling back to default '{}'. "
                    + "Set app.base-url in the active Spring profile to avoid emitting prod URLs "
                    + "from non-prod environments.", baseUrl);
        }
    }

    /**
     * Sole entry point for mutating {@code speaker_pool.status}. See class-level docs for
     * the body ordering, allow-list, and side-effect contract.
     *
     * @param speakerPoolId speaker pool primary key
     * @param target target workflow state
     * @param username username of the user that triggered the change (recorded in audit trail)
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
            String username,
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
            return handleSameStateTransition(speaker, current, username, safePayload);
        }

        if (!isAllowed(current, target)) {
            throw new InvalidStateTransitionException(
                    String.format("Invalid state transition for speaker pool %s: %s → %s",
                            speakerPoolId, current.name(), target.name()));
        }

        // 4. Preconditions (target-specific)
        Event event = loadEvent(speaker.getEventId());
        enforcePrecondition(target, current, event, safePayload, speaker);

        // 5. Side-effect hook (target-specific) — may mutate the in-memory speaker
        runSideEffectHook(speaker, event, current, target, username, safePayload);

        // 6. Persist new state — the ONLY production-code call to SpeakerPool#setStatus.
        speaker.setStatus(target);
        SpeakerPool persisted = speakerPoolRepository.save(speaker);

        // 7. Write status-history row (skip when the caller's own audit log already captures
        //    this change — e.g. SpeakerOutreachService writes an OutreachHistory row and asks
        //    the workflow service to suppress the redundant status_history entry).
        SpeakerStatusHistory historyRow = safePayload.suppressHistoryRow()
                ? null
                : writeHistoryRow(persisted, current, target, username, safePayload);

        // 8. Publish SpeakerWorkflowStateChangeEvent (best-effort — failure does NOT roll back)
        publishWorkflowStateChangeEvent(persisted, current, target, username);

        // 9. State-specific domain events
        publishStateSpecificEvents(persisted, event, current, target, username);

        return new TransitionResult(persisted, historyRow);
    }

    private boolean isAllowed(SpeakerWorkflowState from, SpeakerWorkflowState to) {
        Set<SpeakerWorkflowState> allowed = ALLOWED.get(from);
        return allowed != null && allowed.contains(to);
    }

    private TransitionResult handleSameStateTransition(
            SpeakerPool speaker,
            SpeakerWorkflowState state,
            String username,
            TransitionPayload payload
    ) {
        // Preconditions, side-effects, and state-specific events are all SKIPPED on same-state.
        SpeakerStatusHistory historyRow = writeHistoryRow(speaker, state, state, username, payload);
        publishWorkflowStateChangeEvent(speaker, state, state, username);
        return new TransitionResult(speaker, historyRow);
    }

    private void enforcePrecondition(
            SpeakerWorkflowState target,
            SpeakerWorkflowState current,
            Event event,
            TransitionPayload payload,
            SpeakerPool speaker
    ) {
        switch (target) {
            case READY -> {
                requireEmail(payload);
                requireName(payload);
            }
            case INVITED -> {
                enforceSlotCapacity(event);
                requireUsername(speaker);
            }
            case DECLINED -> requireDeclineReasonIfPostInvitation(current, payload);
            default -> { /* no precondition */ }
        }
    }

    private void requireEmail(TransitionPayload payload) {
        if (payload.email() == null || payload.email().isBlank()) {
            throw new ValidationException("email is required to promote speaker to READY");
        }
    }

    /**
     * Story 11.E.4 code-review patch: defense-in-depth at the workflow-service layer so the
     * AC4 invariant ("no placeholder Cognito names") survives if a future entry path into
     * {@code runReadyHook} forgets to enforce {@code @NotBlank} at the DTO. Today the only
     * caller is the promote endpoint whose DTO carries {@code @NotBlank} on both fields, so
     * this guard never fires; it exists to catch silent regression.
     */
    private void requireName(TransitionPayload payload) {
        if (payload.firstName() == null || payload.firstName().isBlank()) {
            throw new ValidationException(
                    "firstName is required to promote speaker to READY");
        }
        if (payload.lastName() == null || payload.lastName().isBlank()) {
            throw new ValidationException(
                    "lastName is required to promote speaker to READY");
        }
    }

    /**
     * Story 11.E.2 review patch (E5): the INVITED hook calls
     * {@code userApiClient.issueInvitationCredentials(speaker.getUsername())}; a null
     * username produces the URL {@code /users/null/issue-invitation-credentials} → CUMS 404.
     * Fail-fast with a clear ValidationException so the operator sees the real cause.
     * A speaker should always have a username by the time it reaches READY (set in the
     * READY hook); this guards against legacy migration / same-state edge cases.
     */
    private void requireUsername(SpeakerPool speaker) {
        if (speaker.getUsername() == null || speaker.getUsername().isBlank()) {
            throw new ValidationException(
                    "Speaker username is missing — provisioning at READY did not complete. "
                            + "Re-run the CONTACTED → READY transition before promoting to INVITED.");
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
            String username,
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
        // CONTACTED → READY: provisioning seam. Story 11.D.1 refactored this hook (per
        // Story 11.C.2 AR13) to call the consolidated UserApiClient.provisionUserWithRole(...) —
        // a single call that creates the User if missing and grants the SPEAKER role
        // idempotently. Cognito wiring lands in 11.E.2 (the temporaryPassword on the response
        // stays null until then). The payload's email becomes the canonical speaker_pool.email
        // and the SpeakerPromotedToReadyEvent payload.
        ProvisionUserRequest provisionRequest = new ProvisionUserRequest(
                payload.email(),
                ProvisionUserRequest.RoleEnum.SPEAKER);
        // Story 11.E.4 AC4: firstName and lastName are guaranteed non-blank by the
        // PromoteSpeakerRequest @NotBlank validation at the controller boundary; the previous
        // *Fallback methods (with literal "Speaker" / "Unknown" placeholders) are deleted.
        // Other entry paths into runReadyHook are expected to enforce the same invariant.
        provisionRequest.setFirstName(payload.firstName());
        provisionRequest.setLastName(payload.lastName());

        ProvisionUserResponse userResponse = userApiClient.provisionUserWithRole(provisionRequest);

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
    }

    private void runInvitedHook(SpeakerPool speaker, Event event, TransitionPayload payload) {
        // READY → INVITED: Story 11.E.2 (AC7) rewires this hook from magic-link tokens to
        // Cognito-flow credentials. CUMS's issueInvitationCredentials endpoint mints a fresh
        // temporary password (or signals USE_EXISTING_PASSWORD for already-confirmed users).
        // The email service renders the right template branch based on the action discriminator.
        //
        // NB: Email send currently fires synchronously inside the @Transactional boundary; if the
        // transition rolls back after this point, the email has already been sent. Moving to
        // AFTER_COMMIT semantics is tracked in deferred-work (code review 11.B.2, P2).
        InvitationCredentialsResponse credentials =
                userApiClient.issueInvitationCredentials(speaker.getUsername());

        Locale locale = resolveLocale(payload);
        String loginUrl = baseUrl + "/login";
        invitationEmailService.sendInvitationEmail(speaker, event, loginUrl, credentials, locale);
        // Story 11.E.2: the temp password (if non-null) leaves scope here — neither this
        // service nor the speaker entity retains a reference to it.

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
            String username,
            TransitionPayload payload
    ) {
        SpeakerStatusHistory history = new SpeakerStatusHistory();
        history.setSpeakerPoolId(speaker.getId());
        history.setEventId(speaker.getEventId());
        history.setSessionId(speaker.getSessionId());
        history.setPreviousStatus(previous);
        history.setNewStatus(next);
        history.setChangedByUsername(username);
        history.setChangeReason(payload.reason());
        history.setChangedAt(Instant.now());
        return statusHistoryRepository.save(history);
    }

    private void publishWorkflowStateChangeEvent(
            SpeakerPool speaker,
            SpeakerWorkflowState from,
            SpeakerWorkflowState to,
            String username
    ) {
        try {
            SpeakerWorkflowStateChangeEvent event = new SpeakerWorkflowStateChangeEvent(
                    speaker.getId(),
                    speaker.getEventId(),
                    from,
                    to,
                    username
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
            String username
    ) {
        // CONTACTED → READY → SpeakerPromotedToReadyEvent (consumed by Phase E for Cognito email).
        if (from == SpeakerWorkflowState.CONTACTED && to == SpeakerWorkflowState.READY) {
            SpeakerPromotedToReadyEvent promoted = SpeakerPromotedToReadyEvent.builder()
                    .speakerPoolId(speaker.getId())
                    .eventCode(event.getEventCode())
                    .username(speaker.getUsername())
                    .email(speaker.getEmail())
                    .promotedAt(Instant.now())
                    .promotedByUsername(username)
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
                    .acceptedBy(username)
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

}
