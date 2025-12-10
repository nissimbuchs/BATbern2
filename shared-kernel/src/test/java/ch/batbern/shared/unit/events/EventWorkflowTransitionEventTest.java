package ch.batbern.shared.unit.events;

import ch.batbern.shared.events.EventWorkflowTransitionEvent;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 5.1a - Task 1: Test for EventWorkflowTransitionEvent (AC2)
 * Tests domain event for workflow state transitions.
 *
 * Test 1.2: should_serializeAndDeserialize_when_EventWorkflowTransitionEvent_created
 */
class EventWorkflowTransitionEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("should_serializeAndDeserialize_when_EventWorkflowTransitionEvent_created")
    void should_serializeAndDeserialize_when_EventWorkflowTransitionEvent_created() throws Exception {
        // Given: A workflow transition event
        String eventCode = "BATbern56";
        EventWorkflowState fromState = EventWorkflowState.CREATED;
        EventWorkflowState toState = EventWorkflowState.TOPIC_SELECTION;
        String organizerUsername = "john.doe";
        Instant transitionedAt = Instant.now();

        EventWorkflowTransitionEvent event = new EventWorkflowTransitionEvent(
            eventCode,
            fromState,
            toState,
            organizerUsername,
            transitionedAt
        );

        // When: Serialize to JSON
        String json = objectMapper.writeValueAsString(event);

        // Then: JSON should contain all fields
        assertThat(json).isNotNull();
        assertThat(json).contains("\"eventCode\":\"" + eventCode + "\"");
        assertThat(json).contains("\"fromState\"");
        assertThat(json).contains("\"toState\"");
        assertThat(json).contains("\"organizerUsername\":\"" + organizerUsername + "\"");
        assertThat(json).contains("\"transitionedAt\"");

        // When: Deserialize from JSON
        EventWorkflowTransitionEvent deserializedEvent = objectMapper.readValue(json, EventWorkflowTransitionEvent.class);

        // Then: All fields should match
        assertThat(deserializedEvent).isNotNull();
        assertThat(deserializedEvent.getEventCode()).isEqualTo(eventCode);
        assertThat(deserializedEvent.getFromState()).isEqualTo(fromState);
        assertThat(deserializedEvent.getToState()).isEqualTo(toState);
        assertThat(deserializedEvent.getOrganizerUsername()).isEqualTo(organizerUsername);
        assertThat(deserializedEvent.getTransitionedAt()).isEqualTo(transitionedAt);
    }

    @Test
    @DisplayName("should_createValidEvent_when_allRequiredFieldsProvided")
    void should_createValidEvent_when_allRequiredFieldsProvided() {
        // Given: Valid workflow transition data
        String eventCode = "BATbern56";
        EventWorkflowState fromState = EventWorkflowState.SPEAKER_BRAINSTORMING;
        EventWorkflowState toState = EventWorkflowState.SPEAKER_OUTREACH;
        String organizerUsername = "jane.smith";
        Instant transitionedAt = Instant.now();

        // When: Create event
        EventWorkflowTransitionEvent event = new EventWorkflowTransitionEvent(
            eventCode,
            fromState,
            toState,
            organizerUsername,
            transitionedAt
        );

        // Then: Event should be valid with all fields populated
        assertThat(event).isNotNull();
        assertThat(event.getEventCode()).isEqualTo(eventCode);
        assertThat(event.getFromState()).isEqualTo(fromState);
        assertThat(event.getToState()).isEqualTo(toState);
        assertThat(event.getOrganizerUsername()).isEqualTo(organizerUsername);
        assertThat(event.getTransitionedAt()).isEqualTo(transitionedAt);

        // Inherited from DomainEvent
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getEventType()).isNotNull();
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getVersion()).isEqualTo("1.0");
        assertThat(event.getMetadata()).isNotNull();
    }

    @Test
    @DisplayName("should_includeContext_when_eventCreated")
    void should_includeContext_when_eventCreated() {
        // Given: Workflow transition
        String eventCode = "BATbern56";
        EventWorkflowState fromState = EventWorkflowState.CONTENT_COLLECTION;
        EventWorkflowState toState = EventWorkflowState.QUALITY_REVIEW;
        String organizerUsername = "john.doe";
        Instant transitionedAt = Instant.now();

        // When: Create event
        EventWorkflowTransitionEvent event = new EventWorkflowTransitionEvent(
            eventCode,
            fromState,
            toState,
            organizerUsername,
            transitionedAt
        );

        // Then: Context map should contain transition details
        assertThat(event.getContext()).isNotNull();
        assertThat(event.getContext()).containsKey("eventCode");
        assertThat(event.getContext()).containsKey("fromState");
        assertThat(event.getContext()).containsKey("toState");
        assertThat(event.getContext()).containsKey("organizer");

        assertThat(event.getContext().get("eventCode")).isEqualTo(eventCode);
        assertThat(event.getContext().get("fromState")).isEqualTo(fromState.name());
        assertThat(event.getContext().get("toState")).isEqualTo(toState.name());
        assertThat(event.getContext().get("organizer")).isEqualTo(organizerUsername);
    }

    @Test
    @DisplayName("should_supportAllWorkflowTransitions_when_enumeratingStates")
    void should_supportAllWorkflowTransitions_when_enumeratingStates() {
        // Given: Various workflow transitions
        String eventCode = "BATbern56";
        String organizerUsername = "john.doe";
        Instant now = Instant.now();

        // When/Then: Create events for different transitions
        EventWorkflowTransitionEvent createToTopicSelection = new EventWorkflowTransitionEvent(
            eventCode, EventWorkflowState.CREATED, EventWorkflowState.TOPIC_SELECTION, organizerUsername, now
        );
        assertThat(createToTopicSelection.getFromState()).isEqualTo(EventWorkflowState.CREATED);
        assertThat(createToTopicSelection.getToState()).isEqualTo(EventWorkflowState.TOPIC_SELECTION);

        EventWorkflowTransitionEvent speakerOutreachToConfirmation = new EventWorkflowTransitionEvent(
            eventCode, EventWorkflowState.SPEAKER_OUTREACH, EventWorkflowState.SPEAKER_CONFIRMATION, organizerUsername, now
        );
        assertThat(speakerOutreachToConfirmation.getFromState()).isEqualTo(EventWorkflowState.SPEAKER_OUTREACH);
        assertThat(speakerOutreachToConfirmation.getToState()).isEqualTo(EventWorkflowState.SPEAKER_CONFIRMATION);

        EventWorkflowTransitionEvent publishedToArchived = new EventWorkflowTransitionEvent(
            eventCode, EventWorkflowState.AGENDA_PUBLISHED, EventWorkflowState.ARCHIVED, organizerUsername, now
        );
        assertThat(publishedToArchived.getFromState()).isEqualTo(EventWorkflowState.AGENDA_PUBLISHED);
        assertThat(publishedToArchived.getToState()).isEqualTo(EventWorkflowState.ARCHIVED);
    }

    @Test
    @DisplayName("should_haveUniqueEventId_when_multipleEventsCreated")
    void should_haveUniqueEventId_when_multipleEventsCreated() {
        // Given: Same transition parameters
        String eventCode = "BATbern56";
        EventWorkflowState fromState = EventWorkflowState.CREATED;
        EventWorkflowState toState = EventWorkflowState.TOPIC_SELECTION;
        String organizerUsername = "john.doe";
        Instant now = Instant.now();

        // When: Create multiple events
        EventWorkflowTransitionEvent event1 = new EventWorkflowTransitionEvent(
            eventCode, fromState, toState, organizerUsername, now
        );
        EventWorkflowTransitionEvent event2 = new EventWorkflowTransitionEvent(
            eventCode, fromState, toState, organizerUsername, now
        );

        // Then: Each event should have unique eventId (UUID)
        assertThat(event1.getEventId()).isNotNull();
        assertThat(event2.getEventId()).isNotNull();
        assertThat(event1.getEventId()).isNotEqualTo(event2.getEventId());
    }

    @Test
    @DisplayName("should_inheritDomainEventProperties_when_created")
    void should_inheritDomainEventProperties_when_created() {
        // Given: Workflow transition event
        EventWorkflowTransitionEvent event = new EventWorkflowTransitionEvent(
            "BATbern56",
            EventWorkflowState.CREATED,
            EventWorkflowState.TOPIC_SELECTION,
            "john.doe",
            Instant.now()
        );

        // Then: Should inherit DomainEvent properties
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getVersion()).isEqualTo("1.0");
        assertThat(event.getMetadata()).isNotNull();
        assertThat(event.getMetadata()).containsKey("source");
        assertThat(event.getMetadata().get("source")).isEqualTo("shared-kernel");
    }
}
