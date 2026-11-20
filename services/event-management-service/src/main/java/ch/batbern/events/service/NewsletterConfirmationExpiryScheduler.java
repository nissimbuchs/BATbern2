package ch.batbern.events.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly expiry of newsletter requests nobody confirmed (#1288, story 3.5): the confirmation mail
 * says its link works for 7 days. Rows never confirmed are deleted, an unsubscribed row only loses
 * its expired token.
 *
 * <p>Uses ShedLock to prevent duplicate execution in multi-instance ECS deployments.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NewsletterConfirmationExpiryScheduler {

    private final NewsletterSubscriberService subscriberService;

    @Scheduled(cron = "${batbern.newsletter-confirmation-expiry.cron:0 45 3 * * *}", zone = "Europe/Zurich")
    @SchedulerLock(
            name = "NewsletterConfirmationExpiryScheduler_expireUnconfirmedRequests",
            lockAtLeastFor = "PT5M",
            lockAtMostFor = "PT15M"
    )
    public void expireUnconfirmedRequests() {
        try {
            int[] result = subscriberService.expireUnconfirmedRequests();
            log.info("Newsletter confirmation expiry: {} unconfirmed request(s) deleted, {} expired link(s) cleared",
                    result[0], result[1]);
        } catch (Exception e) {
            log.error("Newsletter confirmation expiry failed: {}", e.getMessage(), e);
        }
    }
}
