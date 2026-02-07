package ch.batbern.events.scheduler;

import ch.batbern.events.repository.SpeakerInvitationTokenRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled job to clean up expired magic link tokens.
 * Story 6.1a: Magic Link Infrastructure (AC4)
 *
 * Runs daily at 3 AM to delete tokens that:
 * - Have been expired for more than 90 days
 *
 * Uses ShedLock to prevent duplicate execution in multi-instance deployments.
 */
@Component
public class TokenCleanupScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(TokenCleanupScheduler.class);

    /**
     * Retention period: Delete tokens expired more than 90 days ago.
     * This allows for auditing/debugging of recent expired tokens.
     */
    private static final long RETENTION_DAYS = 90;

    private final SpeakerInvitationTokenRepository tokenRepository;

    public TokenCleanupScheduler(SpeakerInvitationTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * Clean up expired tokens older than retention period.
     * Runs daily at 3:00 AM.
     *
     * ShedLock ensures only one instance runs in multi-node deployment.
     * Lock is held for minimum 5 minutes, maximum 30 minutes.
     */
    @Scheduled(cron = "0 0 3 * * *") // Every day at 3:00 AM
    @SchedulerLock(
            name = "TokenCleanupScheduler_cleanupExpiredTokens",
            lockAtLeastFor = "PT5M",   // Hold lock for at least 5 minutes
            lockAtMostFor = "PT30M"    // Release lock after max 30 minutes
    )
    @Transactional
    public void cleanupExpiredTokens() {
        LOG.info("Starting expired token cleanup job");

        Instant cutoffDate = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        int deletedCount = tokenRepository.deleteExpiredBefore(cutoffDate);

        LOG.info("Token cleanup completed: deleted {} expired tokens older than {} days",
                deletedCount, RETENTION_DAYS);
    }
}
