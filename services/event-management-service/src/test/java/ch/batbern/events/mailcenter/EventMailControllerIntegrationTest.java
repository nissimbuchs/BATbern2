package ch.batbern.events.mailcenter;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 16.3 AC5/AC6 against real PostgreSQL: what went out for an event, newest first, the
 * newsletter union with {@code newsletter_sends}, collapsed automatic mails, and the organizer gate.
 * Synthetic SES simulator addresses only.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Event mail page API (Story 16.3)")
class EventMailControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern915";
    private static final String BASE = "/api/v1/events/" + EVENT_CODE + "/mail-messages";
    private static final String A = "success+anna@simulator.amazonses.com";
    private static final String B = "bounce+bob@simulator.amazonses.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Event event;
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void setUp() {
        event = eventRepository.saveAndFlush(Event.builder()
                .eventCode(EVENT_CODE).title("BATbern Test Event #915").eventNumber(915)
                .date(now.plus(30, ChronoUnit.DAYS)).registrationDeadline(now.plus(29, ChronoUnit.DAYS))
                .venueName("Test Venue").venueAddress("Bern").venueCapacity(200)
                .organizerUsername("organizer").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("AC2: an organizer send lists sender, audience, source, recipients and delivered/failed counts")
    void organizerSend_listedWithCounts() throws Exception {
        UUID id = message(new Mail("BATbern915-1", "SPEAKERS", "speaker-reminder-content-1", "TEMPLATE", "organizer1",
                null, "Reminder", now.minus(1, ChronoUnit.HOURS), EVENT_CODE));
        recipient(id, A, "DELIVERED", null);
        recipient(id, B, "FAILED", "Permanent/NoEmail");

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].mailKey").value("BATbern915-1"))
                .andExpect(jsonPath("$.data[0].senderUsername").value("organizer1"))
                .andExpect(jsonPath("$.data[0].audience").value("SPEAKERS"))
                .andExpect(jsonPath("$.data[0].contentSource").value("TEMPLATE"))
                .andExpect(jsonPath("$.data[0].subject").value("Reminder"))
                .andExpect(jsonPath("$.data[0].recipientCount").value(2))
                .andExpect(jsonPath("$.data[0].deliveredCount").value(1))
                .andExpect(jsonPath("$.data[0].failedCount").value(1))
                .andExpect(jsonPath("$.data[0].messageCount").value(1))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("AC1/AC6: a newsletter without a record is backfilled from newsletter_sends, newest first")
    void newsletterWithoutRecord_backfilled() throws Exception {
        UUID sendId = newsletterSend("newsletter-event", now.minus(2, ChronoUnit.DAYS), 3);
        newsletterRecipient(sendId, A, "sent", null);
        newsletterRecipient(sendId, B, "sent", "hard");
        newsletterRecipient(sendId, "success+carla@simulator.amazonses.com", "failed", null);
        message(new Mail("BATbern915-2", "VENUE", null, "OWN_TEXT", "organizer1", null, "Catering",
                now.minus(1, ChronoUnit.DAYS), EVENT_CODE));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].mailKey", contains("BATbern915-2", "newsletter-" + sendId)))
                .andExpect(jsonPath("$.data[1].audience").value("NEWSLETTER"))
                .andExpect(jsonPath("$.data[1].subject").value(nullValue()))
                .andExpect(jsonPath("$.data[1].recipientCount").value(3))
                .andExpect(jsonPath("$.data[1].deliveredCount").value(1))
                .andExpect(jsonPath("$.data[1].failedCount").value(2))
                .andExpect(jsonPath("$.data[1].newsletterStatus").value("COMPLETED"));

        mockMvc.perform(get(BASE + "/newsletter-" + sendId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bodyHtml").value(nullValue()))
                .andExpect(jsonPath("$.recipients", hasSize(2)))
                .andExpect(jsonPath("$.recipients[*].status", contains("FAILED", "FAILED")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("AC6: a newsletter with a record is listed once, with counts from newsletter_recipients")
    void recordedNewsletter_notDuplicated() throws Exception {
        UUID sendId = newsletterSend("newsletter-event", now.minus(3, ChronoUnit.HOURS), 2);
        newsletterRecipient(sendId, A, "sent", null);
        newsletterRecipient(sendId, B, "sent", "soft");
        message(new Mail("BATbern915-3", "NEWSLETTER", "newsletter-event", "TEMPLATE", "organizer1", sendId, "News",
                now.minus(3, ChronoUnit.HOURS), EVENT_CODE));

        mockMvc.perform(get(BASE))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].mailKey").value("BATbern915-3"))
                .andExpect(jsonPath("$.data[0].deliveredCount").value(1))
                .andExpect(jsonPath("$.data[0].failedCount").value(1));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("automatic mails are collapsed per template and day, and expand with grouped=false")
    void automaticMails_collapsed() throws Exception {
        for (int i = 1; i <= 3; i++) {
            UUID id = message(new Mail("BATbern915-1" + i, "REGISTRANTS", "registration-confirmation", "TEMPLATE", null,
                    null, "Confirmation " + i, now.minus(10 - i, ChronoUnit.MINUTES), EVENT_CODE));
            recipient(id, "success+r" + i + "@simulator.amazonses.com", i == 2 ? "FAILED" : "DELIVERED",
                    i == 2 ? "Permanent/General" : null);
        }

        mockMvc.perform(get(BASE))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].mailKey").value("BATbern915-13"))
                .andExpect(jsonPath("$.data[0].messageCount").value(3))
                .andExpect(jsonPath("$.data[0].recipientCount").value(3))
                .andExpect(jsonPath("$.data[0].failedCount").value(1));

        mockMvc.perform(get(BASE).param("grouped", "false").param("templateKey", "registration-confirmation"))
                .andExpect(jsonPath("$.data[*].mailKey", contains("BATbern915-13", "BATbern915-12", "BATbern915-11")));

        mockMvc.perform(get(BASE).param("audience", "SPEAKERS"))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("AC4/AC5: detail returns the body as recorded and every recipient with its reason")
    void detail_bodyAndRecipients() throws Exception {
        UUID id = message(new Mail("BATbern915-4", "SPEAKERS", "speaker-invitation", "TEMPLATE", "organizer1", null,
                "Invitation", now, EVENT_CODE));
        recipient(id, A, "DELIVERED", null);
        recipient(id, B, "FAILED", "Permanent/NoEmail");

        mockMvc.perform(get(BASE + "/BATbern915-4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.mailKey").value("BATbern915-4"))
                .andExpect(jsonPath("$.bodyHtml").value("<p>body BATbern915-4</p>"))
                .andExpect(jsonPath("$.recipients", hasSize(2)))
                .andExpect(jsonPath("$.recipients[?(@.address=='" + B + "')].failureReason")
                        .value("Permanent/NoEmail"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("AC3: counts give the failed deliveries of the event, newsletter included")
    void counts_failedDeliveries() throws Exception {
        UUID id = message(new Mail("BATbern915-5", "SPEAKERS", "speaker-invitation", "TEMPLATE", "organizer1", null,
                "Invitation", now, EVENT_CODE));
        recipient(id, B, "FAILED", "Permanent/NoEmail");
        UUID sendId = newsletterSend("newsletter-event", now.minus(2, ChronoUnit.DAYS), 1);
        newsletterRecipient(sendId, A, "failed", null);

        mockMvc.perform(get(BASE + "/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedDeliveries").value(2));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("unknown event or mail is 404; a mail of another event is not visible")
    void notFound() throws Exception {
        message(new Mail("BATbern999-1", "SPEAKERS", "speaker-invitation", "TEMPLATE", "organizer1", null, "Other", now,
                "BATbern999"));

        mockMvc.perform(get("/api/v1/events/BATbern404/mail-messages")).andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/BATbern915-99")).andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/BATbern999-1")).andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/newsletter-" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "SPEAKER")
    @DisplayName("AC5: non-organizers get 403 on all three operations")
    void nonOrganizer_forbidden() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/counts")).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/BATbern915-1")).andExpect(status().isForbidden());
    }

    // --- fixtures (direct SQL, inside the test transaction) ---

    /** One mail_message fixture row. */
    private record Mail(String key, String audience, String templateKey, String source, String sender,
            UUID newsletterSendId, String subject, Instant createdAt, String eventCode) {
    }

    private UUID message(Mail mail) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO mail_message (id, mail_key, send_group_id, event_code, audience, template_key,"
                        + " content_source, subject, body_html, sender_username, newsletter_send_id, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, mail.key(), "grp-" + mail.key(), mail.eventCode(), mail.audience(), mail.templateKey(),
                mail.source(), mail.subject(), "<p>body " + mail.key() + "</p>", mail.sender(),
                mail.newsletterSendId(), Timestamp.from(mail.createdAt()));
        return id;
    }

    private void recipient(UUID messageId, String address, String status, String reason) {
        jdbc.update("INSERT INTO mail_recipient (mail_message_id, address, kind, ses_message_id, status,"
                + " failure_reason) VALUES (?, ?, 'TO', ?, ?, ?)", messageId, address, "ses-" + messageId, status,
                reason);
        jdbc.update("UPDATE mail_message SET recipient_count = recipient_count + 1 WHERE id = ?", messageId);
    }

    private UUID newsletterSend(String templateKey, Instant sentAt, int recipientCount) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO newsletter_sends (id, event_id, template_key, is_reminder, locale, sent_at,"
                        + " sent_by_username, recipient_count, status, sent_count, failed_count, test_mode, created_at)"
                        + " VALUES (?, ?, ?, false, 'de', ?, 'organizer1', ?, 'COMPLETED', ?, 0, false, ?)",
                id, event.getId(), templateKey, Timestamp.from(sentAt), recipientCount, recipientCount,
                Timestamp.from(sentAt));
        return id;
    }

    private void newsletterRecipient(UUID sendId, String email, String deliveryStatus, String bounceType) {
        jdbc.update("INSERT INTO newsletter_recipients (send_id, email, delivery_status, bounce_type)"
                + " VALUES (?, ?, ?, ?)", sendId, email, deliveryStatus, bounceType);
    }
}
