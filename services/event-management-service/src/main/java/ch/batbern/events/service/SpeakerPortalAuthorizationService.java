package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.exception.SpeakerPortalAccessDeniedException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Story 11.E.3: resolves the {@link SpeakerPool} row that a Cognito-authenticated speaker
 * is operating on, gating access by pool ownership AND event-lifecycle state.
 *
 * <p>Used by every speaker-portal controller method that targets a specific event
 * ({@code respond}, {@code content/*}, {@code materials/*}). The dashboard endpoint is the
 * single exception — it lists across all of the speaker's events and uses
 * {@link SpeakerPoolRepository#findByUsername(String)} directly.
 *
 * <p>Centralising the lookup gives a single audit-logging point for foreign-event access
 * attempts, a single place to enforce the canonical-username invariant (pool row must have
 * a non-blank {@code username} before any write — see Story 11.E.2 provisioning), and a
 * single place to evolve the rule.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerPortalAuthorizationService {

    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final SessionUserRepository sessionUserRepository;

    /**
     * Looks up the speaker_pool row for {@code (username, eventCode)} and returns it.
     *
     * <p>Throws {@link SpeakerPortalAccessDeniedException} (→ HTTP 403) if no row matches —
     * either the event doesn't exist or this speaker has no invitation for it. The two
     * cases are deliberately collapsed: revealing "event exists but you don't have access"
     * separately from "event doesn't exist" would leak event-existence information.
     *
     * <p>Throws {@link IllegalStateException} (→ HTTP 409, code review 2026-05-18 D2) if the
     * event is in a closed state ({@link EventWorkflowState#EVENT_COMPLETED}). A speaker with
     * a bookmark to a long-past event can no longer accept, decline, content-submit, or
     * upload materials. The dashboard intentionally still lists past events for reference.
     *
     * <p>Throws {@link IllegalStateException} (→ HTTP 409, code review 2026-05-18 P1) if the
     * pool row exists but its {@code username} is null or blank — a provisioning invariant
     * violation. Story 11.E.2 backfills {@code speaker_pool.username} at {@code CONTACTED →
     * READY}; a request that survives the auth chain but lands on a row without a canonical
     * username indicates upstream data corruption and must NOT silently fall back to display
     * names (which would break {@code speaker_status_history.changed_by_username} joins to
     * {@code users.username}).
     *
     * @param username  the Cognito principal's username (from {@code Authentication#getName()})
     * @param eventCode the meaningful event identifier from the URL path
     * @return the matching {@link SpeakerPool} row
     * @throws SpeakerPortalAccessDeniedException if no row matches
     * @throws IllegalStateException if event is past or pool row's username is blank
     */
    @Transactional(readOnly = true)
    public SpeakerPool resolveSpeakerPool(String username, String eventCode) {
        // Code review 2026-05-18 (P20): hard cap on eventCode length avoids passing a
        // multi-kilobyte path segment straight into the JPA JOIN query (which would either
        // produce a 500 from the driver or do a wasteful full-scan match). VARCHAR(20) is
        // generous vs the BATbernNN format actually in use.
        if (eventCode == null || eventCode.isBlank() || eventCode.length() > 20) {
            throw new SpeakerPortalAccessDeniedException(
                    "No invitation found for the requested event");
        }

        Event event =
                eventRepository
                        .findByEventCode(eventCode)
                        .orElseThrow(
                                () ->
                                        new SpeakerPortalAccessDeniedException(
                                                "No invitation found for the requested event"));

        // Post-Epic-11 cleanup (2026-05-21): canonical identity is session_users.username
        // (NOT the deprecated speaker_pool.username column). Find PRIMARY_SPEAKER
        // memberships for this user, filter to the requested event, and derive the pool
        // row from the linked session.
        //
        // 2026-05-22 (BATbern75 bug report) — also reject structural slot memberships
        // (moderation/break/lunch/networking) so an organizer who self-assigned as
        // PRIMARY on a moderation slot can't accidentally win the deterministic-min
        // selection below and steal the authorization context from their real talk.
        List<SessionUser> memberships = sessionUserRepository.findByUsername(username).stream()
                .filter(su -> su.getSpeakerRole() == SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .filter(su -> event.getId().equals(su.getSession().getEventId()))
                .filter(su -> !su.getSession().isStructuralSlot())
                .toList();
        if (memberships.isEmpty()) {
            log.warn("Speaker portal access denied: username={} eventCode={}",
                    username, eventCode);
            throw new SpeakerPortalAccessDeniedException(
                    "No invitation found for the requested event");
        }
        // If the speaker is PRIMARY on multiple sessions in the same event (rare),
        // pick the deterministically-first one. The single-pool-row contract is a
        // legacy of pre-multi-session days — controllers that need session granularity
        // should evolve to a session-scoped endpoint.
        SessionUser membership = memberships.stream()
                .min(Comparator.comparing(su -> su.getSession().getId()))
                .orElseThrow();
        java.util.UUID sessionId = membership.getSession().getId();

        // Pool row lookup: prefer session.speakerPoolId back-reference (set by Story
        // 11.E.8 provisionSessionAndPrimarySpeaker); fall back to the reverse lookup
        // (legacy data where the back-ref wasn't backfilled).
        SpeakerPool pool = null;
        java.util.UUID poolIdHint = membership.getSession().getSpeakerPoolId();
        if (poolIdHint != null) {
            pool = speakerPoolRepository.findById(poolIdHint).orElse(null);
        }
        if (pool == null) {
            pool = speakerPoolRepository.findBySessionId(sessionId).stream()
                    .findFirst()
                    .orElse(null);
        }
        if (pool == null) {
            log.warn("Speaker portal access denied: no pool row for session={} username={}",
                    sessionId, username);
            throw new SpeakerPortalAccessDeniedException(
                    "No invitation found for the requested event");
        }

        if (event.getWorkflowState() == EventWorkflowState.EVENT_COMPLETED) {
            log.info(
                    "Speaker portal write rejected: event is past. username={} eventCode={}"
                            + " workflowState={}",
                    username,
                    eventCode,
                    event.getWorkflowState());
            throw new IllegalStateException("Event is closed; no further changes accepted");
        }

        return pool;
    }
}
