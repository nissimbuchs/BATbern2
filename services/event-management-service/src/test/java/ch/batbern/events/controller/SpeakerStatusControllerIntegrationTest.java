package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Tests for SpeakerStatusController
 * Story 5.4: Speaker Status Management (AC1-18)
 *
 * Test Scenarios:
 * - AC1-2: Manual status updates via PUT endpoint
 * - AC3-4: Status change tracking with history
 * - AC10-12: State transition validation
 * - AC13: Overflow detection
 * - AC15-16: REST API endpoints (PUT/GET status, GET history, GET summary)
 *
 * TDD Workflow: RED Phase - These tests will fail until implementation is complete
 *
 * Uses Testcontainers PostgreSQL for production parity (Migration V19).
 */
@Transactional
public class SpeakerStatusControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ch.batbern.events.repository.EventRepository eventRepository;

    @Autowired
    private ch.batbern.events.repository.SessionRepository sessionRepository;

    @Autowired
    private ch.batbern.events.repository.SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private ch.batbern.events.repository.SpeakerStatusHistoryRepository statusHistoryRepository;

    private static final String TEST_EVENT_CODE = "BATbern997";
    private static final String ORGANIZER_USERNAME = "john.doe";

    private ch.batbern.events.domain.Event testEvent;
    private ch.batbern.events.domain.Session testSession;
    private ch.batbern.events.domain.SpeakerPool testSpeaker;

    @BeforeEach
    void setUp() {
        // Clean database (reverse FK order)
        statusHistoryRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();

        // Step 1: Create Event (for event_code FK)
        testEvent = ch.batbern.events.domain.Event.builder()
                .eventCode(TEST_EVENT_CODE)
                .eventNumber(997)
                .title("Test Event for Controller Tests")
                .description("Integration test event")
                .date(java.time.Instant.now().plusSeconds(86400))
                .registrationDeadline(java.time.Instant.now())
                .venueName("Test Venue")
                .venueAddress("123 Test Street, Bern")
                .venueCapacity(100)
                .organizerUsername(ORGANIZER_USERNAME)
                .eventType(ch.batbern.events.dto.generated.EventType.EVENING)
                .workflowState(ch.batbern.shared.types.EventWorkflowState.CREATED)
                .build();
        testEvent = eventRepository.save(testEvent);

        // Step 2: Create Session (for session_id FK)
        testSession = ch.batbern.events.domain.Session.builder()
                .sessionSlug("test-session-controller")
                .eventId(testEvent.getId())
                .title("Test Session for Controller")
                .build();
        testSession = sessionRepository.save(testSession);

        // Step 3: Create SpeakerPool (for speaker_pool_id FK)
        testSpeaker = new ch.batbern.events.domain.SpeakerPool();
        testSpeaker.setEventId(testEvent.getId());
        testSpeaker.setSpeakerName("Test Speaker");
        testSpeaker.setCompany("Test Company");
        testSpeaker.setStatus(ch.batbern.shared.types.SpeakerWorkflowState.IDENTIFIED);
        testSpeaker.setSessionId(testSession.getId());
        testSpeaker = speakerPoolRepository.save(testSpeaker);
    }

    /**
     * AC1-2: should_return200_when_validStatusUpdate_requested
     * Story 5.4 AC1: Manual status updates by organizer
     * Story 5.4 AC2: Status transitions follow workflow
     */
    @Test
    @DisplayName("Should return 200 when valid status update is requested")
    void should_return200_when_validStatusUpdate_requested() throws Exception {
        // Given: Update status request (IDENTIFIED → CONTACTED)
        String updateRequest = """
                {
                    "newStatus": "CONTACTED",
                    "reason": "Initial contact via email - speaker expressed interest"
                }
                """;

        // When: PUT /api/v1/events/{code}/speakers/{speakerId}/status
        mockMvc.perform(put("/api/v1/events/{code}/speakers/{speakerId}/status",
                        TEST_EVENT_CODE, testSpeaker.getId().toString())
                        .with(user(ORGANIZER_USERNAME).roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andDo(print())
                // Then: Should return 200 with updated status
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speakerId", is(testSpeaker.getId().toString())))
                .andExpect(jsonPath("$.eventCode", is(TEST_EVENT_CODE)))
                .andExpect(jsonPath("$.currentStatus", is("CONTACTED")))
                .andExpect(jsonPath("$.previousStatus", is("IDENTIFIED")))
                .andExpect(jsonPath("$.changedByUsername", is(ORGANIZER_USERNAME)))
                .andExpect(jsonPath("$.changeReason", is("Initial contact via email - speaker expressed interest")))
                .andExpect(jsonPath("$.changedAt", notNullValue()));
    }

    /**
     * AC3-4: should_recordStatusChangeInHistory_when_statusUpdated
     * Story 5.4 AC3: Track timestamp, organizer, and reason for each status change
     * Story 5.4 AC4: Optional reason field (max 2000 characters)
     */
    @Test
    @DisplayName("Should record status change in history when status is updated")
    void should_recordStatusChangeInHistory_when_statusUpdated() throws Exception {
        // Given: Status has been updated
        String updateRequest = """
                {
                    "newStatus": "CONTACTED",
                    "reason": "Follow-up email sent with presentation outline"
                }
                """;

        // First update status
        mockMvc.perform(put("/api/v1/events/{code}/speakers/{speakerId}/status",
                        TEST_EVENT_CODE, testSpeaker.getId().toString())
                        .with(user(ORGANIZER_USERNAME).roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk());

        // When: GET /api/v1/events/{code}/speakers/{speakerId}/status/history
        mockMvc.perform(get("/api/v1/events/{code}/speakers/{speakerId}/status/history",
                        TEST_EVENT_CODE, testSpeaker.getId().toString())
                        .with(user(ORGANIZER_USERNAME).roles("ORGANIZER")))
                .andDo(print())
                // Then: Should return history with latest change
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)))
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].newStatus", is("CONTACTED")))
                .andExpect(jsonPath("$[0].previousStatus", is("IDENTIFIED")))
                .andExpect(jsonPath("$[0].changedByUsername", is(ORGANIZER_USERNAME)))
                .andExpect(jsonPath("$[0].changeReason", is("Follow-up email sent with presentation outline")))
                .andExpect(jsonPath("$[0].changedAt", notNullValue()));
    }

    /**
     * AC4: should_return400_when_reasonExceeds2000Characters
     * Story 5.4 AC4: Reason field max 2000 characters
     */
    @Test
    @DisplayName("Should return 400 when reason exceeds 2000 characters")
    void should_return400_when_reasonExceeds2000Characters() throws Exception {
        // Given: Update request with reason > 2000 characters
        String longReason = "a".repeat(2001);
        String updateRequest = String.format("""
                {
                    "newStatus": "CONTACTED",
                    "reason": "%s"
                }
                """, longReason);

        // When: PUT /api/v1/events/{code}/speakers/{speakerId}/status
        mockMvc.perform(put("/api/v1/events/{code}/speakers/{speakerId}/status",
                        TEST_EVENT_CODE, testSpeaker.getId().toString())
                        .with(user(ORGANIZER_USERNAME).roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andDo(print())
                // Then: Should return 400 validation error
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("2000")));
    }

    /**
     * AC5-6: should_return200WithSummary_when_statusSummaryRequested
     * Story 5.4 AC5: Visual dashboard showing speakers grouped by status
     * Story 5.4 AC6: Acceptance rate tracking
     */
    @Test
    @DisplayName("Should return 200 with status summary when requested")
    void should_return200WithSummary_when_statusSummaryRequested() throws Exception {
        // When: GET /api/v1/events/{code}/speakers/status-summary
        mockMvc.perform(get("/api/v1/events/{code}/speakers/status-summary", TEST_EVENT_CODE)
                        .with(user(ORGANIZER_USERNAME).roles("ORGANIZER")))
                .andDo(print())
                // Then: Should return summary with counts and rates
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode", is(TEST_EVENT_CODE)))
                .andExpect(jsonPath("$.statusCounts", notNullValue()))
                .andExpect(jsonPath("$.totalSpeakers", isA(Integer.class)))
                .andExpect(jsonPath("$.acceptedCount", isA(Integer.class)))
                .andExpect(jsonPath("$.declinedCount", isA(Integer.class)))
                .andExpect(jsonPath("$.pendingCount", isA(Integer.class)))
                .andExpect(jsonPath("$.acceptanceRate", isA(Double.class)))
                .andExpect(jsonPath("$.minSlotsRequired", isA(Integer.class)))
                .andExpect(jsonPath("$.maxSlotsAllowed", isA(Integer.class)))
                .andExpect(jsonPath("$.thresholdMet", isA(Boolean.class)))
                .andExpect(jsonPath("$.overflowDetected", isA(Boolean.class)));
    }

    /**
     * AC10-12: should_return422_when_invalidStateTransition_attempted
     * Story 5.4 AC12: Enforce valid state transitions (cannot un-accept)
     */
    @Test
    @DisplayName("Should return 422 when invalid state transition is attempted")
    void should_return422_when_invalidStateTransition_attempted() throws Exception {
        // Given: Speaker in ACCEPTED status
        // First, transition speaker through workflow to ACCEPTED state
        String toContactedRequest = """
                {
                    "newStatus": "CONTACTED",
                    "reason": "Setup: transition to CONTACTED"
                }
                """;
        mockMvc.perform(put("/api/v1/events/{code}/speakers/{speakerId}/status",
                        TEST_EVENT_CODE, testSpeaker.getId().toString())
                        .with(user(ORGANIZER_USERNAME).roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toContactedRequest))
                .andExpect(status().isOk());

        String toReadyRequest = """
                {
                    "newStatus": "READY",
                    "reason": "Setup: transition to READY"
                }
                """;
        mockMvc.perform(put("/api/v1/events/{code}/speakers/{speakerId}/status",
                        TEST_EVENT_CODE, testSpeaker.getId().toString())
                        .with(user(ORGANIZER_USERNAME).roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toReadyRequest))
                .andExpect(status().isOk());

        String toAcceptedRequest = """
                {
                    "newStatus": "ACCEPTED",
                    "reason": "Setup: transition to ACCEPTED"
                }
                """;
        mockMvc.perform(put("/api/v1/events/{code}/speakers/{speakerId}/status",
                        TEST_EVENT_CODE, testSpeaker.getId().toString())
                        .with(user(ORGANIZER_USERNAME).roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toAcceptedRequest))
                .andExpect(status().isOk());

        // Attempt invalid transition: ACCEPTED → DECLINED
        String invalidRequest = """
                {
                    "newStatus": "DECLINED",
                    "reason": "Attempting invalid transition"
                }
                """;

        // When: PUT /api/v1/events/{code}/speakers/{speakerId}/status
        mockMvc.perform(put("/api/v1/events/{code}/speakers/{speakerId}/status",
                        TEST_EVENT_CODE, testSpeaker.getId().toString())
                        .with(user(ORGANIZER_USERNAME).roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andDo(print())
                // Then: Should return 422 with error message
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("InvalidStateTransitionException")))
                .andExpect(jsonPath("$.message", containsString("transition")))
                .andExpect(jsonPath("$.message", containsString("ACCEPTED")))
                .andExpect(jsonPath("$.message", containsString("DECLINED")));
    }

    /**
     * AC12: should_allowValidTransitions_when_followingWorkflow
     * Story 5.4 AC12: Valid transitions (OPEN → CONTACTED → READY → ACCEPTED)
     */
    @Test
    @DisplayName("Should allow valid transitions when following workflow")
    void should_allowValidTransitions_when_followingWorkflow() throws Exception {
        // Transition 1: IDENTIFIED → CONTACTED
        String contactedRequest = """
                {
                    "newStatus": "CONTACTED",
                    "reason": "Initial contact"
                }
                """;

        mockMvc.perform(put("/api/v1/events/{code}/speakers/{speakerId}/status",
                        TEST_EVENT_CODE, testSpeaker.getId().toString())
                        .with(user(ORGANIZER_USERNAME).roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactedRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus", is("CONTACTED")));

        // Transition 2: CONTACTED → READY
        String readyRequest = """
                {
                    "newStatus": "READY",
                    "reason": "Speaker confirmed availability"
                }
                """;

        mockMvc.perform(put("/api/v1/events/{code}/speakers/{speakerId}/status",
                        TEST_EVENT_CODE, testSpeaker.getId().toString())
                        .with(user(ORGANIZER_USERNAME).roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(readyRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus", is("READY")));

        // Transition 3: READY → ACCEPTED
        String acceptedRequest = """
                {
                    "newStatus": "ACCEPTED",
                    "reason": "Speaker officially accepted"
                }
                """;

        mockMvc.perform(put("/api/v1/events/{code}/speakers/{speakerId}/status",
                        TEST_EVENT_CODE, testSpeaker.getId().toString())
                        .with(user(ORGANIZER_USERNAME).roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptedRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus", is("ACCEPTED")));
    }

    /**
     * AC16: should_require403_when_notAuthenticated
     * Story 5.4 AC16: REST API requires ORGANIZER role
     * Note: @PreAuthorize returns 403 (not 401) when authentication is missing
     */
    @Test
    @DisplayName("Should return 403 when not authenticated")
    void should_require403_when_notAuthenticated() throws Exception {
        // When: Request without authentication
        mockMvc.perform(get("/api/v1/events/{code}/speakers/status-summary", TEST_EVENT_CODE))
                .andDo(print())
                // Then: Should return 403 Forbidden (method-level security)
                .andExpect(status().isForbidden());
    }

    /**
     * AC16: should_require403_when_notOrganizerRole
     * Story 5.4 AC16: REST API requires ORGANIZER role
     */
    @Test
    @DisplayName("Should return 403 when user does not have ORGANIZER role")
    void should_require403_when_notOrganizerRole() throws Exception {
        // When: Request with ATTENDEE role (not ORGANIZER)
        mockMvc.perform(get("/api/v1/events/{code}/speakers/status-summary", TEST_EVENT_CODE)
                        .with(user("attendee.user").roles("ATTENDEE")))
                .andDo(print())
                // Then: Should return 403 Forbidden
                .andExpect(status().isForbidden());
    }
}
