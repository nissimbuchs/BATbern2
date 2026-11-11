package ch.batbern.events.usermerge;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #1183 C/D against real PostgreSQL: company-user-management merges two profiles of one person and
 * asks event-management to move everything it holds under the source username to the target.
 *
 * <p>Not {@code @Transactional}: the merge commits in its own transaction, and the log row is read back.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Internal user merge (#1183 C/D)")
class InternalUserMergeIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE = "merge.source";
    private static final String TARGET = "merge.target";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID eventA;
    private UUID eventB;

    @BeforeEach
    void setUp() {
        cleanUp();
        eventA = insertEvent("BATbern9801", 9801);
        eventB = insertEvent("BATbern9802", 9802);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM user_merge_log WHERE source_username = ?", SOURCE);
        jdbc.update("DELETE FROM notifications WHERE recipient_username IN (?, ?)", SOURCE, TARGET);
        jdbc.update("DELETE FROM newsletter_subscribers WHERE username IN (?, ?)", SOURCE, TARGET);
        jdbc.update("DELETE FROM speaker_arrivals WHERE speaker_username IN (?, ?)", SOURCE, TARGET);
        jdbc.update("DELETE FROM events WHERE event_code IN ('BATbern9801', 'BATbern9802', 'BATbern9803')");
    }

    @Test
    @DisplayName("moves registrations, speaker slots, arrivals, notifications and newsletter rows; logs the keys")
    void merge_movesEverything_andLogsIt() throws Exception {
        UUID regA = insertRegistration(eventA, SOURCE, "attended");
        UUID regB = insertRegistration(eventB, SOURCE, "confirmed");
        UUID session = insertSession(eventA, "talk");
        insertSessionUser(session, SOURCE, "primary_speaker");
        jdbc.update("INSERT INTO speaker_arrivals (event_code, speaker_username, confirmed_by_username) "
                + "VALUES ('BATbern9801', ?, 'organizer1')", SOURCE);
        jdbc.update("INSERT INTO notifications (recipient_username, notification_type, channel, subject, body) "
                + "VALUES (?, 'INFO', 'EMAIL', 's', 'b')", SOURCE);
        jdbc.update("INSERT INTO newsletter_subscribers (email, username, unsubscribe_token) "
                + "VALUES ('merge.source@example.com', ?, ?)", SOURCE, UUID.randomUUID().toString());

        JsonNode result = merge();

        assertThat(count("registrations", "attendee_username", TARGET)).isEqualTo(2);
        assertThat(count("registrations", "attendee_username", SOURCE)).isZero();
        assertThat(count("session_users", "username", TARGET)).isEqualTo(1);
        assertThat(count("speaker_arrivals", "speaker_username", TARGET)).isEqualTo(1);
        assertThat(count("notifications", "recipient_username", TARGET)).isEqualTo(1);
        assertThat(count("newsletter_subscribers", "username", TARGET)).isEqualTo(1);
        assertThat(result.path("moved").path("registrations.attendee_username").asInt()).isEqualTo(2);
        assertThat(result.path("removed").size()).isZero();

        Map<String, Object> log = jdbc.queryForMap(
                "SELECT target_username, performed_by, trigger, moved::text AS moved, "
                        + "source_profile::text AS profile FROM user_merge_log WHERE source_username = ?", SOURCE);
        assertThat(log.get("target_username")).isEqualTo(TARGET);
        assertThat(log.get("performed_by")).isEqualTo("organizer1");
        assertThat(log.get("trigger")).isEqualTo("ORGANIZER");
        assertThat((String) log.get("moved")).contains(regA.toString()).contains(regB.toString());
        assertThat((String) log.get("profile")).contains("merge.source@example.com");
    }

    @Test
    @DisplayName("same event twice: the better registration stays, the other one is removed and logged in full")
    void merge_keepsTheBetterRegistration() throws Exception {
        UUID sourceConfirmed = insertRegistration(eventA, SOURCE, "confirmed");
        UUID targetCancelled = insertRegistration(eventA, TARGET, "cancelled");
        UUID sourceCancelled = insertRegistration(eventB, SOURCE, "cancelled");
        UUID targetWaitlist = insertRegistration(eventB, TARGET, "waitlist");

        JsonNode result = merge();

        assertThat(ids("registrations", "attendee_username", TARGET))
                .containsExactlyInAnyOrder(sourceConfirmed, targetWaitlist);
        assertThat(result.path("removed").path("registrations").asInt()).isEqualTo(2);
        String removed = jdbc.queryForObject(
                "SELECT removed_rows::text FROM user_merge_log WHERE source_username = ?", String.class, SOURCE);
        assertThat(removed).contains(targetCancelled.toString()).contains(sourceCancelled.toString())
                .contains("\"table\": \"registrations\"");
    }

    @Test
    @DisplayName("same session twice: the source slot is removed, the target slot stays")
    void merge_sameSession_keepsTargetSlot() throws Exception {
        UUID session = insertSession(eventA, "talk");
        insertSessionUser(session, SOURCE, "co_speaker");
        UUID targetSlot = insertSessionUser(session, TARGET, "primary_speaker");

        merge();

        assertThat(ids("session_users", "username", TARGET)).containsExactly(targetSlot);
        assertThat(count("session_users", "username", SOURCE)).isZero();
    }

    @Test
    @DisplayName("both lead a different talk at the same event: preview names the blocker, merge refuses with 409")
    void merge_twoTalksSameEvent_isBlocked() throws Exception {
        insertSessionUser(insertSession(eventA, "talk-one"), SOURCE, "primary_speaker");
        insertSessionUser(insertSession(eventA, "talk-two"), TARGET, "primary_speaker");

        String preview = mockMvc.perform(get("/internal/user-references/preview")
                        .param("source", SOURCE).param("target", TARGET))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(preview).path("blockers").toString()).contains("BATbern9801");

        mockMvc.perform(post("/internal/user-references/merge").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isConflict());
        assertThat(count("session_users", "username", SOURCE)).isEqualTo(1);
    }

    @Test
    @DisplayName("preview counts rows and conflicts without changing anything")
    void preview_changesNothing() throws Exception {
        insertRegistration(eventA, SOURCE, "confirmed");
        insertRegistration(eventA, TARGET, "confirmed");
        insertRegistration(eventB, SOURCE, "attended");

        String body = mockMvc.perform(get("/internal/user-references/preview")
                        .param("source", SOURCE).param("target", TARGET))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode preview = objectMapper.readTree(body);
        assertThat(preview.path("moves").path("registrations.attendee_username").asInt()).isEqualTo(2);
        assertThat(preview.path("conflicts").path("registrations").asInt()).isEqualTo(1);
        assertThat(preview.path("blockers").size()).isZero();
        assertThat(count("registrations", "attendee_username", SOURCE)).isEqualTo(2);
    }

    @Test
    @DisplayName("a second run moves nothing and still succeeds (retry after a failed profile step)")
    void merge_isIdempotent() throws Exception {
        insertRegistration(eventA, SOURCE, "confirmed");
        merge();

        JsonNode again = merge();

        assertThat(again.path("moved").path("registrations.attendee_username").asInt()).isZero();
        assertThat(count("registrations", "attendee_username", TARGET)).isEqualTo(1);
    }

    @Test
    @DisplayName("source equal to target is refused with 400")
    void merge_sameUser_isRefused() throws Exception {
        Map<String, Object> body = request();
        body.put("targetUsername", SOURCE);
        mockMvc.perform(post("/internal/user-references/merge").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    private JsonNode merge() throws Exception {
        String body = mockMvc.perform(post("/internal/user-references/merge").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private Map<String, Object> request() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceUsername", SOURCE);
        body.put("targetUsername", TARGET);
        body.put("performedBy", "organizer1");
        body.put("trigger", "ORGANIZER");
        body.put("sourceProfile", Map.of("username", SOURCE, "email", "merge.source@example.com"));
        return body;
    }

    @Test
    @DisplayName("#1244: a new primary address moves the address of upcoming registrations made under the old one")
    void primaryEmailChanged_updatesUpcomingRegistrationsOnly() throws Exception {
        UUID upcoming = insertRegistration(eventA, SOURCE, "confirmed");
        UUID otherAddress = insertRegistration(eventB, SOURCE, "confirmed");
        jdbc.update("UPDATE registrations SET attendee_email = 'guest@example.com' WHERE id = ?", otherAddress);
        UUID pastEvent = UUID.randomUUID();
        jdbc.update("INSERT INTO events (id, event_code, event_number, title, description, event_date, "
                + "registration_deadline, venue_name, venue_address, venue_capacity, organizer_username, "
                + "event_type, workflow_state, created_at, updated_at) VALUES (?, 'BATbern9803', 9803, 'Past', "
                + "'x', NOW() - interval '30 days', NOW() - interval '40 days', 'Venue', 'Address', 100, "
                + "'organizer1', 'evening', 'created', NOW(), NOW())", pastEvent);
        UUID past = insertRegistration(pastEvent, SOURCE, "attended");

        mockMvc.perform(post("/internal/user-references/primary-email").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", SOURCE,
                                "oldEmail", "Merge.Source@example.com", "newEmail", "private@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));

        assertThat(emailOf(upcoming)).isEqualTo("private@example.com");
        assertThat(emailOf(otherAddress)).isEqualTo("guest@example.com");
        assertThat(emailOf(past)).isEqualTo(SOURCE + "@example.com");
    }

    private String emailOf(UUID registration) {
        return jdbc.queryForObject("SELECT attendee_email FROM registrations WHERE id = ?", String.class,
                registration);
    }

    private UUID insertEvent(String code, int number) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO events (id, event_code, event_number, title, description, event_date, "
                + "registration_deadline, venue_name, venue_address, venue_capacity, organizer_username, "
                + "event_type, workflow_state, created_at, updated_at) VALUES (?, ?, ?, 'Merge test', 'x', "
                + "NOW() + interval '30 days', NOW() + interval '20 days', 'Venue', 'Address', 100, "
                + "'organizer1', 'evening', 'created', NOW(), NOW())", id, code, number);
        return id;
    }

    private UUID insertSession(UUID eventId, String slug) {
        UUID id = UUID.randomUUID();
        String code = jdbc.queryForObject("SELECT event_code FROM events WHERE id = ?", String.class, eventId);
        jdbc.update("INSERT INTO sessions (id, event_id, event_code, session_slug, title, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, NOW(), NOW())", id, eventId, code, slug + "-" + id, slug);
        return id;
    }

    private UUID insertSessionUser(UUID sessionId, String username, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO session_users (id, session_id, username, speaker_role) VALUES (?, ?, ?, ?)",
                id, sessionId, username, role);
        return id;
    }

    private UUID insertRegistration(UUID eventId, String username, String state) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO registrations (id, registration_code, event_id, attendee_username, "
                + "attendee_email, status, registration_date, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), NOW())",
                id, "REG-" + id, eventId, username, username + "@example.com", state);
        return id;
    }

    private int count(String table, String column, String value) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, value);
        return n == null ? 0 : n;
    }

    private List<UUID> ids(String table, String column, String value) {
        return jdbc.queryForList("SELECT id FROM " + table + " WHERE " + column + " = ?", UUID.class, value);
    }
}
