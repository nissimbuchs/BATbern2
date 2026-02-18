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
                .orElseThrow(() -> new SessionNotFoundException(sessionSlug));

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

        watchPresenceService.broadcastSessionEnded(eventCode, sessionSlug, completedByUsername);
        log.debug("Session {} ended by {} (overrun: {} min)",
                sessionSlug, completedByUsername, overrunMinutes);
    }
}
