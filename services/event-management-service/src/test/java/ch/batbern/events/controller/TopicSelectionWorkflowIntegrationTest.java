package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.dto.generated.topics.SelectTopicForEventRequest;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.TopicRepository;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Topic Selection Workflow (Story 5.2 Task 3a).
 *
 * Tests verify AC14-16:
 * - AC14: Event state transition to SPEAKER_IDENTIFICATION when topic selected (topic selection complete)
 * - AC15: Speaker pool state transition when speaker added (tested in SpeakerPoolWorkflowTest)
 * - AC16: Validation ensures event is in valid state before topic selection
 *
 * TDD RED PHASE: These tests should FAIL until workflow integration is implemented.
 *
 * Uses PostgreSQL via Testcontainers for production parity.
 */
@Transactional
class TopicSelectionWorkflowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TopicRepository topicRepository;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        eventRepository.deleteAll();
        topicRepository.deleteAll();
    }

    // ==================== AC14 Tests: Event State Transition ====================

    /**
     * Test 3a.1: should_transitionToSpeakerBrainstorming_when_topicSelected
     * Verifies that selecting a topic triggers EventWorkflowStateMachine.transitionToState().
     * Story 5.2 AC14: Event state transition to SPEAKER_IDENTIFICATION (topic selection complete)
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_transitionToSpeakerBrainstorming_when_topicSelected() throws Exception {
        // Given: Event in CREATED state and topic exists
        Event event = createTestEvent("BATbern56", EventWorkflowState.CREATED);
        Topic topic = createTestTopic("Cloud Native Architecture");

        // When: Select topic for event using generated DTO (ADR-006)
        SelectTopicForEventRequest request = new SelectTopicForEventRequest(topic.getTopicCode());

        mockMvc.perform(post("/api/v1/events/{eventCode}/topics", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                // Then: Request is successful
                .andDo(print())  // DEBUG: Print request/response
                .andExpect(status().isOk());

        // Verify: Event is now in SPEAKER_IDENTIFICATION state (topic selection complete)
        Event updatedEvent = eventRepository.findByEventCode(event.getEventCode()).orElseThrow();
        assertThat(updatedEvent.getWorkflowState()).isEqualTo(EventWorkflowState.SPEAKER_IDENTIFICATION);
    }

    /**
     * Test 3a.2: should_callEventWorkflowStateMachine_when_topicSelected
     * Verifies that EventWorkflowStateMachine.transitionToState() is called with correct parameters.
     * Story 5.2 AC14: Call EventWorkflowStateMachine when topic selected
     *
     * NOTE: Verified implicitly by checking the workflow state changes in other tests.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_callEventWorkflowStateMachine_when_topicSelected() throws Exception {
        // Given: Event in CREATED state
        Event event = createTestEvent("BATbern56", EventWorkflowState.CREATED);
        Topic topic = createTestTopic("Kubernetes Best Practices");

        // When: Select topic for event using generated DTO (ADR-006)
        SelectTopicForEventRequest request = new SelectTopicForEventRequest(topic.getTopicCode());

        mockMvc.perform(post("/api/v1/events/{eventCode}/topics", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Then: Verify state machine was called by checking the event state changed to SPEAKER_IDENTIFICATION
        Event updatedEvent = eventRepository.findByEventCode(event.getEventCode()).orElseThrow();
        assertThat(updatedEvent.getWorkflowState()).isEqualTo(EventWorkflowState.SPEAKER_IDENTIFICATION);
    }

    /**
     * Test 3a.3: should_publishEventWorkflowTransitionEvent_when_stateChanged
     * Verifies that EventWorkflowTransitionEvent is published when topic selection occurs.
     * Story 5.2 AC14: Publish EventWorkflowTransitionEvent domain event
     *
     * NOTE: Event publishing is verified by the EventWorkflowStateMachine's own tests.
     * This test verifies the integration works end-to-end.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_publishEventWorkflowTransitionEvent_when_stateChanged() throws Exception {
        // Given: Event in CREATED state
        Event event = createTestEvent("BATbern56", EventWorkflowState.CREATED);
        Topic topic = createTestTopic("AI/ML Fundamentals");

        // When: Select topic for event using generated DTO (ADR-006)
        SelectTopicForEventRequest request = new SelectTopicForEventRequest(topic.getTopicCode());

        mockMvc.perform(post("/api/v1/events/{eventCode}/topics", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Then: Verify the workflow transition occurred (event publishing happens in state machine)
        Event updatedEvent = eventRepository.findByEventCode(event.getEventCode()).orElseThrow();
        assertThat(updatedEvent.getWorkflowState()).isEqualTo(EventWorkflowState.SPEAKER_IDENTIFICATION);
        assertThat(updatedEvent.getTopicCode()).isEqualTo(topic.getTopicCode());
    }

    // ==================== AC16 Tests: Event State Validation ====================

    /**
     * Test 3a.4: should_validateEventState_when_attemptingTopicSelection
     * Verifies that topic selection is only allowed in early workflow states.
     * Story 5.2 AC16: Validate event state before allowing topic selection
     * Note: In 9-state model, topic selection allowed in CREATED, TOPIC_SELECTION, SPEAKER_IDENTIFICATION, ARCHIVED
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_validateEventState_when_attemptingTopicSelection() throws Exception {
        // Given: Event in SLOT_ASSIGNMENT state (invalid for topic selection)
        Event event = createTestEvent("BATbern56", EventWorkflowState.SLOT_ASSIGNMENT);
        Topic topic = createTestTopic("DevOps Best Practices");

        // When: Attempt to select topic for event in invalid state
        SelectTopicForEventRequest request = new SelectTopicForEventRequest(topic.getTopicCode());

        // Then: Returns 400 Bad Request (invalid state transition)
        mockMvc.perform(post("/api/v1/events/{eventCode}/topics", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid state transition"));
    }

    /**
     * Test 3a.5: should_throwException_when_eventNotFound
     * Verifies that 404 is returned when event does not exist.
     * Story 5.2 AC16: Validation
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_throwException_when_eventNotFound() throws Exception {
        // Given: Topic exists but event does not
        Topic topic = createTestTopic("Cloud Architecture");

        // When: Attempt to select topic for non-existent event
        SelectTopicForEventRequest request = new SelectTopicForEventRequest(topic.getTopicCode());

        // Then: Returns 404 Not Found
        mockMvc.perform(post("/api/v1/events/{eventCode}/topics", "INVALID999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    /**
     * Test 3a.6: should_throwException_when_topicNotFound
     * Verifies that 404 is returned when topic does not exist.
     * Story 5.2 AC16: Validation
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_throwException_when_topicNotFound() throws Exception {
        // Given: Event exists but topic does not
        Event event = createTestEvent("BATbern56", EventWorkflowState.CREATED);

        // When: Attempt to select non-existent topic
        SelectTopicForEventRequest request = new SelectTopicForEventRequest("non-existent-topic-code");

        // Then: Returns 404 Not Found
        mockMvc.perform(post("/api/v1/events/{eventCode}/topics", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    /**
     * Test 3a.7: should_allowReselection_when_eventInTopicSelectionState
     * Verifies that topics can be changed when event is already in TOPIC_SELECTION state.
     * Story 5.2 AC16: Allow topic changes during topic selection phase
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_allowReselection_when_eventInTopicSelectionState() throws Exception {
        // Given: Event already in TOPIC_SELECTION state
        Event event = createTestEvent("BATbern56", EventWorkflowState.TOPIC_SELECTION);
        Topic newTopic = createTestTopic("Updated Topic");

        // When: Select different topic
        SelectTopicForEventRequest request = new SelectTopicForEventRequest(newTopic.getTopicCode());

        // Then: Request is successful (re-selection allowed)
        mockMvc.perform(post("/api/v1/events/{eventCode}/topics", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    /**
     * Test 3a.8: should_persistTopicIdOnEvent_when_topicSelected
     * Verifies that topicCode is returned in the response after selection (ADR-003).
     * Story 5.2 AC14: Store topic assignment
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_persistTopicIdOnEvent_when_topicSelected() throws Exception {
        // Given: Event and topic exist
        Event event = createTestEvent("BATbern56", EventWorkflowState.CREATED);
        Topic topic = createTestTopic("Microservices Architecture");

        // When: Select topic for event using generated DTO (ADR-006)
        SelectTopicForEventRequest request = new SelectTopicForEventRequest(topic.getTopicCode());

        mockMvc.perform(post("/api/v1/events/{eventCode}/topics", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                // Then: Response includes topicCode (ADR-003 compliant)
                .andExpect(jsonPath("$.topicCode").value(topic.getTopicCode()));
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
        event.setEventType(ch.batbern.events.dto.generated.EventType.FULL_DAY); // Required field!
        event.setWorkflowState(workflowState);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        event.setCreatedBy("john.doe");
        event.setUpdatedBy("john.doe");
        return eventRepository.save(event);
    }

    private Topic createTestTopic(String title) {
        Topic topic = new Topic();
        topic.setTitle(title);
        topic.setDescription("Test description for " + title);
        topic.setCategory("technical");
        topic.setUsageCount(0);
        topic.setActive(true);
        topic.setCreatedDate(LocalDateTime.now());
        return topicRepository.save(topic);
    }

    // ==================== Topic Update Tests ====================

    /**
     * Test: should_updateTopicId_when_changingTopicInSpeakerBrainstormingState
     * Reproduces bug where topic change doesn't persist when event is already in SPEAKER_IDENTIFICATION.
     *
     * Scenario:
     * 1. Event is in SPEAKER_IDENTIFICATION state with Topic A assigned
     * 2. Organizer changes to Topic B
     * 3. Topic should be updated in database (not just in response)
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_updateTopicId_when_changingTopicInSpeakerBrainstormingState() throws Exception {
        // Given: Event in SPEAKER_IDENTIFICATION with initial topic
        Topic initialTopic = createTestTopic("Cloud Architecture");
        Topic newTopic = createTestTopic("Microservices Patterns");

        Event event = createTestEvent("BATbern98", EventWorkflowState.SPEAKER_IDENTIFICATION);
        event.setTopicCode(initialTopic.getTopicCode());
        event = eventRepository.save(event);

        // When: Change topic to newTopic
        SelectTopicForEventRequest request = new SelectTopicForEventRequest(newTopic.getTopicCode());

        mockMvc.perform(post("/api/v1/events/{eventCode}/topics", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicCode").value(newTopic.getTopicCode()))
                .andExpect(jsonPath("$.workflowState").value("SPEAKER_IDENTIFICATION"));

        // Then: Verify topic was actually updated in database
        Event updatedEvent = eventRepository.findByEventCode("BATbern98")
                .orElseThrow(() -> new AssertionError("Event not found"));

        assertThat(updatedEvent.getTopicCode())
                .as("Topic should be updated to new topic in database")
                .isEqualTo(newTopic.getTopicCode());

        assertThat(updatedEvent.getTopicCode())
                .as("Topic should not be the old topic")
                .isNotEqualTo(initialTopic.getTopicCode());
    }
}
