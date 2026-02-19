package ch.batbern.events.watch;

import ch.batbern.events.exception.SessionNotFoundException;
import ch.batbern.events.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Session lifecycle control via Watch organizer actions.
 * W4.2 Task 7 (AC2, AC4): Advances session state in response to endSession actions.
 *
 * Design guardrails (epic-4-reuse-map.md Area 4):
 * - No TransitionViewModel — server-authoritative state flows via WatchPresenceService broadcast.
 * - No new CachedSession fields — completedByUsername + actualEndTime already defined in V56 migration.
 * - Idempotent: second call with same slug re-broadcasts without a second write.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchSessionService {

    private final SessionRepository sessionRepository;
    private final WatchPresenceService watchPresenceService;

    /**
     * Marks a session as completed by the given organizer.
     * Idempotent: if the session is already completed, re-broadcasts current state without a second write.
     *
     * @param eventCode           event the session belongs to
     * @param sessionSlug         public slug identifying the session
     * @param completedByUsername username of the organizer who tapped Done
     * @throws SessionNotFoundException if no session with the given slug exists in the event
     */
    @Transactional
    public void endSession(String eventCode, String sessionSlug, String completedByUsername) {
        var session = sessionRepository.findByEventCodeAndSessionSlug(eventCode, sessionSlug)
                .orElseThrow(() -> new SessionNotFoundException(sessionSlug, eventCode));

        if (session.getCompletedByUsername() != null) {
            log.info("Session {} already completed by {} — idempotent skip, re-broadcasting",
                    sessionSlug, session.getCompletedByUsername());
            watchPresenceService.broadcastSessionEnded(
                    eventCode, sessionSlug, session.getCompletedByUsername());
            return;
        }

        Instant actualEndTime = Instant.now();
        long overrunMinutes = 0;
        if (session.getEndTime() != null) {
            overrunMinutes = Math.max(0,
                    ChronoUnit.MINUTES.between(session.getEndTime(), actualEndTime));
        }

        session.setActualEndTime(actualEndTime);
        session.setOverrunMinutes((int) overrunMinutes);
        session.setCompletedByUsername(completedByUsername);
        sessionRepository.save(session);

        // Auto-start next session: set actualStartTime so the Watch can show the "Delayed" button
        // in the first 10 minutes (shouldShowDelayed requires actualStartTime != null).
        // W4.3: delayToPreviousSession() clears actualStartTime when re-activating the previous
        // session, so this is safe to set here — it gets cleared if the organizer goes back.
        var nextSessions = sessionRepository
                .findByEventCodeAndScheduledStartTimeAfterOrderByScheduledStartTime(
                        eventCode, session.getStartTime());
        if (!nextSessions.isEmpty()) {
            var nextSession = nextSessions.get(0);
            if (nextSession.getActualStartTime() == null && nextSession.getActualEndTime() == null) {
                nextSession.setActualStartTime(actualEndTime);
                sessionRepository.save(nextSession);
                log.debug("Auto-started next session {} (actualStartTime={})",
                        nextSession.getSessionSlug(), actualEndTime);
            }
        }

        watchPresenceService.broadcastSessionEnded(eventCode, sessionSlug, completedByUsername);
        log.debug("Session {} ended by {} (overrun: {} min)",
                sessionSlug, completedByUsername, overrunMinutes);
    }

    /**
     * Extends the active session's scheduled end time by the given minutes.
     * All downstream sessions are shifted by the same amount (cascade).
     * Idempotent: if the session is already completed, re-broadcasts without a second write.
     *
     * W4.3 Task 8 (AC2, AC7).
     *
     * @param eventCode    event the session belongs to
     * @param sessionSlug  public slug identifying the session
     * @param minutesAdded number of minutes to add
     * @param requestedBy  username of the organizer who tapped Extend
     * @throws SessionNotFoundException if no session with the given slug exists in the event
     */
    @Transactional
    public void extendSession(String eventCode, String sessionSlug, int minutesAdded, String requestedBy) {
        var session = sessionRepository.findByEventCodeAndSessionSlug(eventCode, sessionSlug)
                .orElseThrow(() -> new SessionNotFoundException(sessionSlug, eventCode));

        if (session.getCompletedByUsername() != null) {
            log.info("Session {} already completed — idempotent extend skip, re-broadcasting",
                    sessionSlug);
            watchPresenceService.buildAndBroadcastState(eventCode, "SESSION_EXTENDED", sessionSlug, requestedBy);
            return;
        }

        // Extend current session end time — truncate to minute so fractional seconds never accumulate
        Instant oldEnd = session.getEndTime();
        session.setEndTime(oldEnd.truncatedTo(ChronoUnit.MINUTES).plusSeconds(minutesAdded * 60L));
        sessionRepository.save(session);

        // Cascade: shift all sessions starting after the old end time
        var downstream = sessionRepository
                .findByEventCodeAndScheduledStartTimeAfterOrderByScheduledStartTime(eventCode, oldEnd);
        for (var ds : downstream) {
            ds.setStartTime(ds.getStartTime().truncatedTo(ChronoUnit.MINUTES).plusSeconds(minutesAdded * 60L));
            ds.setEndTime(ds.getEndTime().truncatedTo(ChronoUnit.MINUTES).plusSeconds(minutesAdded * 60L));
        }
        if (!downstream.isEmpty()) {
            sessionRepository.saveAll(downstream);
        }

        watchPresenceService.buildAndBroadcastState(eventCode, "SESSION_EXTENDED", sessionSlug, requestedBy);
        log.debug("Session {} extended by {} min (requested by {})", sessionSlug, minutesAdded, requestedBy);
    }

    /**
     * Re-activates the previous session by resetting the current session to SCHEDULED
     * and extending the previous session's end time.
     * Idempotent: if the previous session is already ACTIVE, re-broadcasts without a second write.
     *
     * W4.3 Task 9 (AC4, AC7).
     *
     * @param eventCode    event the session belongs to
     * @param currentSlug  public slug identifying the current (to-be-reset) session
     * @param minutesAdded number of minutes to add to the previous session
     * @param requestedBy  username of the organizer who tapped Delayed
     * @throws SessionNotFoundException if no session with the given slug exists in the event
     * @throws IllegalStateException if no previous session exists
     */
    @Transactional
    public void delayToPreviousSession(String eventCode, String currentSlug, int minutesAdded, String requestedBy) {
        var current = sessionRepository.findByEventCodeAndSessionSlug(eventCode, currentSlug)
                .orElseThrow(() -> new SessionNotFoundException(currentSlug, eventCode));

        // Find previous session (the one scheduled immediately before current)
        var maybePrevious = sessionRepository
                .findFirstByEventCodeAndScheduledStartTimeBeforeOrderByScheduledStartTimeDesc(
                        eventCode, current.getStartTime());

        // No previous session — this is the first session. Shift it and all downstream forward.
        if (maybePrevious.isEmpty()) {
            var toShift = sessionRepository
                    .findByEventCodeAndScheduledStartTimeGreaterThanEqualOrderByScheduledStartTime(
                            eventCode, current.getStartTime());
            for (var s : toShift) {
                s.setStartTime(s.getStartTime().truncatedTo(ChronoUnit.MINUTES).plusSeconds(minutesAdded * 60L));
                s.setEndTime(s.getEndTime().truncatedTo(ChronoUnit.MINUTES).plusSeconds(minutesAdded * 60L));
            }
            sessionRepository.saveAll(toShift);
            watchPresenceService.buildAndBroadcastState(
                    eventCode, "SESSION_DELAYED", currentSlug, requestedBy);
            log.debug("First session {} delayed by {} min — all sessions shifted forward",
                    currentSlug, minutesAdded);
            return;
        }

        var previous = maybePrevious.get();

        // Idempotency: if previous already has actualStartTime set and actualEndTime is null → ACTIVE
        if (previous.getActualStartTime() != null && previous.getActualEndTime() == null
                && current.getActualStartTime() == null) {
            log.info("Previous session {} already active — idempotent delay skip, re-broadcasting",
                    previous.getSessionSlug());
            watchPresenceService.buildAndBroadcastStateWithPreviousSlug(
                    eventCode, "SESSION_DELAYED", currentSlug, requestedBy, previous.getSessionSlug());
            return;
        }

        // Reset current to SCHEDULED
        current.setActualStartTime(null);
        sessionRepository.save(current);

        // Extend previous and re-activate (set actualEndTime to null to mark as active)
        previous.setEndTime(previous.getEndTime().truncatedTo(ChronoUnit.MINUTES).plusSeconds(minutesAdded * 60L));
        previous.setActualEndTime(null);
        previous.setCompletedByUsername(null);
        previous.setOverrunMinutes(null);
        sessionRepository.save(previous);

        // Shift current + all downstream
        var toShift = sessionRepository
                .findByEventCodeAndScheduledStartTimeGreaterThanEqualOrderByScheduledStartTime(
                        eventCode, current.getStartTime());
        for (var s : toShift) {
            s.setStartTime(s.getStartTime().truncatedTo(ChronoUnit.MINUTES).plusSeconds(minutesAdded * 60L));
            s.setEndTime(s.getEndTime().truncatedTo(ChronoUnit.MINUTES).plusSeconds(minutesAdded * 60L));
        }
        sessionRepository.saveAll(toShift);

        watchPresenceService.buildAndBroadcastStateWithPreviousSlug(
                eventCode, "SESSION_DELAYED", currentSlug, requestedBy, previous.getSessionSlug());
        log.debug("Delayed to previous session {} (+{} min), current {} reset to SCHEDULED",
                previous.getSessionSlug(), minutesAdded, currentSlug);
    }
}
