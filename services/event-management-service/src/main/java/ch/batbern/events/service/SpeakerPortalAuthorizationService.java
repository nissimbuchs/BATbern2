package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.exception.SpeakerPortalAccessDeniedException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        SpeakerPool pool =
                speakerPoolRepository
                        .findByEventCodeAndUsername(eventCode, username)
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "Speaker portal access denied: username={}"
                                                    + " eventCode={}",
                                            username,
                                            eventCode);
                                    return new SpeakerPortalAccessDeniedException(
                                            "No invitation found for the requested event");
                                });

        if (pool.getUsername() == null || pool.getUsername().isBlank()) {
            log.error(
                    "Provisioning invariant violation: speaker_pool row id={} for eventCode={}"
                            + " has null/blank username — refusing write path",
                    pool.getId(),
                    eventCode);
            throw new IllegalStateException(
                    "Speaker pool row has no canonical username — contact organizer");
        }

        Event event =
                eventRepository
                        .findByEventCode(eventCode)
                        .orElseThrow(
                                () ->
                                        new SpeakerPortalAccessDeniedException(
                                                "No invitation found for the requested event"));
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
