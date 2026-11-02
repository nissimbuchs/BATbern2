package ch.batbern.events.mailcenter;

import ch.batbern.events.client.UserApiClient;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 16.2a AC8, AC12, AC13 against real PostgreSQL: the conversation API and the automatic
 * counts. Synthetic SES simulator addresses only.
 */
@Transactional
@TestPropertySource(properties = "aws.inbound-email.bucket-name=batbern-inbound-emails-test")
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Story 16.2a: conversation API")
class MailConversationControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT = "BATbern917";
    private static final String SPEAKER = "success+speaker@simulator.amazonses.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private UserApiClient userApiClient;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void setUp() {
        eventRepository.saveAndFlush(Event.builder()
                .eventCode(EVENT).title("BATbern Test Event #917").eventNumber(917)
                .date(now.plus(30, ChronoUnit.DAYS)).registrationDeadline(now.plus(29, ChronoUnit.DAYS))
                .venueName("Test Venue").venueAddress("Bern").venueCapacity(200)
                .organizerUsername("lead.organizer").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("lead.organizer", "second.organizer"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("AC9/AC13: OPEN and WAITING first, newest activity first; DONE last; no-store")
    void should_listOpenFirst_when_conversationsExist() throws Exception {
        conversation("conv-9001", "OPEN", now.minus(3, ChronoUnit.HOURS));
        conversation("conv-9002", "DONE", now.minus(1, ChronoUnit.HOURS));
        conversation("conv-9003", "WAITING", now.minus(2, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/v1/events/" + EVENT + "/conversations"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.data[*].conversationKey", contains("conv-9003", "conv-9001", "conv-9002")))
                .andExpect(jsonPath("$.data[0].via").value("batbern917-moderator@batbern.ch"))
                .andExpect(jsonPath("$.data[0].messageCount").value(1))
                .andExpect(jsonPath("$.data[0].counterpartAddress").value(SPEAKER))
                .andExpect(jsonPath("$.pagination.totalItems").value(3));

        mockMvc.perform(get("/api/v1/events/" + EVENT + "/conversations").param("status", "OPEN"))
                .andExpect(jsonPath("$.data[*].conversationKey", contains("conv-9001")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("AC11/AC13: detail has every message oldest first, as IN with sender and attachment names")
    void should_returnMessagesOldestFirst_when_detailRequested() throws Exception {
        UUID id = conversation("conv-9010", "OPEN", now);
        message(id, "BATbern917-in-2", now.minus(1, ChronoUnit.MINUTES), "Zweite",
                "[{\"name\":\"slides.pdf\",\"contentType\":\"application/pdf\",\"size\":12}]");

        mockMvc.perform(get("/api/v1/conversations/conv-9010"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.conversation.conversationKey").value("conv-9010"))
                .andExpect(jsonPath("$.messages[*].message.mailKey", contains("conv-9010-first", "BATbern917-in-2")))
                .andExpect(jsonPath("$.messages[0].message.direction").value("IN"))
                .andExpect(jsonPath("$.messages[0].message.fromAddress").value(SPEAKER))
                .andExpect(jsonPath("$.messages[0].message.conversationKey").value("conv-9010"))
                .andExpect(jsonPath("$.messages[1].message.attachments[0].name").value("slides.pdf"))
                .andExpect(jsonPath("$.messages[1].bodyHtml").value("<p>Zweite</p>"));
    }

    @Test
    @WithMockUser(username = "second.organizer", roles = "ORGANIZER")
    @DisplayName("AC12: status and assignee are stored and visible to the next read")
    void should_storePatch_when_organizerChangesStatusAndAssignee() throws Exception {
        conversation("conv-9020", "OPEN", now);

        mockMvc.perform(patch("/api/v1/conversations/conv-9020").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"WAITING\",\"assigneeUsername\":\"second.organizer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.assigneeUsername").value("second.organizer"));

        mockMvc.perform(get("/api/v1/conversations/conv-9020"))
                .andExpect(jsonPath("$.conversation.status").value("WAITING"))
                .andExpect(jsonPath("$.conversation.assigneeUsername").value("second.organizer"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT updated_by FROM mail_conversation WHERE conversation_key = 'conv-9020'", String.class))
                .isEqualTo("second.organizer");

        mockMvc.perform(patch("/api/v1/conversations/conv-9020").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearAssignee\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeUsername").doesNotExist())
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("AC13: an assignee who is not an organizer is a 400; an unknown key a 404")
    void should_reject_when_assigneeUnknownOrKeyUnknown() throws Exception {
        conversation("conv-9030", "OPEN", now);

        mockMvc.perform(patch("/api/v1/conversations/conv-9030").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUsername\":\"someone.else\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/conversations/conv-0").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/conversations/conv-0")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/events/BATbern0/conversations")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("16.6: a kept attachment gets a short-lived link; one kept by name only, or unknown, is a 404")
    void should_linkKeptAttachment_when_requested() throws Exception {
        UUID id = conversation("conv-9060", "OPEN", now);
        message(id, "BATbern917-in-6", now, "Folien", "[{\"name\":\"slides.pdf\",\"contentType\":"
                + "\"application/pdf\",\"size\":12,\"storageKey\":\"mail-attachments/in_x/0\"},"
                + "{\"name\":\"video.mp4\",\"contentType\":\"video/mp4\",\"size\":99999999}]");

        mockMvc.perform(get("/api/v1/conversations/conv-9060"))
                .andExpect(jsonPath("$.messages[1].message.attachments[0].kept").value(true))
                .andExpect(jsonPath("$.messages[1].message.attachments[1].kept").value(false))
                .andExpect(jsonPath("$.messages[1].message.attachments[0].storageKey").doesNotExist());
        mockMvc.perform(get("/api/v1/conversations/conv-9060/messages/BATbern917-in-6/attachments/0/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.downloadUrl", containsString("mail-attachments/in_x/0")))
                .andExpect(jsonPath("$.downloadUrl", containsString("filename%2A%3DUTF-8%27%27slides.pdf")));
        mockMvc.perform(get("/api/v1/conversations/conv-9060/messages/BATbern917-in-6/attachments/1/download"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/conversations/conv-9060/messages/BATbern917-in-6/attachments/7/download"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/conversations/conv-9010/messages/BATbern917-in-6/attachments/0/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "SPEAKER")
    @DisplayName("AC13: anyone but an organizer gets 403")
    void should_forbid_when_notOrganizer() throws Exception {
        conversation("conv-9040", "OPEN", now);
        mockMvc.perform(get("/api/v1/events/" + EVENT + "/conversations")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/conversations/conv-9040")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/conversations/conv-9040/messages/conv-9040-first/attachments/0/download"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/conversations/conv-9040").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("AC8: counts list the event's automatic mail by kind; other events do not count")
    void should_countAutomaticMail_when_rowsExist() throws Exception {
        automatic("OUT_OF_OFFICE", EVENT);
        automatic("OUT_OF_OFFICE", EVENT);
        automatic("BOUNCE", EVENT);
        automatic("CALENDAR_ACCEPTED", EVENT);
        automatic("CALENDAR_DECLINED", EVENT);
        automatic("KEYWORD_ACCEPT", EVENT);
        automatic("KEYWORD_CANCEL", EVENT);
        automatic("UNSUBSCRIBE", EVENT);
        automatic("OUT_OF_OFFICE", "BATbern1");
        automatic("OUT_OF_OFFICE", null);

        mockMvc.perform(get("/api/v1/events/" + EVENT + "/mails/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedDeliveries").value(0))
                .andExpect(jsonPath("$.outOfOffice").value(2))
                .andExpect(jsonPath("$.bounces").value(1))
                .andExpect(jsonPath("$.calendarAccepts").value(1))
                .andExpect(jsonPath("$.calendarDeclines").value(1))
                .andExpect(jsonPath("$.keywordAccepts").value(1))
                .andExpect(jsonPath("$.keywordCancels").value(1))
                .andExpect(jsonPath("$.unsubscribes").value(1));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("AC13: inbound mail stays out of what went out")
    void should_keepInboundOutOfSentList_when_listingMails() throws Exception {
        conversation("conv-9050", "OPEN", now);
        mockMvc.perform(get("/api/v1/events/" + EVENT + "/mails"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(0));
    }

    /** A conversation with one inbound message ({@code {key}-first}) to the moderator alias. */
    private UUID conversation(String key, String status, Instant lastMessageAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO mail_conversation (id, conversation_key, event_code, audience, subject, "
                + "counterpart_address, counterpart_name, status, assignee_username, reply_token, last_message_at) "
                + "VALUES (?, ?, ?, 'SPEAKERS', 'Frage', ?, 'A Speaker', ?, 'lead.organizer', ?, ?)",
                id, key, EVENT, SPEAKER, status, UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                Timestamp.from(lastMessageAt));
        jdbc.update("INSERT INTO mail_message (mail_key, send_group_id, event_code, direction, audience, "
                + "content_source, subject, body_html, conversation_id, from_address, from_name, inbound_path, "
                + "alias_local_part, created_at) VALUES (?, ?, ?, 'IN', 'SPEAKERS', 'OWN_TEXT', 'Frage', "
                + "'<p>Erste</p>', ?, ?, 'A Speaker', 'ALIAS', 'batbern917-moderator', ?)",
                key + "-first", "in_" + key, EVENT, id, SPEAKER, Timestamp.from(lastMessageAt.minus(1, ChronoUnit.HOURS)));
        return id;
    }

    private void message(UUID conversation, String key, Instant at, String text, String attachments) {
        jdbc.update("INSERT INTO mail_message (mail_key, send_group_id, event_code, direction, audience, "
                + "content_source, subject, body_html, conversation_id, from_address, inbound_path, created_at, "
                + "attachments) VALUES (?, ?, ?, 'IN', 'SPEAKERS', 'OWN_TEXT', 'Re: Frage', ?, ?, ?, 'REPLIES', ?, "
                + "CAST(? AS JSONB))", key, "in_" + key, EVENT, "<p>" + text + "</p>", conversation, SPEAKER,
                Timestamp.from(at), attachments);
    }

    private void automatic(String kind, String eventCode) {
        jdbc.update("INSERT INTO mail_automatic_event (dedupe_key, event_code, kind) VALUES (?, ?, ?)",
                "test-" + UUID.randomUUID(), eventCode, kind);
    }
}
