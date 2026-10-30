package ch.batbern.events.mailcenter;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.MailMessage;
import ch.batbern.events.domain.MailRecipient;
import ch.batbern.events.domain.NewsletterSend;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.MailMessageRepository;
import ch.batbern.events.repository.MailRecipientRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.mail.OutboundMail;
import ch.batbern.shared.mail.OutboundMailRecorder;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.service.TestMarkedEmailException;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.util.EmailContentTestMarker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.ses.SesClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 16.1a against real PostgreSQL: a send with a {@link MailContext} goes through the real
 * {@link EmailService} (no SES, as in tests) into {@code mail_message} + {@code mail_recipient}.
 *
 * <p>Not {@code @Transactional}: the recorder commits in its own transaction, so this class removes
 * its own rows.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Record every outbound mail (PostgreSQL)")
class MailRecordingIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern912";
    private static final String OLD_EVENT_CODE = "BATbern913";
    private static final String TO = "success+speaker@simulator.amazonses.com";
    private static final String CC = "success+speaker.private@simulator.amazonses.com";

    @Autowired
    private OutboundMailRecorder recorder;
    @Autowired
    private MailMessageRepository messages;
    @Autowired
    private MailRecipientRepository recipients;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private NewsletterSendRepository newsletterSendRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate tx;

    private EmailService emailService;
    private Event event;

    @BeforeEach
    void setUp() {
        cleanUp();
        emailService = new EmailService();
        ReflectionTestUtils.setField(emailService, "outboundMailRecorder", recorder);
        ReflectionTestUtils.setField(emailService, "replyToEmail", "replies@batbern.ch");
        event = eventRepository.save(event(EVENT_CODE, 912, Instant.now().plus(30, ChronoUnit.DAYS)));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM mail_message WHERE event_code IN (?, ?) OR (mail_key LIKE 'general-%'"
                + " AND template_key = 'mail-recording-test')", EVENT_CODE, OLD_EVENT_CODE);
        jdbc.update("DELETE FROM newsletter_sends WHERE event_id IN (SELECT id FROM events WHERE event_code IN (?, ?))",
                EVENT_CODE, OLD_EVENT_CODE);
        jdbc.update("DELETE FROM events WHERE event_code IN (?, ?)", EVENT_CODE, OLD_EVENT_CODE);
    }

    @Test
    @DisplayName("AC1/AC8: an individual mail is one message with a readable key and one row per address")
    void individualMail_recorded() {
        MailContext context = MailContext.individual(EVENT_CODE, MailContext.Audience.SPEAKERS, "speaker-invitation")
                .withSecrets("Temp-Pa55!");

        emailService.sendHtmlEmailSync(context, TO, List.of(CC), "Invitation", "<p>Password Temp-Pa55!</p>",
                null, null);

        MailMessage message = messages.findBySendGroupId(context.sendGroupId()).orElseThrow();
        assertThat(message.getMailKey()).matches(EVENT_CODE + "-\\d+");
        assertThat(message.getAudience()).isEqualTo(MailContext.Audience.SPEAKERS);
        assertThat(message.getTemplateKey()).isEqualTo("speaker-invitation");
        assertThat(message.getContentSource()).isEqualTo(MailContext.ContentSource.TEMPLATE);
        assertThat(message.getDirection()).isEqualTo("OUT");
        assertThat(message.getBodyHtml()).isEqualTo("<p>Password [redacted]</p>");
        assertThat(message.getRecipientCount()).isEqualTo(2);
        assertThat(recipients.findByMailMessageId(message.getId()))
                .extracting(MailRecipient::getAddress, MailRecipient::getKind, MailRecipient::getStatus,
                        MailRecipient::getBodyHtml)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(TO, MailRecipient.Kind.TO, MailRecipient.Status.SENT, null),
                        org.assertj.core.groups.Tuple.tuple(CC, MailRecipient.Kind.CC, MailRecipient.Status.SENT, null));
    }

    @Test
    @DisplayName("AC2: sends of one trigger, event and template share a message; a personalised body is kept per recipient")
    void groupedSends_oneMessage() {
        MailTrigger run = MailTrigger.organizer("organizer1");
        MailContext context = MailContext.of(run, EVENT_CODE, MailContext.Audience.REGISTRANTS, "slides-online");

        emailService.sendHtmlEmailSync(context, TO, List.of(), "Slides", "<p>Hello Anna</p>", null, null);
        emailService.sendHtmlEmailSync(MailContext.of(run, EVENT_CODE, MailContext.Audience.REGISTRANTS,
                "slides-online"), "success+bob@simulator.amazonses.com", List.of(), "Slides", "<p>Hello Bob</p>",
                null, null);

        assertThat(messages.findByEventCodeOrderByCreatedAtDesc(EVENT_CODE)).hasSize(1);
        MailMessage message = messages.findBySendGroupId(context.sendGroupId()).orElseThrow();
        assertThat(message.getSenderUsername()).isEqualTo("organizer1");
        assertThat(message.getRecipientCount()).isEqualTo(2);
        assertThat(message.getBodyHtml()).isEqualTo("<p>Hello Anna</p>");
        assertThat(recipients.findByMailMessageId(message.getId()))
                .extracting(MailRecipient::getBodyHtml)
                .containsExactlyInAnyOrder(null, "<p>Hello Bob</p>");
    }

    @Test
    @DisplayName("AC3: a newsletter is one message linked to newsletter_sends, counted, without recipient rows")
    void newsletter_linkedWithoutRecipientRows() {
        NewsletterSend send = newsletterSendRepository.save(NewsletterSend.builder()
                .eventId(event.getId()).templateKey("newsletter-event").sentAt(Instant.now())
                .sentByUsername("organizer1").recipientCount(2).build());
        MailContext context = MailContext.of(MailTrigger.forSend(send.getId(), "organizer1"), EVENT_CODE,
                MailContext.Audience.NEWSLETTER, "newsletter-event").forNewsletterSend(send.getId(), false);

        emailService.sendHtmlEmailSync(context, TO, List.of(), "News", "<p>n</p>", "newsletter-set", null);
        emailService.sendHtmlEmailSync(context, "success+bob@simulator.amazonses.com", List.of(), "News",
                "<p>n</p>", "newsletter-set", null);

        MailMessage message = messages.findBySendGroupId(context.sendGroupId()).orElseThrow();
        assertThat(message.getNewsletterSendId()).isEqualTo(send.getId());
        assertThat(message.getRecipientCount()).isEqualTo(2);
        assertThat(recipients.findByMailMessageId(message.getId())).isEmpty();
    }

    @Test
    @DisplayName("AC4: an SES failure is recorded as FAILED with the error code")
    void sesFailure_recordedFailed() {
        MailContext context = MailContext.individual(EVENT_CODE, MailContext.Audience.REGISTRANTS, "registration-confirmation");

        recorder.record(context, new OutboundMail(TO, List.of(), "S", "<p>b</p>", null, "MessageRejected"));

        MailMessage message = messages.findBySendGroupId(context.sendGroupId()).orElseThrow();
        assertThat(recipients.findByMailMessageId(message.getId()))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getStatus()).isEqualTo(MailRecipient.Status.FAILED);
                    assertThat(r.getFailureReason()).isEqualTo("MessageRejected");
                });
    }

    @Test
    @DisplayName("AC4/ADR-015: a test-marked send blocked before SES writes no row")
    void testMarked_writesNothing() {
        ReflectionTestUtils.setField(emailService, "sesClient", Mockito.mock(SesClient.class));
        MailContext context = MailContext.individual(EVENT_CODE, MailContext.Audience.SPEAKERS, "speaker-invitation");

        assertThatThrownBy(() -> emailService.sendHtmlEmailSync(context, TO, List.of(),
                "Invitation " + EmailContentTestMarker.PLAYWRIGHT_MARKER, "<p>b</p>", null, null))
                .isInstanceOf(TestMarkedEmailException.class);

        assertThat(messages.findBySendGroupId(context.sendGroupId())).isEmpty();
    }

    @Test
    @DisplayName("AC8: keys come from one sequence; mail without an event is general-{n}")
    void mailKeys_fromOneSequence() {
        MailContext first = MailContext.individual(EVENT_CODE, MailContext.Audience.ORGANIZERS, "task-reminder");
        MailContext general = MailContext.individual(null, MailContext.Audience.SYSTEM, "mail-recording-test");
        MailContext second = MailContext.individual(EVENT_CODE, MailContext.Audience.ORGANIZERS, "task-reminder");

        for (MailContext context : List.of(first, general, second)) {
            recorder.record(context, new OutboundMail(TO, List.of(), "S", "<p>b</p>", "ses-1", null));
        }

        long n1 = number(messages.findBySendGroupId(first.sendGroupId()).orElseThrow().getMailKey());
        String generalKey = messages.findBySendGroupId(general.sendGroupId()).orElseThrow().getMailKey();
        long n3 = number(messages.findBySendGroupId(second.sendGroupId()).orElseThrow().getMailKey());
        assertThat(generalKey).matches("general-\\d+");
        assertThat(number(generalKey)).isEqualTo(n1 + 1);
        assertThat(n3).isEqualTo(n1 + 2);
    }

    @Test
    @DisplayName("AC6: retention deletes mail of events past 12 months and old event-less mail, nothing else")
    void retention_deletesOnlyExpired() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(365, ChronoUnit.DAYS);
        eventRepository.save(event(OLD_EVENT_CODE, 913, now.minus(400, ChronoUnit.DAYS)));
        MailContext oldEvent = recordAged(OLD_EVENT_CODE, now.minus(420, ChronoUnit.DAYS));
        MailContext oldEventFreshMail = recordAged(OLD_EVENT_CODE, now.minus(10, ChronoUnit.DAYS));
        MailContext futureEventOldMail = recordAged(EVENT_CODE, now.minus(420, ChronoUnit.DAYS));
        MailContext oldGeneral = recordAged(null, now.minus(420, ChronoUnit.DAYS));
        MailContext freshGeneral = recordAged(null, now.minus(10, ChronoUnit.DAYS));

        Integer deleted = tx.execute(status -> messages.deleteExpired(cutoff));

        assertThat(deleted).isGreaterThanOrEqualTo(2);
        assertThat(messages.findBySendGroupId(oldEvent.sendGroupId())).isEmpty();
        assertThat(messages.findBySendGroupId(oldGeneral.sendGroupId())).isEmpty();
        assertThat(messages.findBySendGroupId(oldEventFreshMail.sendGroupId())).isPresent();
        assertThat(messages.findBySendGroupId(futureEventOldMail.sendGroupId())).isPresent();
        assertThat(messages.findBySendGroupId(freshGeneral.sendGroupId())).isPresent();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_recipient r WHERE NOT EXISTS"
                + " (SELECT 1 FROM mail_message m WHERE m.id = r.mail_message_id)", Integer.class)).isZero();
    }

    private MailContext recordAged(String eventCode, Instant createdAt) {
        MailContext context = MailContext.individual(eventCode, MailContext.Audience.SYSTEM, "mail-recording-test");
        recorder.record(context, new OutboundMail(TO, List.of(), "S", "<p>b</p>", "ses-1", null));
        jdbc.update("UPDATE mail_message SET created_at = ? WHERE send_group_id = ?",
                Timestamp.from(createdAt), context.sendGroupId());
        return context;
    }

    private static long number(String mailKey) {
        return Long.parseLong(mailKey.substring(mailKey.lastIndexOf('-') + 1));
    }

    private static Event event(String code, int number, Instant date) {
        return Event.builder()
                .eventCode(code)
                .title("BATbern Test Event #" + number)
                .eventNumber(number)
                .date(date)
                .registrationDeadline(date.minus(1, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Bern")
                .venueCapacity(200)
                .organizerUsername("organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build();
    }
}
