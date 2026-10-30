package ch.batbern.events.scheduler;

import ch.batbern.events.repository.MailMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Nightly retention for recorded mail (Story 16.1a AC6, owner decision 1): a mail is deleted once
 * its event date and the mail itself are older than 12 months; mail without an event after 12
 * months. Recorded mail holds addresses and texts, i.e. personal data, so it is not kept longer.
 *
 * <p>Uses ShedLock to prevent duplicate execution in multi-instance ECS deployments.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MailMessageRetentionScheduler {

    static final int RETENTION_MONTHS = 12;
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    private final MailMessageRepository mailMessages;

    @Scheduled(cron = "${batbern.mail-message-retention.cron:0 30 3 * * *}", zone = "Europe/Zurich")
    @SchedulerLock(
            name = "MailMessageRetentionScheduler_purgeExpired",
            lockAtLeastFor = "PT5M",
            lockAtMostFor = "PT15M"
    )
    @Transactional
    public void purgeExpired() {
        try {
            int deleted = mailMessages.deleteExpired(
                    ZonedDateTime.now(ZURICH).minusMonths(RETENTION_MONTHS).toInstant());
            log.info("Mail message retention: {} mail(s) older than {} months deleted", deleted, RETENTION_MONTHS);
        } catch (Exception e) {
            log.error("Mail message retention failed: {}", e.getMessage(), e);
        }
    }
}
