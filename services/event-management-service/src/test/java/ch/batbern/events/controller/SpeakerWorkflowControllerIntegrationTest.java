package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SpeakerStatusController workflow endpoints (Story 6.0a).
 *
 * Tests the REST API layer for speaker workflow state transitions:
 * - PUT /api/v1/events/{eventCode}/speakers/{speakerId}/status
 *
 * Tests cover:
 * - Valid transitions return 200
 * - Invalid transitions return 422
 * - Speaker not found returns 404
 * - Unauthorized returns 403
 *
 * Uses PostgreSQL via Testcontainers for production parity.
 */
@Transactional
class SpeakerWorkflowControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    private Event testEvent;
    private static final String TEST_EVENT_CODE = "BAT-CONTROLLER-TEST";
    private static final String BASE_PATH = "/api/v1/events/{eventCode}/speakers/{speakerId}/status";

    @BeforeEach
    void setUp() {
        // Create test event
        testEvent = Event.builder()
                .eventCode(TEST_EVENT_CODE)
                .eventNumber(998)
                .title("BATbern Controller Test")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .date(Instant.now().plus(90, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(80, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("123 Test Street")
                .venueCapacity(200)
                .organizerUsername("john.doe")
                .build();
        testEvent = eventRepository.save(testEvent);
    }

    @Nested
    @DisplayName("Valid Workflow Transitions")
    class ValidTransitions {

        @Test
        @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
        @DisplayName("should return 200 when valid transition IDENTIFIED -> CONTACTED")
        void should_return200_when_validTransition() throws Exception {
            // Given: Speaker in IDENTIFIED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.IDENTIFIED);

            // When/Then: PUT request should succeed
            mockMvc.perform(put(BASE_PATH, TEST_EVENT_CODE, speaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "newStatus", "CONTACTED",
                                    "reason", "Initial outreach via email"
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentStatus").value("CONTACTED"))
                    .andExpect(jsonPath("$.previousStatus").value("IDENTIFIED"))
                    .andExpect(jsonPath("$.speakerId").value(speaker.getId().toString()));
        }

        @Test
        @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
        @DisplayName("should return 200 when transitioning to DECLINED")
        void should_return200_when_transitionToDeclined() throws Exception {
            // Given: Speaker in CONTACTED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.CONTACTED);

            // When/Then: PUT request to DECLINED should succeed
            mockMvc.perform(put(BASE_PATH, TEST_EVENT_CODE, speaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "newStatus", "DECLINED",
                                    "reason", "Speaker not available for this event"
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentStatus").value("DECLINED"))
                    .andExpect(jsonPath("$.previousStatus").value("CONTACTED"));
        }
    }

    @Nested
    @DisplayName("Invalid Workflow Transitions")
    class InvalidTransitions {

        @Test
        @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
        @DisplayName("should return 422 when invalid transition IDENTIFIED -> ACCEPTED")
        void should_return422_when_invalidTransition() throws Exception {
            // Given: Speaker in IDENTIFIED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.IDENTIFIED);

            // When/Then: PUT request to skip steps should fail with 422
            mockMvc.perform(put(BASE_PATH, TEST_EVENT_CODE, speaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "newStatus", "ACCEPTED"
                            ))))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
        @DisplayName("should return 422 when transition from terminal state DECLINED")
        void should_return422_when_transitionFromTerminalState() throws Exception {
            // Given: Speaker in DECLINED state (terminal)
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.DECLINED);

            // When/Then: PUT request from terminal state should fail with 422
            mockMvc.perform(put(BASE_PATH, TEST_EVENT_CODE, speaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "newStatus", "CONTACTED"
                            ))))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    @DisplayName("Speaker Not Found")
    class SpeakerNotFound {

        @Test
        @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
        @DisplayName("should return 404 when speaker not found")
        void should_return404_when_speakerNotFound() throws Exception {
            // Given: Non-existent speaker ID
            UUID nonExistentId = UUID.randomUUID();

            // When/Then: PUT request should return 404
            mockMvc.perform(put(BASE_PATH, TEST_EVENT_CODE, nonExistentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "newStatus", "CONTACTED"
                            ))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Authorization")
    class Authorization {

        @Test
        @DisplayName("should return 403 when unauthenticated (Spring Security default)")
        void should_return403_when_unauthenticated() throws Exception {
            // Given: Speaker in IDENTIFIED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.IDENTIFIED);

            // When/Then: PUT request without auth should return 403 (Spring Security returns 403 for anonymous)
            mockMvc.perform(put(BASE_PATH, TEST_EVENT_CODE, speaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "newStatus", "CONTACTED"
                            ))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "regular.user", roles = {"USER"})
        @DisplayName("should return 403 when user lacks ORGANIZER role")
        void should_return403_when_notOrganizer() throws Exception {
            // Given: Speaker in IDENTIFIED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.IDENTIFIED);

            // When/Then: PUT request without ORGANIZER role should return 403
            mockMvc.perform(put(BASE_PATH, TEST_EVENT_CODE, speaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "newStatus", "CONTACTED"
                            ))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Request Validation")
    class RequestValidation {

        @Test
        @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
        @DisplayName("should return 400 when newStatus is missing")
        void should_return400_when_statusMissing() throws Exception {
            // Given: Speaker in IDENTIFIED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.IDENTIFIED);

            // When/Then: PUT request without newStatus should return 400
            mockMvc.perform(put(BASE_PATH, TEST_EVENT_CODE, speaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
        @DisplayName("should return 400 when newStatus is invalid enum value")
        void should_return400_when_invalidStatus() throws Exception {
            // Given: Speaker in IDENTIFIED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.IDENTIFIED);

            // When/Then: PUT request with invalid status should return 400
            mockMvc.perform(put(BASE_PATH, TEST_EVENT_CODE, speaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "newStatus", "INVALID_STATUS"
                            ))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
        @DisplayName("should accept optional reason field")
        void should_acceptOptionalReason() throws Exception {
            // Given: Speaker in IDENTIFIED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.IDENTIFIED);

            // When/Then: PUT request with reason should succeed
            mockMvc.perform(put(BASE_PATH, TEST_EVENT_CODE, speaker.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "newStatus", "CONTACTED",
                                    "reason", "Sent initial email on 2024-01-15"
                            ))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.changeReason").value("Sent initial email on 2024-01-15"));
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a speaker in specified workflow state.
     */
    private SpeakerPool createSpeaker(SpeakerWorkflowState status) {
        SpeakerPool speaker = new SpeakerPool();
        speaker.setEventId(testEvent.getId());
        speaker.setSpeakerName("Test Speaker " + UUID.randomUUID().toString().substring(0, 8));
        speaker.setCompany("Tech Corp");
        speaker.setExpertise("Architecture");
        speaker.setStatus(status);
        return speakerPoolRepository.save(speaker);
    }
}
