package ch.batbern.companyuser.watch;

import ch.batbern.companyuser.watch.repository.WatchPairingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled cleanup service for expired Watch pairing codes.
 * Story W2.1: Task 4.6 — Expiry cleanup job (AC3)
 *
 * Cleanup rule: DELETE rows WHERE pairedAt IS NULL AND pairingCodeExpiresAt < now
 * These are pending (unpairing) code rows whose 24h TTL has passed without being used.
 *
 * Runs daily at 3 AM UTC. ShedLock ensures only ONE ECS instance executes this job.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WatchPairingCleanupService {

    private final WatchPairingRepository watchPairingRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(
            name = "cleanupExpiredWatchPairingCodes",
            lockAtMostFor = "10m",
            lockAtLeastFor = "1m"
    )
    @Transactional
    public void cleanupExpiredPairingCodes() {
        LocalDateTime now = LocalDateTime.now();
        int deleted = watchPairingRepository.deleteExpiredPendingCodes(now);
        if (deleted > 0) {
            log.info("Cleaned up {} expired Watch pairing code rows", deleted);
        }
    }
}
