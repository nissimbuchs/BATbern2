package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerReminderLog;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerReminderLogRepository;
import ch.batbern.events.service.MagicLinkService;
import ch.batbern.events.service.SpeakerReminderEmailService;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SpeakerReminderController - Story 6.5.
 *
 * Tests manual reminder trigger and reminders toggle endpoints
 * against real PostgreSQL via Testcontainers.
 */
@Transactional
class SpeakerReminderControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private SpeakerReminderLogRepository reminderLogRepository;

    @MockitoBean
    private SpeakerReminderEmailService reminderEmailService;

    @MockitoBean
    private MagicLinkService magicLinkService;

    private Event testEvent;
    private SpeakerPool invitedSpeaker;
    private SpeakerPool acceptedSpeaker;
    private String eventCode;

    @BeforeEach
    void setUp() {
        eventCode = "BATbernTest" + System.currentTimeMillis();

        testEvent = Event.builder()
                .eventCode(eventCode)
                .title("Test Event for Reminders")
                .eventNumber((int) (System.currentTimeMillis() % 100000))
                .date(Instant.now().plus(30, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(20, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address, 3000 Bern")
                .venueCapacity(100)
                .eventType(ch.batbern.events.dto.generated.EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .organizerUsername("organizer.test")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testEvent = eventRepository.save(testEvent);

        invitedSpeaker = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("John Invited")
                .email("john@example.com")
                .status(SpeakerWorkflowState.INVITED)
                .responseDeadline(LocalDate.now().plusDays(14))
                .remindersDisabled(false)
                .build();
        invitedSpeaker = speakerPoolRepository.save(invitedSpeaker);

        acceptedSpeaker = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("Jane Accepted")
                .email("jane@example.com")
                .status(SpeakerWorkflowState.ACCEPTED)
                .contentStatus("PENDING")
                .contentDeadline(LocalDate.now().plusDays(7))
                .remindersDisabled(false)
                .build();
        acceptedSpeaker = speakerPoolRepository.save(acceptedSpeaker);

        // Mock email service and magic link
        doNothing().when(reminderEmailService).sendReminderEmail(
                any(), any(), any(), any(), any(), any(), any());
        when(magicLinkService.generateToken(any(UUID.class), any(TokenAction.class)))
                .thenReturn("test-magic-token");
    }

    @Nested
    @DisplayName("POST /api/v1/events/{eventCode}/speaker-pool/{id}/send-reminder")
    class SendReminder {

        @Test
        @DisplayName("should send RESPONSE reminder to INVITED speaker")
        @WithMockUser(username = "organizer1", roles = {"ORGANIZER"})
        void shouldSendResponseReminder() throws Exception {
            String body = """
                    {"reminderType": "RESPONSE", "tier": "TIER_1"}
                    """;

            mockMvc.perform(post("/api/v1/events/{eventCode}/speaker-pool/{id}/send-reminder",
                            eventCode, invitedSpeaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message", is("Reminder sent successfully")))
                    .andExpect(jsonPath("$.tier", is("TIER_1")))
                    .andExpect(jsonPath("$.emailAddress", is("john@example.com")));

            // Verify reminder was logged
            assertThat(reminderLogRepository.findAll()).hasSize(1);
            SpeakerReminderLog log = reminderLogRepository.findAll().get(0);
            assertThat(log.getReminderType()).isEqualTo("RESPONSE");
            assertThat(log.getTier()).isEqualTo("TIER_1");
            assertThat(log.getTriggeredBy()).isEqualTo("organizer1");
        }

        @Test
        @DisplayName("should send CONTENT reminder to ACCEPTED speaker")
        @WithMockUser(username = "organizer1", roles = {"ORGANIZER"})
        void shouldSendContentReminder() throws Exception {
            String body = """
                    {"reminderType": "CONTENT", "tier": "TIER_2"}
                    """;

            mockMvc.perform(post("/api/v1/events/{eventCode}/speaker-pool/{id}/send-reminder",
                            eventCode, acceptedSpeaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message", is("Reminder sent successfully")))
                    .andExpect(jsonPath("$.tier", is("TIER_2")));
        }

        @Test
        @DisplayName("should auto-detect tier when not specified")
        @WithMockUser(username = "organizer1", roles = {"ORGANIZER"})
        void shouldAutoDetectTier() throws Exception {
            String body = """
                    {"reminderType": "RESPONSE"}
                    """;

            mockMvc.perform(post("/api/v1/events/{eventCode}/speaker-pool/{id}/send-reminder",
                            eventCode, invitedSpeaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tier").exists());
        }

        @Test
        @DisplayName("should return 409 when reminders disabled")
        @WithMockUser(username = "organizer1", roles = {"ORGANIZER"})
        void shouldReturn409_whenRemindersDisabled() throws Exception {
            invitedSpeaker.setRemindersDisabled(true);
            speakerPoolRepository.save(invitedSpeaker);

            String body = """
                    {"reminderType": "RESPONSE", "tier": "TIER_1"}
                    """;

            mockMvc.perform(post("/api/v1/events/{eventCode}/speaker-pool/{id}/send-reminder",
                            eventCode, invitedSpeaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error", is("REMINDERS_DISABLED")));
        }

        @Test
        @DisplayName("should return 400 for RESPONSE when speaker not INVITED")
        @WithMockUser(username = "organizer1", roles = {"ORGANIZER"})
        void shouldReturn400_whenWrongStateForResponse() throws Exception {
            String body = """
                    {"reminderType": "RESPONSE", "tier": "TIER_1"}
                    """;

            // acceptedSpeaker is ACCEPTED, not INVITED
            mockMvc.perform(post("/api/v1/events/{eventCode}/speaker-pool/{id}/send-reminder",
                            eventCode, acceptedSpeaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", is("INVALID_STATE")));
        }

        @Test
        @DisplayName("should return 400 for CONTENT when speaker not ACCEPTED")
        @WithMockUser(username = "organizer1", roles = {"ORGANIZER"})
        void shouldReturn400_whenWrongStateForContent() throws Exception {
            String body = """
                    {"reminderType": "CONTENT", "tier": "TIER_1"}
                    """;

            // invitedSpeaker is INVITED, not ACCEPTED
            mockMvc.perform(post("/api/v1/events/{eventCode}/speaker-pool/{id}/send-reminder",
                            eventCode, invitedSpeaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", is("INVALID_STATE")));
        }

        @Test
        @DisplayName("should return 403 when not ORGANIZER role")
        @WithMockUser(username = "user1", roles = {"SPEAKER"})
        void shouldReturn403_whenNotOrganizer() throws Exception {
            String body = """
                    {"reminderType": "RESPONSE", "tier": "TIER_1"}
                    """;

            mockMvc.perform(post("/api/v1/events/{eventCode}/speaker-pool/{id}/send-reminder",
                            eventCode, invitedSpeaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 404 when event not found")
        @WithMockUser(username = "organizer1", roles = {"ORGANIZER"})
        void shouldReturn404_whenEventNotFound() throws Exception {
            String body = """
                    {"reminderType": "RESPONSE", "tier": "TIER_1"}
                    """;

            mockMvc.perform(post("/api/v1/events/{eventCode}/speaker-pool/{id}/send-reminder",
                            "NONEXISTENT", invitedSpeaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/events/{eventCode}/speaker-pool/{id}/reminders")
    class UpdateRemindersDisabled {

        @Test
        @DisplayName("should toggle reminders disabled to true")
        @WithMockUser(username = "organizer1", roles = {"ORGANIZER"})
        void shouldToggleRemindersDisabled() throws Exception {
            String body = """
                    {"remindersDisabled": true}
                    """;

            mockMvc.perform(patch("/api/v1/events/{eventCode}/speaker-pool/{id}/reminders",
                            eventCode, invitedSpeaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.remindersDisabled", is(true)));

            // Verify persisted
            SpeakerPool updated = speakerPoolRepository.findById(invitedSpeaker.getId()).orElseThrow();
            assertThat(updated.getRemindersDisabled()).isTrue();
        }

        @Test
        @DisplayName("should toggle reminders disabled to false")
        @WithMockUser(username = "organizer1", roles = {"ORGANIZER"})
        void shouldToggleRemindersEnabled() throws Exception {
            invitedSpeaker.setRemindersDisabled(true);
            speakerPoolRepository.save(invitedSpeaker);

            String body = """
                    {"remindersDisabled": false}
                    """;

            mockMvc.perform(patch("/api/v1/events/{eventCode}/speaker-pool/{id}/reminders",
                            eventCode, invitedSpeaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.remindersDisabled", is(false)));
        }

        @Test
        @DisplayName("should return 403 when not ORGANIZER role")
        @WithMockUser(username = "user1", roles = {"SPEAKER"})
        void shouldReturn403_whenNotOrganizer() throws Exception {
            String body = """
                    {"remindersDisabled": true}
                    """;

            mockMvc.perform(patch("/api/v1/events/{eventCode}/speaker-pool/{id}/reminders",
                            eventCode, invitedSpeaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }
    }
}
