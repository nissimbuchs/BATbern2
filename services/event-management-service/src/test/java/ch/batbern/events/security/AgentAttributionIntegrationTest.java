package ch.batbern.events.security;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.notification.Notification;
import ch.batbern.events.notification.NotificationRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.test.security.RoleStubTables;
import ch.batbern.shared.test.security.TestTokens;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agentic interaction 0.2: an agent token is recorded as {@code agent:<run> for <username>} in a
 * who-acted column, while ownership still reads the username, and a mail the agent sends records the
 * person as its sender. An agent cannot cancel an event (AD-15, {@code EventCancellationIntegrationTest}). Real agent tokens ({@link TestTokens})
 * go through the production chain and the real decoder.
 *
 * <p>The EMS test schema has a {@code user_profiles} stub (testmigration V100) but no
 * {@code role_assignments}: this test creates it ({@link RoleStubTables}), so
 * {@code JwtRolesConverter} resolves the agent's roles by its {@code sub} as production does.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("EMS: agent actions are recorded as agent:<run> for <username>")
class AgentAttributionIntegrationTest extends AbstractIntegrationTest {

    private static final String ORGANIZER = "carla.agentattr";
    private static final String ORGANIZER_SUB = "c0000000-0000-4000-8000-0000000000c1";
    private static final String OTHER_ORGANIZER = "ben.agentattr";
    private static final String RUN = "run-ems-1";
    private static final String SETTING = "agent-attribution.probe";
    private static final String EVENT = "BATbern9770";
    private static final String CONVERSATION = "conv-agentattr-9770";
    private static final String SPEAKER_MAIL = "success+agentattr-dora@simulator.amazonses.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private CumsContractHarness cums;

    @BeforeEach
    void setUp() {
        RoleStubTables.create(jdbc);
        cleanUp();
        sesRecorder.reset();
        RoleStubTables.grant(jdbc, ORGANIZER, ORGANIZER_SUB, "ORGANIZER");
    }

    @AfterEach
    void cleanUp() {
        sesRecorder.awaitAsyncSends();
        cums.reset();
        jdbc.update("DELETE FROM mail_recipient WHERE mail_message_id IN "
                + "(SELECT id FROM mail_message WHERE event_code = ?)", EVENT);
        jdbc.update("DELETE FROM mail_message WHERE event_code = ?", EVENT);
        jdbc.update("DELETE FROM mail_conversation WHERE event_code = ?", EVENT);
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT);
        jdbc.update("DELETE FROM app_settings WHERE setting_key = ?", SETTING);
        jdbc.update("DELETE FROM notifications WHERE recipient_username IN (?, ?)", ORGANIZER, OTHER_ORGANIZER);
        RoleStubTables.remove(jdbc, ORGANIZER_SUB);
    }

    private static String bearer() {
        return "Bearer " + TestTokens.agentToken(ORGANIZER_SUB, ORGANIZER, RUN, "organizer");
    }

    private String updatedByOfSetting() {
        return jdbc.queryForObject("SELECT updated_by FROM app_settings WHERE setting_key = ?", String.class, SETTING);
    }

    private UUID notificationFor(String recipient) {
        Instant now = Instant.now();
        return notificationRepository.saveAndFlush(Notification.builder()
                .recipientUsername(recipient)
                .notificationType("EventWorkflowTransitionEvent")
                .channel("IN_APP")
                .priority("NORMAL")
                .subject("BATbern57 moved")
                .body("carla.agentattr moved BATbern57")
                .status("UNREAD")
                .createdAt(now)
                .updatedAt(now)
                .build()).getId();
    }

    private String statusOf(UUID notification) {
        return jdbc.queryForObject("SELECT status FROM notifications WHERE id = ?", String.class, notification);
    }

    @Test
    @DisplayName("an organizer's agent changes an admin setting: updated_by = agent:<run> for <username>")
    void should_recordAgentActor_when_agentChangesAdminSetting() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings/" + SETTING)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\": \"on\"}"))
                .andExpect(status().isOk());

        assertThat(updatedByOfSetting()).isEqualTo("agent:" + RUN + " for " + ORGANIZER);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = "ORGANIZER")
    @DisplayName("positive control: the organizer signed in as a person is recorded by username")
    void should_recordUsername_when_personChangesAdminSetting() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings/" + SETTING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\": \"on\"}"))
                .andExpect(status().isOk());

        assertThat(updatedByOfSetting()).isEqualTo(ORGANIZER);
    }

    @Test
    @DisplayName("ownership reads the username: the agent marks its person's notification read, not another's")
    void should_passOwnership_when_agentMarksOwnNotificationRead() throws Exception {
        UUID own = notificationFor(ORGANIZER);
        UUID other = notificationFor(OTHER_ORGANIZER);

        mockMvc.perform(patch("/api/v1/notifications/" + own)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"READ\"}"))
                .andExpect(status().isOk());
        assertThat(statusOf(own)).isEqualTo("READ");

        mockMvc.perform(patch("/api/v1/notifications/" + other)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"READ\"}"))
                .andExpect(status().isNotFound());
        assertThat(statusOf(other)).isEqualTo("UNREAD");
    }

    @Test
    @DisplayName("an organizer's agent answers a conversation: the mail goes out, its sender is the person")
    void should_recordPersonAsSender_when_agentAnswersConversation() throws Exception {
        Instant date = Instant.now().plus(40, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        eventRepository.saveAndFlush(Event.builder()
                .eventCode(EVENT)
                .title("Agent Reply Probe")
                .eventNumber(9770)
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .organizerUsername(ORGANIZER)
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .currentPublishedPhase("agenda")
                .build());
        cums.user(ORGANIZER, "Carla", "Agentattr", "success+agentattr-carla@simulator.amazonses.com");
        jdbc.update("INSERT INTO mail_conversation (id, conversation_key, event_code, audience, subject, "
                + "counterpart_address, counterpart_name, status, reply_token, last_message_at) "
                + "VALUES (?, ?, ?, 'SPEAKERS', 'Slides', ?, 'Dora', 'OPEN', ?, ?)",
                UUID.randomUUID(), CONVERSATION, EVENT, SPEAKER_MAIL, "agentattrtoken01",
                Timestamp.from(Instant.now()));

        mockMvc.perform(post("/api/v1/conversations/{key}/replies", CONVERSATION)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Bis Freitag, danke.\"}"))
                .andExpect(status().isCreated());

        assertThat(sesRecorder.mailsTo(SPEAKER_MAIL)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT sender_username FROM mail_message WHERE event_code = ?"
                        + " AND direction = 'OUT'", String.class, EVENT))
                .as("the sender is an identity: the person").isEqualTo(ORGANIZER);
    }
}
