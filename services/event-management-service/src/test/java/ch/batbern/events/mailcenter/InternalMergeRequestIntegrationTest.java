package ch.batbern.events.mailcenter;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #1237 against real PostgreSQL: company-user-management opens the Mail Center conversation of a
 * profile merge request and closes it once decided. Not {@code @Transactional}: the inbound store
 * commits in its own transaction.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Internal merge-request conversations (#1237)")
class InternalMergeRequestIntegrationTest extends AbstractIntegrationTest {

    private static final UUID REQUEST = UUID.fromString("7a1c0000-0000-4000-8000-000000001237");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM mail_conversation WHERE merge_request_id = ?", REQUEST);
        jdbc.update("DELETE FROM mail_message WHERE send_group_id = ?", RequestConversations.Kind.MERGE.groupId(REQUEST));
    }

    private String open() throws Exception {
        String response = openRequest("Anna.Private@Example.com")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("conversationKey").asText();
    }

    private ResultActions openRequest(String requesterEmail) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("requestId", REQUEST);
        body.put("requesterName", "Anna Muster");
        body.put("requesterEmail", requesterEmail);
        body.put("subject", "Profil zusammenführen: Anna Muster");
        body.put("note", "Bis 2024 bei Firma X");
        return mockMvc.perform(post("/internal/mail-center/merge-requests")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    @Test
    @DisplayName("4.9b: the requester address follows the #1102 rule, an IDN domain is accepted")
    void open_acceptsIdnDomain() throws Exception {
        openRequest("juerg.brunner@bücherstube.example").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT counterpart_address FROM mail_conversation WHERE merge_request_id = ?",
                String.class, REQUEST)).isEqualTo("juerg.brunner@bücherstube.example");
    }

    @Test
    @DisplayName("4.9b: a Unicode local part answers 400 and opens nothing")
    void open_rejectsUnicodeLocalPart() throws Exception {
        openRequest("jürg@bücherstube.example").andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_conversation WHERE merge_request_id = ?",
                Integer.class, REQUEST)).isZero();
    }

    @Test
    @DisplayName("opening creates one OPEN conversation without event, with the requester and their note")
    void open_createsConversation() throws Exception {
        String key = open();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, audience, event_code, counterpart_address, merge_request_id"
                        + " FROM mail_conversation WHERE conversation_key = ?", key);
        assertThat(row.get("status")).isEqualTo("OPEN");
        assertThat(row.get("audience")).isEqualTo("SYSTEM");
        assertThat(row.get("event_code")).isNull();
        assertThat(row.get("counterpart_address")).isEqualTo("anna.private@example.com");
        assertThat(row.get("merge_request_id")).isEqualTo(REQUEST);
        assertThat(jdbc.queryForObject("SELECT body_text FROM mail_message m JOIN mail_conversation c"
                + " ON m.conversation_id = c.id WHERE c.conversation_key = ?", String.class, key))
                .contains("Bis 2024 bei Firma X");
    }

    @Test
    @DisplayName("opening twice keeps one conversation")
    void open_isIdempotent() throws Exception {
        String first = open();
        String second = open();

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_conversation WHERE merge_request_id = ?",
                Integer.class, REQUEST)).isEqualTo(1);
    }

    @Test
    @DisplayName("done closes the conversation and records who decided")
    void done_closes() throws Exception {
        String key = open();

        mockMvc.perform(post("/internal/mail-center/merge-requests/{id}/done", REQUEST)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decidedBy\":\"orga.nizer\"}"))
                .andExpect(status().isNoContent());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, updated_by FROM mail_conversation WHERE conversation_key = ?", key);
        assertThat(row.get("status")).isEqualTo("DONE");
        assertThat(row.get("updated_by")).isEqualTo("orga.nizer");
    }

    @Test
    @WithMockUser(username = "orga.nizer", roles = "ORGANIZER")
    @DisplayName("the Mail Center shows the request id on the conversation")
    void conversation_carriesMergeRequestId() throws Exception {
        String key = open();

        mockMvc.perform(get("/api/v1/conversations/{key}", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.mergeRequestId").value(REQUEST.toString()));
    }
}
