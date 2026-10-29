package ch.batbern.events.scheduler;

import ch.batbern.events.forwarding.EmailForwardingLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly retention for {@code email_forwarding_log} (#1138): rows older than 90 days are deleted.
 *
 * <p>The rows hold no personal data (S3 key, alias local parts, counts) and the volume is a few
 * hundred a year, so this only keeps the table bounded. 90 days is well past any use: the raw
 * mail in S3 expires after 7 days, so an older row can no longer be replayed.
 *
 * <p>Uses ShedLock to prevent duplicate execution in multi-instance ECS deployments.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailForwardingLogRetentionScheduler {

    static final int RETENTION_DAYS = 90;

    private final EmailForwardingLogService forwardingLog;

    @Scheduled(cron = "${batbern.email-forwarding-log-retention.cron:0 15 3 * * *}", zone = "Europe/Zurich")
    @SchedulerLock(
            name = "EmailForwardingLogRetentionScheduler_purgeOldEntries",
            lockAtLeastFor = "PT5M",
            lockAtMostFor = "PT15M"
    )
    public void purgeOldEntries() {
        try {
            int deleted = forwardingLog.purgeOlderThanDays(RETENTION_DAYS);
            log.info("Email forwarding log retention: {} row(s) older than {} days deleted",
                    deleted, RETENTION_DAYS);
        } catch (Exception e) {
            log.error("Email forwarding log retention failed: {}", e.getMessage(), e);
        }
    }
}
