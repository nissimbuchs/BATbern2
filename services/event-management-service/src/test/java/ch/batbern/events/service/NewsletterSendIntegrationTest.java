package ch.batbern.events.service;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterSend;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.shared.service.TestMarkedEmailException;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.test.mail.SesRecorder.RecordedMail;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The newsletter send against real PostgreSQL and the real {@code EmailService} (SES is the
 * {@link SesRecorder}). Replaces the former mock-verify unit tests of {@code sendNewsletter}
 * (Story 16.8c touch rule): the queued row, the mails subscribers receive, the newsletter
 * configuration set, and the walls that refuse a send before any row is written. The duplicate-send
 * guard (PENDING, IN_PROGRESS, registrant notices) is in {@code SlidesOnlineIntegrationTest}. Not
 * {@code @Transactional}: the send runs on an {@code @Async} thread, so the class removes its rows.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Newsletter send (Story 10.7, 16.8c)")
class NewsletterSendIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern761";
    private static final String SUBSCRIBERS = "success+nl761-%";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NewsletterEmailService newsletterEmailService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private NewsletterSubscriberRepository subscriberRepository;
    @Autowired
    private NewsletterSendRepository sendRepository;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private JdbcTemplate jdbc;

    private Event event;

    @BeforeEach
    void setUp() {
        cleanUp();
        sesRecorder.reset();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        event = eventRepository.saveAndFlush(Event.builder()
                .eventCode(EVENT_CODE).title("Newsletter Test Event").eventNumber(761)
                .date(now.plus(30, ChronoUnit.DAYS)).registrationDeadline(now.plus(29, ChronoUnit.DAYS))
                .venueName("Test Venue").venueAddress("Bern").venueCapacity(100)
                .organizerUsername("org.user").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());
    }

    @AfterEach
    void cleanUp() {
        sesRecorder.awaitAsyncSends();
        jdbc.update("DELETE FROM mail_message WHERE event_code = ?", EVENT_CODE);
        jdbc.update("DELETE FROM newsletter_recipients WHERE send_id IN (SELECT s.id FROM newsletter_sends s"
                + " JOIN events e ON e.id = s.event_id WHERE e.event_code = ?)", EVENT_CODE);
        jdbc.update("DELETE FROM newsletter_sends WHERE event_id IN (SELECT id FROM events WHERE event_code = ?)",
                EVENT_CODE);
        jdbc.update("DELETE FROM newsletter_subscribers WHERE email LIKE ?", SUBSCRIBERS);
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT_CODE);
    }

    @Test
    @WithMockUser(username = "org.user", roles = "ORGANIZER")
    @DisplayName("a newsletter is queued as a row with its template, and every active subscriber gets one mail")
    void send_queuesRow_andMailsEverySubscriberOnce() throws Exception {
        subscriber("a");
        subscriber("b");

        mockMvc.perform(post("/api/v1/events/{eventCode}/mails", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"NEWSLETTER\",\"locale\":\"de\",\"target\":\"RECIPIENTS\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recipientCount").isNumber());

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mailsTo(sim("a"))).hasSize(1);
        assertThat(sesRecorder.mailsTo(sim("b"))).hasSize(1);
        assertThat(sendsOfEvent()).singleElement().satisfies(send -> {
            assertThat(send.getTemplateKey()).isEqualTo("newsletter-event");
            assertThat(send.getSentByUsername()).isEqualTo("org.user");
            assertThat(send.isTestMode()).isFalse();
        });
    }

    @Test
    @DisplayName("the newsletter goes out with the newsletter configuration set, when one is configured")
    void send_usesNewsletterConfigurationSet() {
        subscriber("cfg");
        NewsletterEmailService target = AopTestUtils.getUltimateTargetObject(newsletterEmailService);
        Object original = ReflectionTestUtils.getField(target, "configurationSetName");
        ReflectionTestUtils.setField(target, "configurationSetName", "batbern-newsletter-tracking");
        try {
            newsletterEmailService.sendNewsletter(event, false, "de", "org.user", null);
            sesRecorder.awaitAsyncSends();
        } finally {
            ReflectionTestUtils.setField(target, "configurationSetName", original);
        }

        assertThat(sesRecorder.mailsTo(sim("cfg"))).singleElement()
                .extracting(RecordedMail::configurationSetName).isEqualTo("batbern-newsletter-tracking");
    }

    @Test
    @DisplayName("a REGISTRANT_NOTICE template is refused for subscribers (400): no row, no mail")
    void send_refusesRegistrantNoticeTemplate() {
        subscriber("reg");

        assertThatThrownBy(() -> newsletterEmailService.sendNewsletter(event, false, "de", "org.user",
                "slides-online"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("registrants audience");

        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).isEmpty();
        assertThat(sendsOfEvent()).isEmpty();
    }

    @Test
    @DisplayName("a test-marked event is refused before any row is written; positive control: a normal one queues")
    void send_refusesTestMarkedEvent() {
        subscriber("mark");
        event.setTitle("BATPW-E2E 1782885868836"); // the wall reads the event it is handed

        assertThatThrownBy(() -> newsletterEmailService.sendNewsletter(event, false, "de", "org.user", null))
                .isInstanceOf(TestMarkedEmailException.class);
        sesRecorder.awaitAsyncSends();
        assertThat(sesRecorder.mails()).isEmpty();
        assertThat(sendsOfEvent()).isEmpty();

        event.setTitle("Newsletter Test Event");
        QueuedSend queued = newsletterEmailService.sendNewsletter(event, false, "de", "org.user", null);
        sesRecorder.awaitAsyncSends();
        assertThat(sendsOfEvent()).extracting(NewsletterSend::getId).containsExactly(queued.sendId());
        assertThat(sesRecorder.mailsTo(sim("mark"))).hasSize(1);
    }

    private void subscriber(String name) {
        subscriberRepository.saveAndFlush(NewsletterSubscriber.builder().email(sim(name)).language("de")
                .source("explicit").unsubscribeToken("tok-761-" + name).build());
    }

    private List<NewsletterSend> sendsOfEvent() {
        return sendRepository.findAll().stream().filter(s -> event.getId().equals(s.getEventId())).toList();
    }

    /** A class-scoped address of the SES mailbox simulator (nothing is delivered in tests). */
    private static String sim(String name) {
        return "success+nl761-" + name + "@simulator.amazonses.com";
    }
}
