package ch.batbern.shared.unit.types;

import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for EventWorkflowState enum (8-state model)
 * Tests the consolidated 8-state event workflow state machine enum.
 * V82: AGENDA_FINALIZED removed — scheduler drives AGENDA_PUBLISHED → EVENT_LIVE directly.
 */
class EventWorkflowStateTest {

    @Test
    @DisplayName("should_containAllEightStates_when_EventWorkflowStateEnum_verified")
    void should_containAllEightStates_when_EventWorkflowStateEnum_verified() {
        // Given: Expected 8 workflow states (V82: AGENDA_FINALIZED removed)
        List<String> expectedStates = Arrays.asList(
            "CREATED",
            "TOPIC_SELECTION",
            "SPEAKER_IDENTIFICATION",
            "SLOT_ASSIGNMENT",
            "AGENDA_PUBLISHED",
            "EVENT_LIVE",
            "EVENT_COMPLETED",
            "ARCHIVED"
        );

        // When: Get all enum values
        EventWorkflowState[] actualStates = EventWorkflowState.values();
        List<String> actualStateNames = Arrays.stream(actualStates)
            .map(Enum::name)
            .collect(Collectors.toList());

        // Then: All 8 states should exist
        assertThat(actualStates).hasSize(8);
        assertThat(actualStateNames).containsExactlyInAnyOrderElementsOf(expectedStates);
    }

    @Test
    @DisplayName("should_haveCorrectInitialState_when_eventCreated")
    void should_haveCorrectInitialState_when_eventCreated() {
        // Given/When: Initial state for new events
        EventWorkflowState initialState = EventWorkflowState.CREATED;

        // Then: Initial state should be CREATED
        assertThat(initialState).isNotNull();
        assertThat(initialState.name()).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("should_haveFinalState_when_eventArchived")
    void should_haveFinalState_when_eventArchived() {
        // Given/When: Final state for completed events
        EventWorkflowState finalState = EventWorkflowState.ARCHIVED;

        // Then: Final state should be ARCHIVED
        assertThat(finalState).isNotNull();
        assertThat(finalState.name()).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("should_supportValueOfConversion_when_stateNameProvided")
    void should_supportValueOfConversion_when_stateNameProvided() {
        // Given: State name from database or API
        String stateName = "SPEAKER_IDENTIFICATION";

        // When: Convert to enum
        EventWorkflowState state = EventWorkflowState.valueOf(stateName);

        // Then: Conversion should succeed
        assertThat(state).isNotNull();
        assertThat(state).isEqualTo(EventWorkflowState.SPEAKER_IDENTIFICATION);
        assertThat(state.name()).isEqualTo(stateName);
    }

    @Test
    @DisplayName("should_containAllWorkflowPhases_when_enumeratingStates")
    void should_containAllWorkflowPhases_when_enumeratingStates() {
        // Given: Workflow phases from 8-state model (V82)
        // Phase 1: Event Creation (CREATED)
        // Phase 2: Topic Selection (TOPIC_SELECTION)
        // Phase 3: Speaker Identification (SPEAKER_IDENTIFICATION)
        // Phase 4: Slot Assignment (SLOT_ASSIGNMENT)
        // Phase 5: Agenda Published (AGENDA_PUBLISHED)
        // Phase 6: Event Live (EVENT_LIVE) — auto-transition by scheduler on event day
        // Phase 7: Event Completed (EVENT_COMPLETED)
        // Phase 8: Archived (ARCHIVED)

        // When: Get all states
        EventWorkflowState[] states = EventWorkflowState.values();

        // Then: Verify key workflow states exist
        List<String> stateNames = Arrays.stream(states)
            .map(Enum::name)
            .collect(Collectors.toList());

        // All 8 states
        assertThat(stateNames).contains("CREATED");
        assertThat(stateNames).contains("TOPIC_SELECTION");
        assertThat(stateNames).contains("SPEAKER_IDENTIFICATION");
        assertThat(stateNames).contains("SLOT_ASSIGNMENT");
        assertThat(stateNames).contains("AGENDA_PUBLISHED");
        assertThat(stateNames).contains("EVENT_LIVE");
        assertThat(stateNames).contains("EVENT_COMPLETED");
        assertThat(stateNames).contains("ARCHIVED");
    }
}
