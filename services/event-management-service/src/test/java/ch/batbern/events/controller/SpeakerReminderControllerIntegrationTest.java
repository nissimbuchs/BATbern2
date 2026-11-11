package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reminders switch - Story 6.5 AC6.
 *
 * <p>Story 4.10 folded the former {@code PATCH /speaker-pool/{id}/reminders} into the entry PATCH,
 * {@code PATCH /events/{eventCode}/speakers/{speakerId}} with {@code remindersDisabled} (ADR-013
 * §1: no per-attribute endpoint). Real PostgreSQL via Testcontainers. The manual reminder send
 * moved to the Mail Center composer (Story 16.8c, speakers audience). Not {@code @Transactional}:
 * the class removes its own rows.
 */
class SpeakerReminderControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Event testEvent;
    private SpeakerPool invitedSpeaker;
    private SpeakerPool acceptedSpeaker;
    private String eventCode;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM speaker_pool WHERE event_id IN (SELECT id FROM events WHERE event_code = ?)",
                eventCode);
        jdbc.update("DELETE FROM events WHERE event_code = ?", eventCode);
    }

    @BeforeEach
    void setUp() {
        // The speaker paths take the real event code shape (^BATbern[0-9]+$).
        eventCode = "BATbern" + (900_000 + System.currentTimeMillis() % 100_000);

        testEvent = Event.builder()
                .eventCode(eventCode)
                .title("Test Event for Reminders")
                .eventNumber((int) (System.currentTimeMillis() % 100000))
                .date(Instant.now().plus(30, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(20, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address, 3000 Bern")
                .venueCapacity(100)
                .eventType(ch.batbern.events.core.dto.generated.EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .organizerUsername("organizer.test")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testEvent = eventRepository.save(testEvent);

        invitedSpeaker = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("John Invited")
                .status(SpeakerWorkflowState.INVITED)
                .responseDeadline(LocalDate.now().plusDays(14))
                .remindersDisabled(false)
                .build();
        invitedSpeaker = speakerPoolRepository.save(invitedSpeaker);

        acceptedSpeaker = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("Jane Accepted")
                .status(SpeakerWorkflowState.ACCEPTED)
                .contentDeadline(LocalDate.now().plusDays(7))
                .remindersDisabled(false)
                .build();
        acceptedSpeaker = speakerPoolRepository.save(acceptedSpeaker);
    }

    @Nested
    @DisplayName("PATCH /api/v1/events/{eventCode}/speakers/{speakerId} {remindersDisabled}")
    class UpdateRemindersDisabled {

        @Test
        @DisplayName("should toggle reminders disabled to true")
        @WithMockUser(username = "organizer1", roles = {"ORGANIZER"})
        void shouldToggleRemindersDisabled() throws Exception {
            String body = """
                    {"remindersDisabled": true}
                    """;

            mockMvc.perform(patch("/api/v1/events/{eventCode}/speakers/{speakerId}",
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

            mockMvc.perform(patch("/api/v1/events/{eventCode}/speakers/{speakerId}",
                            eventCode, invitedSpeaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.remindersDisabled", is(false)));

            assertThat(speakerPoolRepository.findById(invitedSpeaker.getId()).orElseThrow()
                    .getRemindersDisabled()).isFalse();
        }

        @Test
        @DisplayName("should leave the switch alone when the PATCH does not name it")
        @WithMockUser(username = "organizer1", roles = {"ORGANIZER"})
        void shouldKeepSwitch_whenFieldAbsent() throws Exception {
            invitedSpeaker.setRemindersDisabled(true);
            speakerPoolRepository.save(invitedSpeaker);

            mockMvc.perform(patch("/api/v1/events/{eventCode}/speakers/{speakerId}",
                            eventCode, invitedSpeaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"notes\": \"Rückruf am Montag\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.remindersDisabled", is(true)));

            assertThat(speakerPoolRepository.findById(invitedSpeaker.getId()).orElseThrow()
                    .getRemindersDisabled()).isTrue();
        }

        @Test
        @DisplayName("the former per-attribute endpoint is gone")
        @WithMockUser(username = "organizer1", roles = {"ORGANIZER"})
        void shouldNotServeFormerRemindersPath() throws Exception {
            mockMvc.perform(patch("/api/v1/events/{eventCode}/speaker-pool/{id}/reminders",
                            eventCode, invitedSpeaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"remindersDisabled\": true}"))
                    .andExpect(status().isNotFound());

            assertThat(speakerPoolRepository.findById(invitedSpeaker.getId()).orElseThrow()
                    .getRemindersDisabled()).isFalse();
        }

        @Test
        @DisplayName("should return 403 when not ORGANIZER role")
        @WithMockUser(username = "user1", roles = {"SPEAKER"})
        void shouldReturn403_whenNotOrganizer() throws Exception {
            String body = """
                    {"remindersDisabled": true}
                    """;

            mockMvc.perform(patch("/api/v1/events/{eventCode}/speakers/{speakerId}",
                            eventCode, invitedSpeaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }
    }
}
