package ch.batbern.events.mailcenter.inbound;

import ch.batbern.events.repository.MailConversationRepository;
import ch.batbern.events.repository.MailMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Nightly retention for recorded mail (Story 16.1a AC6, owner decision 1): a mail is deleted once
 * its event date and the mail itself are older than 12 months; mail without an event after 12
 * months. Recorded mail holds addresses and texts, i.e. personal data, so it is not kept longer.
 * Since Story 16.2a the same rule covers conversations (by last activity, with their inbound mail)
 * and the automatic counts (by receipt). Story 16.7: spam goes after 30 days.
 *
 * <p>Uses ShedLock to prevent duplicate execution in multi-instance ECS deployments.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MailMessageRetentionScheduler {

    static final int RETENTION_MONTHS = 12;
    /** Story 16.7 AC7: spam only 30 days. */
    static final int SPAM_RETENTION_DAYS = 30;
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    private final MailMessageRepository mailMessages;
    private final MailConversationRepository conversations;
    private final AutomaticMailCounter automaticMail;
    private final InboundAttachmentStore attachments;

    @Scheduled(cron = "${batbern.mail-message-retention.cron:0 30 3 * * *}", zone = "Europe/Zurich")
    @SchedulerLock(
            name = "MailMessageRetentionScheduler_purgeExpired",
            lockAtLeastFor = "PT5M",
            lockAtMostFor = "PT15M"
    )
    @Transactional
    public void purgeExpired() {
        try {
            Instant cutoff = ZonedDateTime.now(ZURICH).minusMonths(RETENTION_MONTHS).toInstant();
            int spamDeleted = mailMessages.deleteSpamReceivedBefore(
                    ZonedDateTime.now(ZURICH).minusDays(SPAM_RETENTION_DAYS).toInstant());
            // Story 16.2a AC14: conversations first (their messages go with them), then recorded mail,
            // then the automatic counts, all by the same rule.
            // Story 16.6: the kept attachment files of the conversations about to go
            List<String> files = conversations.expiredAttachmentKeys(cutoff);
            int conversationsDeleted = conversations.deleteExpired(cutoff);
            int deleted = mailMessages.deleteExpired(cutoff);
            int countsDeleted = automaticMail.deleteExpired(cutoff);
            attachments.delete(files);
            log.info("Mail message retention: {} conversation(s), {} mail(s) and {} automatic count(s) older than {}"
                    + " months deleted, {} spam mail(s) older than {} days", conversationsDeleted, deleted,
                    countsDeleted, RETENTION_MONTHS, spamDeleted, SPAM_RETENTION_DAYS);
        } catch (Exception e) {
            log.error("Mail message retention failed: {}", e.getMessage(), e);
        }
    }
}
