package ch.batbern.events.mailcenter;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.mail.SesRecorder;
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
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 16.6 AC1–AC3, AC6 against real PostgreSQL: an organizer answers a conversation. The real
 * {@link EmailService} sends to the {@link SesRecorder}, so nothing leaves; the tests read the MIME
 * message SES received.
 *
 * <p>Not {@code @Transactional}: the recorder writes in its own transaction, so this class removes
 * what it wrote.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Story 16.6: answer a conversation")
class ConversationReplyIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT = "BATbern918";
    private static final String SPEAKER = "success+speaker@simulator.amazonses.com";
    private static final String SPEAKER_REPLY_TO = "success+speaker.office@simulator.amazonses.com";
    private static final String TOKEN = "abcdefghijklmnop";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EventRepository eventRepository;
    /** CUMS behind the real UserApiClient (Story 17.0b2). */
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private SesRecorder sesRecorder;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void setUp() {
        eventRepository.saveAndFlush(Event.builder()
                .eventCode(EVENT).title("BATbern Test Event #918").eventNumber(918)
                .date(now.plus(30, ChronoUnit.DAYS)).registrationDeadline(now.plus(29, ChronoUnit.DAYS))
                .venueName("Test Venue").venueAddress("Bern").venueCapacity(200)
                .organizerUsername("anna.muster").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());
        cums.reset();
        cums.user("anna.muster", "Anna", "Muster", "anna.muster@example.com");
        sesRecorder.reset();
    }

    @AfterEach
    void tearDown() {
        cums.reset();
        jdbc.update("DELETE FROM mail_recipient WHERE mail_message_id IN "
                + "(SELECT id FROM mail_message WHERE event_code = ?)", EVENT);
        jdbc.update("DELETE FROM mail_message WHERE event_code = ?", EVENT);
        jdbc.update("DELETE FROM mail_conversation WHERE event_code = ?", EVENT);
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT);
    }

    @Test
    @WithMockUser(username = "anna.muster", roles = "ORGANIZER")
    @DisplayName("AC1/AC2/AC3: one mail with our headers, recorded in the conversation, status WAITING")
    void should_sendAndRecord_when_organizerAnswers() throws Exception {
        UUID id = conversation("conv-9101", "Slides", SPEAKER, "OPEN");
        inbound(id, "conv-9101-in", "Re: Slides", new Headers(SPEAKER, SPEAKER_REPLY_TO, "in-2@mail.batbern-test.ch",
                "root@mail.batbern-test.ch in-1@mail.batbern-test.ch"), "Wann soll ich die Folien schicken?");

        mockMvc.perform(post("/api/v1/conversations/conv-9101/replies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Bis Freitag, danke.\\nAnna\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message.direction").value("OUT"))
                .andExpect(jsonPath("$.message.senderUsername").value("anna.muster"))
                .andExpect(jsonPath("$.message.conversationKey").value("conv-9101"));

        SesRecorder.RecordedMail sent = sesRecorder.single();
        assertThat(sent.raw()).isTrue();
        assertThat(sent.to()).containsExactly(SPEAKER_REPLY_TO);
        assertThat(sent.fromName()).isEqualTo("Anna Muster via BATbern");
        // Owner 2026-11-03: answers come from info@, not noreply@; Reply-To keeps the thread
        assertThat(sent.from()).isEqualTo("info@batbern.ch");
        assertThat(sent.replyTo()).containsExactly("replies+" + TOKEN + "@batbern.ch");
        assertThat(sent.subject()).isEqualTo("Re: Slides");
        assertThat(sent.headers()).containsEntry("In-Reply-To", "<in-2@mail.batbern-test.ch>");
        assertThat(sent.headers().get("References").split(" ")).containsExactly("<root@mail.batbern-test.ch>",
                "<in-1@mail.batbern-test.ch>", "<in-2@mail.batbern-test.ch>");
        String messageId = sent.messageIdHeader();
        assertThat(messageId).matches("<[0-9a-f-]{36}@batbern\\.ch>");
        assertThat(sent.text().replace("\r\n", "\n")).startsWith("Bis Freitag, danke.\nAnna")
                .contains("> Wann soll ich die Folien schicken?");
        assertThat(sent.html()).contains("Bis Freitag, danke.<br>Anna").contains("<blockquote");

        Map<String, Object> out = jdbc.queryForMap("SELECT conversation_id, message_id_header, content_source, "
                + "sender_username, audience FROM mail_message WHERE event_code = ? AND direction = 'OUT'", EVENT);
        assertThat(out.get("conversation_id")).isEqualTo(id);
        assertThat(out.get("sender_username")).isEqualTo("anna.muster");
        assertThat(out.get("audience")).isEqualTo("SPEAKERS");
        String bare = messageId.substring(1, messageId.length() - 1);
        assertThat(out.get("message_id_header")).isEqualTo(bare);
        assertThat(out.get("content_source")).isEqualTo("OWN_TEXT");
        assertThat(jdbc.queryForObject("SELECT r.message_id_header FROM mail_recipient r JOIN mail_message m "
                + "ON m.id = r.mail_message_id WHERE m.event_code = ? AND m.direction = 'OUT'", String.class, EVENT))
                .isEqualTo(bare);
        assertThat(jdbc.queryForObject("SELECT status FROM mail_conversation WHERE id = ?", String.class, id))
                .isEqualTo("WAITING");

        mockMvc.perform(get("/api/v1/conversations/conv-9101"))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].message.direction").value("OUT"));
        mockMvc.perform(get("/api/v1/events/" + EVENT + "/mails"))
                .andExpect(jsonPath("$.pagination.totalItems").value(0));
    }

    @Test
    @WithMockUser(username = "anna.muster", roles = "ORGANIZER")
    @DisplayName("AC1/AC3: no second Re: prefix; markDone sets DONE; From is used without Reply-To")
    void should_markDone_when_requested() throws Exception {
        UUID id = conversation("conv-9102", "AW: Slides", SPEAKER, "OPEN");
        inbound(id, "conv-9102-in", "AW: Slides", new Headers(SPEAKER, null, null, null), "Danke");

        mockMvc.perform(post("/api/v1/conversations/conv-9102/replies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Gern.\",\"markDone\":true}"))
                .andExpect(status().isCreated());

        SesRecorder.RecordedMail sent = sesRecorder.single();
        assertThat(sent.subject()).isEqualTo("AW: Slides");
        assertThat(sent.to()).containsExactly(SPEAKER);
        assertThat(sent.headers()).doesNotContainKey("In-Reply-To");
        assertThat(jdbc.queryForObject("SELECT status FROM mail_conversation WHERE id = ?", String.class, id))
                .isEqualTo("DONE");
    }

    @Test
    @WithMockUser(username = "anna.muster", roles = "ORGANIZER")
    @DisplayName("AC6: empty text 400, unknown conversation 404, reserved-domain counterpart 409; nothing sent")
    void should_reject_when_invalid() throws Exception {
        UUID id = conversation("conv-9103", "Frage", "someone@example.com", "OPEN");
        inbound(id, "conv-9103-in", "Frage", new Headers("someone@example.com", null, null, null), "Hallo");

        mockMvc.perform(post("/api/v1/conversations/conv-9103/replies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/conversations/conv-9103/replies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"   \"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/conversations/conv-0/replies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"x\"}")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/conversations/conv-9103/replies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"x\"}")).andExpect(status().isConflict());

        assertThat(sesRecorder.mails()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM mail_conversation WHERE id = ?", String.class, id))
                .isEqualTo("OPEN");
    }

    @Test
    @WithMockUser(roles = "SPEAKER")
    @DisplayName("AC6: anyone but an organizer gets 403")
    void should_forbid_when_notOrganizer() throws Exception {
        conversation("conv-9104", "Frage", SPEAKER, "OPEN");
        mockMvc.perform(post("/api/v1/conversations/conv-9104/replies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"x\"}")).andExpect(status().isForbidden());
        assertThat(sesRecorder.mails()).isEmpty();
        assertThat(cums.requestedUsers()).isEmpty();
    }

    private UUID conversation(String key, String subject, String counterpart, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO mail_conversation (id, conversation_key, event_code, audience, subject, "
                + "counterpart_address, counterpart_name, status, reply_token, last_message_at) "
                + "VALUES (?, ?, ?, 'SPEAKERS', ?, ?, 'A Speaker', ?, ?, ?)",
                id, key, EVENT, subject, counterpart, status,
                key.equals("conv-9101") ? TOKEN : UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                Timestamp.from(now));
        return id;
    }

    /** The headers of a stored inbound mail. */
    private record Headers(String from, String replyTo, String messageId, String references) {
    }

    private void inbound(UUID conversation, String key, String subject, Headers h, String text) {
        jdbc.update("INSERT INTO mail_message (mail_key, send_group_id, event_code, direction, audience, "
                + "content_source, subject, body_html, body_text, conversation_id, from_address, from_name, "
                + "reply_to_address, message_id_header, references_header, inbound_path, created_at) "
                + "VALUES (?, ?, ?, 'IN', 'SPEAKERS', 'OWN_TEXT', ?, ?, ?, ?, ?, 'A Speaker', ?, ?, ?, 'ALIAS', ?)",
                key, "in_" + key, EVENT, subject, "<p>" + text + "</p>", text, conversation, h.from(), h.replyTo(),
                h.messageId(), h.references(), Timestamp.from(now.minus(1, ChronoUnit.HOURS)));
    }
}
