package ch.batbern.events.service;

import ch.batbern.events.domain.QnaWindowStatus;
import ch.batbern.events.domain.SessionQnaWindow;
import ch.batbern.events.repository.SessionQnaWindowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled freeze of expired Q&A windows (Story 7.5, AC5/AC6).
 *
 * <p>Hourly, any {@code OPEN} window whose {@code closesAt} has passed is flipped to {@code FROZEN}
 * (read-only). Modeled on the {@code @Scheduled + @SchedulerLock} jobs in
 * {@code EventWorkflowScheduledService}; the {@code LockProvider} comes from {@code ShedLockConfig}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionQnaScheduledService {

    private final SessionQnaWindowRepository windowRepository;

    /**
     * Freeze windows whose close time has passed. Runs hourly; ShedLock ensures a single instance
     * runs it across the cluster.
     */
    @Scheduled(cron = "${qna.scheduled.freeze.cron:0 5 * * * *}")
    @SchedulerLock(name = "freezeQnaWindows", lockAtMostFor = "5m", lockAtLeastFor = "30s")
    @Transactional
    public void freezeExpiredWindows() {
        Instant now = Instant.now();
        List<SessionQnaWindow> expired =
                windowRepository.findByStatusAndClosesAtBefore(QnaWindowStatus.OPEN, now);
        if (expired.isEmpty()) {
            log.debug("freezeQnaWindows: no windows to freeze");
            return;
        }
        for (SessionQnaWindow window : expired) {
            window.setStatus(QnaWindowStatus.FROZEN);
        }
        windowRepository.saveAll(expired);
        log.info("freezeQnaWindows: froze {} expired Q&A window(s)", expired.size());
    }
}
