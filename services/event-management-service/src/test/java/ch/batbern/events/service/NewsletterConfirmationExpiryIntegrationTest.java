package ch.batbern.events.service;

import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #1288 story 3.5: a newsletter request nobody confirms expires after the 7 days the mail names.
 * The nightly job deletes rows that were never confirmed and clears the expired token of an
 * unsubscribed row; confirmed and suppressed rows stay. An expired link answers 404.
 */
class NewsletterConfirmationExpiryIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-20T02:45:00Z");

    /** What the nightly NewsletterConfirmationExpiryScheduler runs (its ShedLock would skip repeat calls). */
    @Autowired
    private NewsletterSubscriberService subscriberService;

    @Autowired
    private NewsletterSubscriberRepository subscriberRepository;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        subscriberRepository.deleteAll();
        clock.setInstant(NOW);
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        subscriberRepository.deleteAll();
    }

    @Test
    void should_deleteOnlyRequestsPastTheWindow_when_jobRuns() {
        save("old.pending@batbern-test.ch", "tok-old", ago(8), null, null, null);
        save("young.pending@batbern-test.ch", "tok-young", ago(6), null, null, null);
        // an old request whose mail was sent again three days ago counts from that mail
        NewsletterSubscriber resent = save("resent.pending@batbern-test.ch", "tok-resent", ago(20), null, null, null);
        resent.setConfirmationSentAt(ago(3));
        subscriberRepository.save(resent);
        save("active@batbern-test.ch", null, ago(400), ago(400), null, null);
        save("suppressed.pending@batbern-test.ch", "tok-supp", ago(30), null, null, ago(29));
        save("unsubscribed.request@batbern-test.ch", "tok-unsub", ago(10), ago(300), ago(200), null);

        subscriberService.expireUnconfirmedRequests();

        assertThat(subscriberRepository.findByEmailIgnoreCase("old.pending@batbern-test.ch")).isEmpty();
        assertThat(subscriberRepository.findByEmailIgnoreCase("young.pending@batbern-test.ch")).isPresent();
        assertThat(subscriberRepository.findByEmailIgnoreCase("resent.pending@batbern-test.ch")).isPresent();
        assertThat(subscriberRepository.findByEmailIgnoreCase("active@batbern-test.ch")).isPresent();
        assertThat(subscriberRepository.findByEmailIgnoreCase("suppressed.pending@batbern-test.ch")).isPresent();
        NewsletterSubscriber unsubscribed =
                subscriberRepository.findByEmailIgnoreCase("unsubscribed.request@batbern-test.ch").orElseThrow();
        assertThat(unsubscribed.getConfirmationToken()).isNull();
        assertThat(unsubscribed.getUnsubscribedAt()).isNotNull();
    }

    @Test
    void should_waitForTheRegistrationsLink_when_optInCameWithARegistration() {
        // no mail of its own: it waits up to 14 days for the registration's link (resends, cleanup)
        registrationOptIn("reg.ten.days@batbern-test.ch", ago(10), null);
        registrationOptIn("reg.fifteen.days@batbern-test.ch", ago(15), null);
        // an organizer's confirmation sent the newsletter mail: the 7 days of that mail apply
        registrationOptIn("reg.mailed@batbern-test.ch", ago(10), ago(8));

        subscriberService.expireUnconfirmedRequests();

        assertThat(subscriberRepository.findByEmailIgnoreCase("reg.ten.days@batbern-test.ch")).isPresent();
        assertThat(subscriberRepository.findByEmailIgnoreCase("reg.fifteen.days@batbern-test.ch")).isEmpty();
        assertThat(subscriberRepository.findByEmailIgnoreCase("reg.mailed@batbern-test.ch")).isEmpty();
    }

    @Test
    void should_countFromTheNewRequest_when_anOldUnsubscribedRowIsAskedAgain() {
        // unsubscribed a year ago, ticked the box at a registration today
        NewsletterSubscriber again = save("again@batbern-test.ch", "tok-again", ago(400), ago(400), ago(300), null);
        again.setSource("registration");
        again.setConfirmationRequestedAt(NOW);
        subscriberRepository.save(again);
        // unsubscribed, asked again, then bounced: suppressed rows are never touched
        save("bounced@batbern-test.ch", "tok-bounced", ago(40), ago(300), ago(200), ago(30));

        subscriberService.expireUnconfirmedRequests();

        assertThat(subscriberRepository.findByEmailIgnoreCase("again@batbern-test.ch").orElseThrow()
                .getConfirmationToken()).isEqualTo("tok-again");
        assertThat(subscriberRepository.findByEmailIgnoreCase("bounced@batbern-test.ch").orElseThrow()
                .getConfirmationToken()).isEqualTo("tok-bounced");
    }

    @Test
    void should_refuseTheLink_when_itsWindowHasPassed() throws Exception {
        save("late@batbern-test.ch", "tok-late", ago(8), null, null, null);
        save("in.time@batbern-test.ch", "tok-in-time", ago(6), null, null, null);

        mockMvc.perform(post("/api/v1/newsletter/confirmations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"tok-late\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/newsletter/confirmations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"tok-in-time\"}"))
                .andExpect(status().isOk());

        assertThat(subscriberRepository.findByEmailIgnoreCase("late@batbern-test.ch").orElseThrow()
                .getConfirmedAt()).isNull();
        assertThat(subscriberRepository.findByEmailIgnoreCase("in.time@batbern-test.ch").orElseThrow()
                .getConfirmedAt()).isNotNull();
    }

    private void registrationOptIn(String email, Instant requestedAt, Instant mailedAt) {
        NewsletterSubscriber sub = save(email, "tok-" + email, requestedAt, null, null, null);
        sub.setSource("registration");
        sub.setConfirmationRequestedAt(requestedAt);
        sub.setConfirmationSentAt(mailedAt);
        subscriberRepository.save(sub);
    }

    private static Instant ago(int days) {
        return NOW.minus(Duration.ofDays(days));
    }

    private NewsletterSubscriber save(String email, String token, Instant subscribedAt, Instant confirmedAt,
                                      Instant unsubscribedAt, Instant suppressedAt) {
        return subscriberRepository.save(NewsletterSubscriber.builder()
                .email(email).language("de").source("explicit")
                .unsubscribeToken("unsub-" + email).confirmationToken(token)
                .subscribedAt(subscribedAt).confirmedAt(confirmedAt)
                .unsubscribedAt(unsubscribedAt).suppressedAt(suppressedAt).build());
    }
}
