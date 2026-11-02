package ch.batbern.events.mailcenter;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.users.AdditionalEmail;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.forwarding.EmailForwardingService;
import ch.batbern.events.forwarding.ForwardingDirectory;
import ch.batbern.events.mailcenter.inbound.InboundMailCenter;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.scheduler.MailMessageRetentionScheduler;
import ch.batbern.shared.test.AbstractIntegrationTest;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 16.7 (global Mail page: spam, virus, "Not spam", event assignment, lists across events) and
 * Story 16.10 (mail of one person or partner) against real PostgreSQL. Synthetic addresses only;
 * nothing is sent.
 *
 * <p>Not {@code @Transactional}: the inbound store and the counter commit in their own transaction, so
 * this class removes what it wrote.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Stories 16.7, 16.10: Mail across events")
class GlobalMailCenterIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_A = "BATbern931";
    private static final String EVENT_B = "BATbern932";
    private static final String SPEAKER = "success+speaker@simulator.amazonses.com";
    private static final String STRANGER = "success+stranger@simulator.amazonses.com";
    private static final String ANNA = "success+anna@simulator.amazonses.com";
    private static final String ANNA_EXTRA = "success+anna.extra@simulator.amazonses.com";
    private static final String BOB = "success+bob@simulator.amazonses.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InboundMailCenter mailCenter;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private MailMessageRetentionScheduler retention;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private ForwardingDirectory directory;
    @MockitoBean
    private UserApiClient userApiClient;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void setUp() {
        event(EVENT_A, 931);
        event(EVENT_B, 932);
        when(directory.organizerEmails()).thenReturn(Set.of("organizer-mail@example.com"));
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("lead.organizer"));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM mail_message WHERE event_code IN (?, ?) OR event_code IS NULL OR direction = 'IN'",
                EVENT_A, EVENT_B);
        jdbc.update("DELETE FROM mail_conversation");
        jdbc.update("DELETE FROM mail_automatic_event");
        jdbc.update("DELETE FROM events WHERE event_code IN (?, ?)", EVENT_A, EVENT_B);
    }

    // ── AC3 / AC4: spam and virus on arrival ─────────────────────────────────────────────────

    @Test
    @DisplayName("AC3: a spam-FAIL mail to info@ is stored flagged, opens no conversation and is counted")
    void should_storeSpamOnly_when_spamVerdictFails() throws Exception {
        alias(mail(STRANGER, "info@batbern.ch", "You won", "spam1@example.com", "X-SES-Spam-Verdict: FAIL\r\n"));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM mail_message WHERE direction = 'IN'");
        assertThat(row).containsEntry("is_spam", true).containsEntry("from_address", STRANGER)
                .containsEntry("alias_local_part", "info");
        assertThat(row.get("conversation_id")).isNull();
        assertThat(row.get("event_code")).isNull();
        assertThat((String) row.get("mail_key")).startsWith("general-");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_conversation", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_automatic_event WHERE kind = 'SPAM'",
                Integer.class)).isEqualTo(1);

        organizer(get("/api/v1/conversations/counts"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.spam").value(1))
                .andExpect(jsonPath("$.open").value(0));
        organizer(get("/api/v1/mails").param("direction", "IN"))
                .andExpect(jsonPath("$.pagination.totalItems").value(0));
        organizer(get("/api/v1/mails").param("direction", "IN").param("spam", "true"))
                .andExpect(jsonPath("$.data[0].mailKey").value(row.get("mail_key")))
                .andExpect(jsonPath("$.data[0].spam").value(true))
                .andExpect(jsonPath("$.data[0].direction").value("IN"))
                .andExpect(jsonPath("$.data[0].fromAddress").value(STRANGER));
        organizer(get("/api/v1/mails/" + row.get("mail_key")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bodyHtml", containsString("Hallo")));
    }

    @Test
    @DisplayName("AC4: a virus-FAIL mail is neither stored nor opened, only counted")
    void should_countOnly_when_virusVerdictFails() {
        alias(mail(STRANGER, "info@batbern.ch", "Invoice", "virus1@example.com",
                "X-SES-Spam-Verdict: FAIL\r\nX-SES-Virus-Verdict: FAIL\r\n"));
        reply(mail(STRANGER, "replies@batbern.ch", "Re: Invoice", "virus2@example.com",
                "X-SES-Virus-Verdict: FAIL\r\n"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE direction = 'IN'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_automatic_event WHERE kind = 'VIRUS'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("Regression: a mail without event is stored without an ingest failure (strict path returns it)")
    void should_returnStored_when_noEventMailIngestedStrictly() {
        Optional<InboundMailCenter.Stored> stored = mailCenter.ingestAliasStrict("forwarding/" + UUID.randomUUID(),
                mail(STRANGER, "info@batbern.ch", "Allgemeine Frage", "strict1@example.com", null));

        assertThat(stored).isPresent();
        assertThat(stored.get().eventCode()).isNull();
        assertThat(stored.get().conversationKey()).startsWith("conv-");
    }

    // ── AC3: "Not spam" ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC3: not spam on a mail to the moderator alias puts it into the event's conversations")
    void should_threadIntoEvent_when_notSpamOnModeratorMail() throws Exception {
        alias(mail(SPEAKER, "batbern931-moderator@batbern.ch", "Frage", "s2@example.com",
                "X-SES-Spam-Verdict: FAIL\r\n"));
        String key = jdbc.queryForObject("SELECT mail_key FROM mail_message WHERE is_spam", String.class);

        organizer(patch("/api/v1/mails/" + key).contentType(MediaType.APPLICATION_JSON).content("{\"spam\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.spam").value(false))
                .andExpect(jsonPath("$.message.eventCode").value(EVENT_A));

        Map<String, Object> conv = jdbc.queryForMap("SELECT * FROM mail_conversation");
        assertThat(conv).containsEntry("event_code", EVENT_A).containsEntry("status", "OPEN")
                .containsEntry("audience", "SPEAKERS").containsEntry("assignee_username", "lead.organizer");
        assertThat(jdbc.queryForMap("SELECT * FROM mail_message WHERE mail_key = ?", key))
                .containsEntry("is_spam", false).containsEntry("event_code", EVENT_A)
                .containsEntry("conversation_id", conv.get("id"));
        organizer(get("/api/v1/conversations/counts")).andExpect(jsonPath("$.spam").value(0))
                .andExpect(jsonPath("$.open").value(1));
    }

    @Test
    @DisplayName("AC3: not spam on an info@ mail without event lands in 'No event'; a second PATCH is 400")
    void should_landWithoutEvent_when_notSpamOnInfoMail() throws Exception {
        alias(mail(STRANGER, "info@batbern.ch", "Allgemeine Frage", "s3@example.com", "X-SES-Spam-Verdict: FAIL\r\n"));
        String key = jdbc.queryForObject("SELECT mail_key FROM mail_message WHERE is_spam", String.class);

        organizer(patch("/api/v1/mails/" + key).contentType(MediaType.APPLICATION_JSON).content("{\"spam\":false}"))
                .andExpect(status().isOk());
        organizer(get("/api/v1/conversations").param("noEvent", "true"))
                .andExpect(jsonPath("$.data[0].counterpartAddress").value(STRANGER))
                .andExpect(jsonPath("$.data[0].via").value("info@batbern.ch"));

        organizer(patch("/api/v1/mails/" + key).contentType(MediaType.APPLICATION_JSON).content("{\"spam\":false}"))
                .andExpect(status().isBadRequest());
        organizer(patch("/api/v1/mails/" + key).contentType(MediaType.APPLICATION_JSON).content("{\"spam\":true}"))
                .andExpect(status().isBadRequest());
        organizer(patch("/api/v1/mails/general-0").contentType(MediaType.APPLICATION_JSON).content("{\"spam\":false}"))
                .andExpect(status().isNotFound());
    }

    // ── AC1: assign to event ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC1: assigning a no-event conversation moves it and its messages; clearing reverses it")
    void should_moveConversation_when_assignedToEvent() throws Exception {
        alias(mail(STRANGER, "info@batbern.ch", "Anmeldung?", "a1@example.com", null));
        String conv = jdbc.queryForObject("SELECT conversation_key FROM mail_conversation", String.class);
        String mailKey = jdbc.queryForObject("SELECT mail_key FROM mail_message WHERE direction = 'IN'", String.class);

        organizer(patch("/api/v1/conversations/" + conv).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventCode\":\"" + EVENT_B + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(EVENT_B));
        assertThat(jdbc.queryForObject("SELECT event_code FROM mail_message WHERE mail_key = ?", String.class,
                mailKey)).isEqualTo(EVENT_B);
        assertThat(mailKey).startsWith("general-");
        organizer(get("/api/v1/events/" + EVENT_B + "/conversations"))
                .andExpect(jsonPath("$.data[*].conversationKey", contains(conv)));
        organizer(get("/api/v1/conversations").param("noEvent", "true"))
                .andExpect(jsonPath("$.pagination.totalItems").value(0));

        organizer(patch("/api/v1/conversations/" + conv).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clearEvent\":true}"))
                .andExpect(jsonPath("$.eventCode").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT event_code FROM mail_message WHERE mail_key = ?", String.class,
                mailKey)).isNull();
    }

    @Test
    @DisplayName("AC1: an unknown event is 404, eventCode with clearEvent is 400")
    void should_reject_when_assignmentInvalid() throws Exception {
        alias(mail(STRANGER, "info@batbern.ch", "Frage", "a2@example.com", null));
        String conv = jdbc.queryForObject("SELECT conversation_key FROM mail_conversation", String.class);

        organizer(patch("/api/v1/conversations/" + conv).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventCode\":\"BATbern999999\"}"))
                .andExpect(status().isNotFound());
        organizer(patch("/api/v1/conversations/" + conv).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventCode\":\"" + EVENT_A + "\",\"clearEvent\":true}"))
                .andExpect(status().isBadRequest());
    }

    // ── AC2 / AC8: conversations across events and counts ────────────────────────────────────

    @Test
    @DisplayName("AC2/AC8: counts and the open list span all events; noEvent=false leaves out no-event ones")
    void should_listAcrossEvents_when_conversationsInSeveralEvents() throws Exception {
        alias(mail(SPEAKER, "batbern931-moderator@batbern.ch", "Frage A", "c1@example.com", null));
        alias(mail("success+other@simulator.amazonses.com", "batbern932-moderator@batbern.ch", "Frage B",
                "c2@example.com", null));
        alias(mail(STRANGER, "info@batbern.ch", "Frage ohne Event", "c3@example.com", null));
        jdbc.update("UPDATE mail_conversation SET status = 'WAITING' WHERE subject = 'Frage B'");

        organizer(get("/api/v1/conversations/counts"))
                .andExpect(jsonPath("$.open").value(2))
                .andExpect(jsonPath("$.waiting").value(1))
                .andExpect(jsonPath("$.noEvent").value(1))
                .andExpect(jsonPath("$.spam").value(0));
        organizer(get("/api/v1/conversations").param("status", "OPEN").param("status", "WAITING")
                .param("noEvent", "false"))
                .andExpect(jsonPath("$.data[*].subject", containsInAnyOrder("Frage A", "Frage B")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        organizer(get("/api/v1/conversations").param("eventCode", EVENT_B))
                .andExpect(jsonPath("$.data[*].subject", contains("Frage B")));
    }

    // ── AC9 / AC10: GET /mails ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC9: GET /mails lists outbound mail of every event and without event; filters by event")
    void should_listAcrossEvents_when_mailsRequested() throws Exception {
        outbound(EVENT_A + "-1", EVENT_A, "REGISTRANTS", now.minus(3, ChronoUnit.HOURS), BOB);
        outbound(EVENT_B + "-1", EVENT_B, "SPEAKERS", now.minus(2, ChronoUnit.HOURS), BOB);
        outbound("general-1", null, "ORGANIZERS", now.minus(1, ChronoUnit.HOURS), BOB);
        alias(mail(STRANGER, "info@batbern.ch", "Frage", "m1@example.com", null)); // IN, not in the OUT list

        organizer(get("/api/v1/mails"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.data[*].mailKey", contains("general-1", EVENT_B + "-1", EVENT_A + "-1")))
                .andExpect(jsonPath("$.data[1].eventCode").value(EVENT_B))
                .andExpect(jsonPath("$.data[1].direction").value("OUT"));
        organizer(get("/api/v1/mails").param("eventCode", EVENT_A))
                .andExpect(jsonPath("$.data[*].mailKey", contains(EVENT_A + "-1")));
        organizer(get("/api/v1/mails").param("noEvent", "true"))
                .andExpect(jsonPath("$.data[*].mailKey", contains("general-1")));
        organizer(get("/api/v1/mails").param("direction", "ANY"))
                .andExpect(jsonPath("$.pagination.totalItems").value(4));
        organizer(get("/api/v1/mails/" + EVENT_B + "-1"))
                .andExpect(jsonPath("$.recipients[0].address").value(BOB));

        organizer(get("/api/v1/mails").param("eventCode", EVENT_A).param("noEvent", "true"))
                .andExpect(status().isBadRequest());
        organizer(get("/api/v1/mails").param("direction", "SIDEWAYS"))
                .andExpect(status().isBadRequest());
        organizer(get("/api/v1/mails").param("eventCode", "BATbern999999"))
                .andExpect(status().isNotFound());
        organizer(get("/api/v1/mails/nope-1")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC10: the event list keeps its contract: direction OUT by default, IN on request")
    void should_keepEventListOutbound_when_noDirectionGiven() throws Exception {
        outbound(EVENT_A + "-1", EVENT_A, "REGISTRANTS", now.minus(3, ChronoUnit.HOURS), BOB);
        alias(mail(SPEAKER, "batbern931-moderator@batbern.ch", "Frage", "e1@example.com", null));

        organizer(get("/api/v1/events/" + EVENT_A + "/mails"))
                .andExpect(jsonPath("$.data[*].mailKey", contains(EVENT_A + "-1")));
        organizer(get("/api/v1/events/" + EVENT_A + "/mails").param("direction", "IN"))
                .andExpect(jsonPath("$.data[0].direction").value("IN"))
                .andExpect(jsonPath("$.data[0].conversationKey").exists());
    }

    @Test
    @WithMockUser(roles = "SPEAKER")
    @DisplayName("AC10: every new operation is ORGANIZER only")
    void should_forbid_when_notOrganizer() throws Exception {
        mockMvc.perform(get("/api/v1/mails")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/mails/general-1")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/mails/general-1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"spam\":false}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/conversations")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/conversations/counts")).andExpect(status().isForbidden());
    }

    // ── AC7: spam retention ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC7: spam older than 30 days is deleted, younger spam and other mail stay")
    void should_deleteOldSpam_when_retentionRuns() {
        alias(mail(STRANGER, "info@batbern.ch", "Old spam", "r1@example.com", "X-SES-Spam-Verdict: FAIL\r\n"));
        alias(mail(STRANGER, "info@batbern.ch", "New spam", "r2@example.com", "X-SES-Spam-Verdict: FAIL\r\n"));
        alias(mail(STRANGER, "info@batbern.ch", "Old question", "r3@example.com", null));
        jdbc.update("UPDATE mail_message SET created_at = ? WHERE subject LIKE 'Old%'",
                Timestamp.from(now.minus(31, ChronoUnit.DAYS)));

        retention.purgeExpired();

        assertThat(jdbc.queryForList("SELECT subject FROM mail_message WHERE direction = 'IN'", String.class))
                .containsExactlyInAnyOrder("New spam", "Old question");
    }

    // ── Story 16.10: mail of one person or partner ────────────────────────────────────────────

    @Test
    @DisplayName("16.10 AC1/AC2: a user's mail by every address, sent and received; others, newsletters and tests not")
    void should_listParticipantMail_when_userHasSeveralAddresses() throws Exception {
        when(userApiClient.getUserByUsername("anna.example")).thenReturn(new UserResponse().id("anna.example")
                .email(ANNA).additionalEmails(List.of(new AdditionalEmail().email(ANNA_EXTRA.toUpperCase()))));
        UUID toPrimary = outbound(EVENT_A + "-1", EVENT_A, "REGISTRANTS", now.minus(5, ChronoUnit.HOURS), ANNA);
        jdbc.update("UPDATE mail_recipient SET status = 'DELIVERED' WHERE mail_message_id = ?", toPrimary);
        outbound(EVENT_B + "-1", EVENT_B, "SPEAKERS", now.minus(4, ChronoUnit.HOURS), ANNA_EXTRA);
        outbound(EVENT_B + "-2", EVENT_B, "SPEAKERS", now.minus(3, ChronoUnit.HOURS), BOB);
        UUID test = outbound(EVENT_B + "-3", EVENT_B, "SPEAKERS", now.minus(2, ChronoUnit.HOURS), ANNA);
        jdbc.update("UPDATE mail_message SET is_test = TRUE WHERE id = ?", test);
        alias(mail(ANNA_EXTRA, "info@batbern.ch", "Frage von Anna", "p1@example.com", null));

        organizer(get("/api/v1/mails").param("participantUsername", "anna.example").param("direction", "ANY")
                .param("grouped", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(3))
                .andExpect(jsonPath("$.data[0].direction").value("IN"))
                .andExpect(jsonPath("$.data[0].fromAddress").value(ANNA_EXTRA))
                .andExpect(jsonPath("$.data[1].mailKey").value(EVENT_B + "-1"))
                .andExpect(jsonPath("$.data[1].participantStatus").value("SENT"))
                .andExpect(jsonPath("$.data[2].mailKey").value(EVENT_A + "-1"))
                .andExpect(jsonPath("$.data[2].participantStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.data[*].mailKey", not(hasItem(EVENT_B + "-2"))));
    }

    @Test
    @DisplayName("16.10 AC3/AC4: a partner's mail by its PARTNER contacts; both filters 400; unknown 404")
    void should_listCompanyMail_when_partnerContactsExist() throws Exception {
        when(userApiClient.getUsersByCompany("ExampleAG", "PARTNER")).thenReturn(List.of(
                new UserResponse().id("bob.example").email(BOB)));
        when(userApiClient.getUsersByCompany("NoSuchCo", "PARTNER")).thenReturn(List.of());
        when(userApiClient.getCompanyDisplayName("NoSuchCo")).thenReturn(null);
        when(userApiClient.getUserByUsername("ghost")).thenThrow(new UserNotFoundException("ghost"));
        outbound(EVENT_A + "-1", EVENT_A, "PARTNERS", now.minus(5, ChronoUnit.HOURS), BOB);
        outbound(EVENT_A + "-2", EVENT_A, "PARTNERS", now.minus(4, ChronoUnit.HOURS), ANNA);

        organizer(get("/api/v1/mails").param("participantCompany", "ExampleAG").param("direction", "ANY"))
                .andExpect(jsonPath("$.data[*].mailKey", contains(EVENT_A + "-1")));
        organizer(get("/api/v1/mails").param("participantCompany", "ExampleAG")
                .param("participantUsername", "bob.example"))
                .andExpect(status().isBadRequest());
        organizer(get("/api/v1/mails").param("participantCompany", "NoSuchCo"))
                .andExpect(status().isNotFound());
        organizer(get("/api/v1/mails").param("participantUsername", "ghost"))
                .andExpect(status().isNotFound());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────

    private ResultActions organizer(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(user("lead.organizer").roles("ORGANIZER")));
    }

    private void event(String code, int number) {
        eventRepository.saveAndFlush(Event.builder()
                .eventCode(code).title("BATbern Test Event #" + number).eventNumber(number)
                .date(now.plus(30, ChronoUnit.DAYS)).registrationDeadline(now.plus(29, ChronoUnit.DAYS))
                .venueName("Test Venue").venueAddress("Bern").venueCapacity(200)
                .organizerUsername("lead.organizer").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());
    }

    private void alias(byte[] raw) {
        mailCenter.ingestAlias("forwarding/" + UUID.randomUUID(), raw, EmailForwardingService.Result.FORWARDED);
    }

    private void reply(byte[] raw) {
        mailCenter.ingestReply("emails/" + UUID.randomUUID(), raw, null);
    }

    private static byte[] mail(String from, String to, String subject, String messageId, String extraHeaders) {
        String raw = "From: \"A Person\" <" + from + ">\r\nTo: " + to + "\r\nSubject: " + subject + "\r\n"
                + "Message-ID: <" + messageId + ">\r\n" + (extraHeaders == null ? "" : extraHeaders)
                + "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nHallo zusammen\r\n";
        return raw.getBytes(StandardCharsets.UTF_8);
    }

    private UUID outbound(String key, String eventCode, String audience, Instant at, String recipient) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO mail_message (id, mail_key, send_group_id, event_code, audience, content_source, "
                + "subject, body_html, sender_username, recipient_count, created_at) "
                + "VALUES (?, ?, ?, ?, ?, 'OWN_TEXT', ?, '<p>x</p>', 'lead.organizer', 1, ?)",
                id, key, "grp-" + key, eventCode, audience, "Mail " + key, Timestamp.from(at));
        jdbc.update("INSERT INTO mail_recipient (mail_message_id, address, kind, ses_message_id, status) "
                + "VALUES (?, ?, 'TO', ?, 'SENT')", id, recipient, "ses-" + key);
        return id;
    }
}
