package ch.batbern.events.mailcenter;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Event-management implements the Mail Center request contract CUMS owns (Epic 17 entry 7, #1237,
 * #1246), against real PostgreSQL: CUMS opens the conversation of a merge or logo request when it
 * is filed and tells the decision after the commit. The wire format is checked against
 * {@code cums-internal.openapi.yml} in {@code S2sProviderContractIntegrationTest}. Not
 * {@code @Transactional}: the inbound store commits in its own transaction.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Mail Center request contract, implemented by event-management (Epic 17 entry 7)")
class InternalRequestConversationIntegrationTest extends AbstractIntegrationTest {

    private static final UUID MERGE = UUID.fromString("7a1c0000-0000-4000-8000-000000001237");
    private static final UUID LOGO = UUID.fromString("7a1c0000-0000-4000-8000-000000001246");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM mail_conversation WHERE merge_request_id = ? OR logo_request_id = ?", MERGE, LOGO);
        jdbc.update("DELETE FROM mail_message WHERE send_group_id IN (?, ?)",
                RequestConversations.Kind.MERGE.groupId(MERGE), RequestConversations.Kind.LOGO.groupId(LOGO));
    }

    private ResultActions open(String kind, UUID id, String requesterEmail, String note) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("kind", kind);
        body.put("requestId", id);
        body.put("requesterName", "Anna Muster");
        body.put("requesterEmail", requesterEmail);
        body.put("subject", "MERGE".equals(kind) ? "Profil zusammenführen: Anna Muster"
                : "Neues Logo für ACME AG: Anna Muster");
        body.put("note", note);
        return mockMvc.perform(post("/internal/mail-center-requests")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private void openMerge() throws Exception {
        open("MERGE", MERGE, "Anna.Private@Example.com", "Bis 2024 bei Firma X").andExpect(status().isNoContent());
    }

    private ResultActions decide(String kind, UUID id, Instant decidedAt) throws Exception {
        return mockMvc.perform(post("/internal/mail-center-requests/{id}/decision", id)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind": "%s", "status": "APPROVED", "decidedBy": "orga.nizer", "decidedAt": "%s"}"""
                        .formatted(kind, decidedAt)));
    }

    private Map<String, Object> conversation(String column, UUID id) {
        return jdbc.queryForMap("SELECT conversation_key, status, audience, event_code, counterpart_address, subject,"
                + " updated_by FROM mail_conversation WHERE " + column + " = ?", id);
    }

    private int count(String column, UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM mail_conversation WHERE " + column + " = ?", Integer.class,
                id);
    }

    // ------------------------------------------------------------------ opened

    @Test
    @DisplayName("opening a merge request creates one OPEN conversation without event, with the requester and note")
    void open_createsConversation() throws Exception {
        openMerge();

        Map<String, Object> row = conversation("merge_request_id", MERGE);
        assertThat(row.get("status")).isEqualTo("OPEN");
        assertThat(row.get("audience")).isEqualTo("SYSTEM");
        assertThat(row.get("event_code")).isNull();
        assertThat(row.get("counterpart_address")).isEqualTo("anna.private@example.com");
        assertThat(row.get("subject")).isEqualTo("Profil zusammenführen: Anna Muster");
        assertThat(jdbc.queryForObject("SELECT body_text FROM mail_message m JOIN mail_conversation c"
                + " ON m.conversation_id = c.id WHERE c.merge_request_id = ?", String.class, MERGE))
                .contains("Anna Muster").contains("Bis 2024 bei Firma X");
    }

    @Test
    @WithMockUser(username = "orga.nizer", roles = "ORGANIZER")
    @DisplayName("a logo request's conversation carries its request id in the Mail Center")
    void open_logo_carriesRequestId() throws Exception {
        open("LOGO", LOGO, "anna@example.com", null).andExpect(status().isNoContent());
        String key = (String) conversation("logo_request_id", LOGO).get("conversation_key");

        mockMvc.perform(get("/api/v1/conversations/{key}", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.logoRequestId").value(LOGO.toString()))
                .andExpect(jsonPath("$.conversation.mergeRequestId").doesNotExist());
    }

    @Test
    @DisplayName("opening twice keeps one conversation and one opening mail")
    void open_isIdempotent() throws Exception {
        openMerge();
        openMerge();

        assertThat(count("merge_request_id", MERGE)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE send_group_id = ?", Integer.class,
                RequestConversations.Kind.MERGE.groupId(MERGE))).isEqualTo(1);
    }

    @Test
    @DisplayName("4.9b: the requester address follows the #1102 rule, an IDN domain is accepted")
    void open_acceptsIdnDomain() throws Exception {
        open("MERGE", MERGE, "juerg.brunner@bücherstube.example", null).andExpect(status().isNoContent());

        assertThat(conversation("merge_request_id", MERGE))
                .containsEntry("counterpart_address", "juerg.brunner@bücherstube.example");
    }

    @Test
    @DisplayName("4.9b: a Unicode local part answers 400 and opens nothing")
    void open_rejectsUnicodeLocalPart() throws Exception {
        open("MERGE", MERGE, "jürg@bücherstube.example", null).andExpect(status().isBadRequest());

        assertThat(count("merge_request_id", MERGE)).isZero();
    }

    @Test
    @DisplayName("a conversation stored but never linked to its request (crash between the steps) is linked")
    void unlinkedConversation_isLinkedOnTheNextOpen() throws Exception {
        openMerge();
        String key = (String) conversation("merge_request_id", MERGE).get("conversation_key");
        jdbc.update("UPDATE mail_conversation SET merge_request_id = NULL WHERE conversation_key = ?", key);

        openMerge();

        assertThat(conversation("merge_request_id", MERGE)).containsEntry("conversation_key", key);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_message WHERE send_group_id = ?", Integer.class,
                RequestConversations.Kind.MERGE.groupId(MERGE))).isEqualTo(1);
    }

    // ------------------------------------------------------------------ decided

    @Test
    @DisplayName("a decision closes the conversation and records who decided; told twice, nothing changes")
    void decision_closes_idempotently() throws Exception {
        openMerge();
        Instant decidedAt = Instant.now();

        decide("MERGE", MERGE, decidedAt).andExpect(status().isNoContent());

        assertThat(conversation("merge_request_id", MERGE)).containsEntry("status", "DONE")
                .containsEntry("updated_by", "orga.nizer");
        Timestamp closedAt = jdbc.queryForObject("SELECT updated_at FROM mail_conversation WHERE merge_request_id = ?",
                Timestamp.class, MERGE);

        decide("MERGE", MERGE, decidedAt).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT updated_at FROM mail_conversation WHERE merge_request_id = ?",
                Timestamp.class, MERGE)).isEqualTo(closedAt);
    }

    @ParameterizedTest(name = "requester replied after the decision: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("a decision does not close a conversation over a reply that came in after it")
    void decision_keepsALaterReplyOpen(boolean replied) throws Exception {
        openMerge();
        Instant decidedAt = Instant.now().plusSeconds(1);
        if (replied) {
            jdbc.update("UPDATE mail_conversation SET last_inbound_at = ? WHERE merge_request_id = ?",
                    Timestamp.from(decidedAt.plusSeconds(5)), MERGE);
        }

        decide("MERGE", MERGE, decidedAt).andExpect(status().isNoContent());

        assertThat(conversation("merge_request_id", MERGE)).containsEntry("status", replied ? "OPEN" : "DONE");
    }

    @Test
    @DisplayName("sweep: a WITHDRAWN filing (CUMS rolled back after the open) closes the conversation as the system")
    void withdrawn_closesConversation() throws Exception {
        openMerge();

        mockMvc.perform(post("/internal/mail-center-requests/{id}/decision", MERGE)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"kind": "MERGE", "status": "WITHDRAWN", "decidedBy": "system:mail-center-request",
                                 "decidedAt": "%s"}""".formatted(Instant.now())))
                .andExpect(status().isNoContent());

        assertThat(conversation("merge_request_id", MERGE)).containsEntry("status", "DONE")
                .containsEntry("updated_by", "system:mail-center-request");
    }

    @Test
    @DisplayName("a decision for a request without a conversation changes nothing")
    void decision_withoutConversation_isIgnored() throws Exception {
        decide("LOGO", LOGO, Instant.now()).andExpect(status().isNoContent());

        assertThat(count("logo_request_id", LOGO)).isZero();
    }

    @Test
    @DisplayName("a decision without decider or time answers 400")
    void decision_invalid_isBadRequest() throws Exception {
        mockMvc.perform(post("/internal/mail-center-requests/{id}/decision", MERGE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"MERGE\", \"status\": \"APPROVED\"}"))
                .andExpect(status().isBadRequest());
    }
}
