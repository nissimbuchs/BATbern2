package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Speaker Pool Management (Story 5.2 Task 4a).
 *
 * Tests verify AC9-13:
 * - AC9: Organizers can add potential speakers to event (name, company, expertise)
 * - AC10: Free-text notes field for each potential speaker
 * - AC11: Assign speakers to specific organizers for outreach
 * - AC12: Track which organizer will contact which speaker
 * - AC13: Initial status = 'identified' (not yet contacted)
 *
 * TDD RED PHASE: These tests should FAIL until speaker pool implementation is complete.
 *
 * Uses PostgreSQL via Testcontainers for production parity.
 */
@Transactional
class SpeakerPoolWorkflowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        eventRepository.deleteAll();
    }

    // ==================== AC9 Tests: Add Speaker to Pool ====================

    /**
     * Test 4a.1: should_addSpeakerToPool_when_validSpeakerDataProvided
     * Verifies that organizers can add potential speakers with name, company, and expertise.
     * Story 5.2 AC9: Add potential speakers to event
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_addSpeakerToPool_when_validSpeakerDataProvided() throws Exception {
        // Given: Event exists in TOPIC_SELECTION state
        Event event = createTestEvent("BATbern56", EventWorkflowState.TOPIC_SELECTION);

        // When: Add speaker to pool with valid data
        Map<String, Object> request = new HashMap<>();
        request.put("speakerName", "Jane Smith");
        request.put("company", "Tech Corp");
        request.put("expertise", "Cloud Architecture, Kubernetes");

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/pool", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                // Then: Request is successful
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.speakerName").value("Jane Smith"))
                .andExpect(jsonPath("$.company").value("Tech Corp"))
                .andExpect(jsonPath("$.expertise").value("Cloud Architecture, Kubernetes"));
    }

    /**
     * Test 4a.2: should_validateSpeakerName_when_addingToPool
     * Verifies that speaker name is required.
     * Story 5.2 AC9: Validation
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_validateSpeakerName_when_addingToPool() throws Exception {
        // Given: Event exists
        Event event = createTestEvent("BATbern56", EventWorkflowState.TOPIC_SELECTION);

        // When: Attempt to add speaker without name
        Map<String, Object> request = new HashMap<>();
        request.put("company", "Tech Corp");
        request.put("expertise", "Cloud Architecture");

        // Then: Returns 400 Bad Request
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/pool", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    // ==================== AC10 Tests: Speaker Notes ====================

    /**
     * Test 4a.3: should_saveNotes_when_speakerNotesProvided
     * Verifies that free-text notes can be stored for each potential speaker.
     * Story 5.2 AC10: Speaker notes field
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_saveNotes_when_speakerNotesProvided() throws Exception {
        // Given: Event exists
        Event event = createTestEvent("BATbern56", EventWorkflowState.TOPIC_SELECTION);

        // When: Add speaker with notes
        Map<String, Object> request = new HashMap<>();
        request.put("speakerName", "Jane Smith");
        request.put("company", "Tech Corp");
        request.put("expertise", "Cloud Architecture");
        request.put("notes", "Met at KubeCon 2024. Very enthusiastic about BATbern. Follow up next week.");

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/pool", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                // Then: Notes are saved
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notes").value("Met at KubeCon 2024. Very enthusiastic about BATbern. Follow up next week."));
    }

    // ==================== AC11, AC12 Tests: Assignment Strategy ====================

    /**
     * Test 4a.4: should_assignSpeakerToOrganizer_when_assignmentMade
     * Verifies that speakers can be assigned to specific organizers for outreach.
     * Story 5.2 AC11, AC12: Assignment strategy and contact distribution
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_assignSpeakerToOrganizer_when_assignmentMade() throws Exception {
        // Given: Event exists
        Event event = createTestEvent("BATbern56", EventWorkflowState.TOPIC_SELECTION);

        // When: Add speaker with organizer assignment
        Map<String, Object> request = new HashMap<>();
        request.put("speakerName", "Jane Smith");
        request.put("company", "Tech Corp");
        request.put("expertise", "Cloud Architecture");
        request.put("assignedOrganizerId", "alice.mueller");

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/pool", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                // Then: Assignment is recorded
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignedOrganizerId").value("alice.mueller"));
    }

    /**
     * Test 4a.5: should_allowUnassignedSpeaker_when_noOrganizerSpecified
     * Verifies that speakers can be added without immediate assignment.
     * Story 5.2 AC11: Assignment is optional initially
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_allowUnassignedSpeaker_when_noOrganizerSpecified() throws Exception {
        // Given: Event exists
        Event event = createTestEvent("BATbern56", EventWorkflowState.TOPIC_SELECTION);

        // When: Add speaker without assignment
        Map<String, Object> request = new HashMap<>();
        request.put("speakerName", "Jane Smith");
        request.put("company", "Tech Corp");
        request.put("expertise", "Cloud Architecture");

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/pool", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                // Then: Request succeeds, assignedOrganizerId is null
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.speakerName").value("Jane Smith"));
    }

    // ==================== AC13 Tests: Speaker Status ====================

    /**
     * Test 4a.6: should_setStatusToIdentified_when_speakerAddedToPool
     * Verifies that initial status is set to 'identified' (not yet contacted).
     * Story 5.2 AC13: Initial status = 'identified'
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_setStatusToIdentified_when_speakerAddedToPool() throws Exception {
        // Given: Event exists
        Event event = createTestEvent("BATbern56", EventWorkflowState.TOPIC_SELECTION);

        // When: Add speaker to pool
        Map<String, Object> request = new HashMap<>();
        request.put("speakerName", "Jane Smith");
        request.put("company", "Tech Corp");
        request.put("expertise", "Cloud Architecture");

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/pool", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                // Then: Status is 'IDENTIFIED'
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IDENTIFIED"));
    }

    // ==================== Validation Tests ====================

    /**
     * Test 4a.7: should_throwException_when_eventNotFound
     * Verifies that 404 is returned when event does not exist.
     * Story 5.2 AC9: Validation
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_throwException_when_eventNotFound() throws Exception {
        // Given: Event does not exist

        // When: Attempt to add speaker to non-existent event
        Map<String, Object> request = new HashMap<>();
        request.put("speakerName", "Jane Smith");
        request.put("company", "Tech Corp");
        request.put("expertise", "Cloud Architecture");

        // Then: Returns 404 Not Found
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/pool", "INVALID999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    /**
     * Test 4a.8: should_persistSpeakerPoolEntity_when_speakerAdded
     * Verifies that speaker pool entry is persisted to database.
     * Story 5.2 AC18: Speaker pool table persistence
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_persistSpeakerPoolEntity_when_speakerAdded() throws Exception {
        // Given: Event exists
        Event event = createTestEvent("BATbern56", EventWorkflowState.TOPIC_SELECTION);

        // When: Add multiple speakers
        Map<String, Object> request1 = new HashMap<>();
        request1.put("speakerName", "Jane Smith");
        request1.put("company", "Tech Corp");
        request1.put("expertise", "Cloud Architecture");

        Map<String, Object> request2 = new HashMap<>();
        request2.put("speakerName", "Bob Johnson");
        request2.put("company", "DevOps Inc");
        request2.put("expertise", "CI/CD, Docker");

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/pool", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/pool", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        // Then: Both speakers are persisted and returned with IDs
        // This will be verified in the GET endpoint test when implemented
    }

    // ==================== GET Speaker Pool Tests ====================

    /**
     * Test: should_returnSpeakerPool_when_eventHasSpeakers
     * Verifies that GET endpoint returns list of speakers in the pool.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_returnSpeakerPool_when_eventHasSpeakers() throws Exception {
        // Given: Event with speakers in pool
        Event event = createTestEvent("BATbern56", EventWorkflowState.SPEAKER_IDENTIFICATION);

        // Add speakers to pool
        Map<String, Object> speaker1 = new HashMap<>();
        speaker1.put("speakerName", "Jane Smith");
        speaker1.put("company", "Tech Corp");
        speaker1.put("expertise", "Cloud");

        Map<String, Object> speaker2 = new HashMap<>();
        speaker2.put("speakerName", "John Doe");
        speaker2.put("company", "Dev Inc");
        speaker2.put("expertise", "Security");

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/pool", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(speaker1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/pool", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(speaker2)))
                .andExpect(status().isCreated());

        // When: Get speaker pool
        mockMvc.perform(get("/api/v1/events/{eventCode}/speakers/pool", event.getEventCode()))
                // Then: Returns list with both speakers
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].speakerName").value("Jane Smith"))
                .andExpect(jsonPath("$[1].speakerName").value("John Doe"));
    }

    /**
     * Test: should_returnEmptyList_when_noSpeakersInPool
     * Verifies that GET endpoint returns empty list when no speakers added yet.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_returnEmptyList_when_noSpeakersInPool() throws Exception {
        // Given: Event with no speakers in pool
        Event event = createTestEvent("BATbern56", EventWorkflowState.SPEAKER_IDENTIFICATION);

        // When: Get speaker pool
        mockMvc.perform(get("/api/v1/events/{eventCode}/speakers/pool", event.getEventCode()))
                // Then: Returns empty list
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ==================== Helper Methods ====================

    private Event createTestEvent(String eventCode, EventWorkflowState workflowState) {
        Event event = new Event();
        event.setEventCode(eventCode);
        event.setEventNumber(56);
        event.setTitle("Test Event");
        event.setDate(Instant.now().plusSeconds(90 * 24 * 3600)); // 90 days from now
        event.setRegistrationDeadline(Instant.now().plusSeconds(60 * 24 * 3600)); // 60 days from now
        event.setVenueName("Test Venue");
        event.setVenueAddress("Test Address");
        event.setVenueCapacity(200);
        event.setOrganizerUsername("john.doe");
        event.setEventType(ch.batbern.events.dto.generated.EventType.FULL_DAY);
        event.setWorkflowState(workflowState);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        event.setCreatedBy("john.doe");
        event.setUpdatedBy("john.doe");
        return eventRepository.save(event);
    }
}
