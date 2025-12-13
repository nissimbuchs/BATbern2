package ch.batbern.shared.unit.types;

import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 5.1a - Task 1: Test for EventWorkflowState enum (AC1)
 * Tests the 16-state event workflow state machine enum.
 *
 * Test 1.1: should_containAllSixteenStates_when_EventWorkflowStateEnum_verified
 */
class EventWorkflowStateTest {

    @Test
    @DisplayName("should_containAllSixteenStates_when_EventWorkflowStateEnum_verified")
    void should_containAllSixteenStates_when_EventWorkflowStateEnum_verified() {
        // Given: Expected 16 workflow states as defined in Story 5.1a AC1
        List<String> expectedStates = Arrays.asList(
            "CREATED",
            "TOPIC_SELECTION",
            "SPEAKER_BRAINSTORMING",
            "SPEAKER_OUTREACH",
            "SPEAKER_CONFIRMATION",
            "CONTENT_COLLECTION",
            "QUALITY_REVIEW",
            "THRESHOLD_CHECK",
            "OVERFLOW_MANAGEMENT",
            "SLOT_ASSIGNMENT",
            "AGENDA_PUBLISHED",
            "AGENDA_FINALIZED",
            "NEWSLETTER_SENT",
            "EVENT_READY",
            "PARTNER_MEETING_COMPLETE",
            "ARCHIVED"
        );

        // When: Get all enum values
        EventWorkflowState[] actualStates = EventWorkflowState.values();
        List<String> actualStateNames = Arrays.stream(actualStates)
            .map(Enum::name)
            .collect(Collectors.toList());

        // Then: All 16 states should exist
        assertThat(actualStates).hasSize(16);
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
        String stateName = "SPEAKER_OUTREACH";

        // When: Convert to enum
        EventWorkflowState state = EventWorkflowState.valueOf(stateName);

        // Then: Conversion should succeed
        assertThat(state).isNotNull();
        assertThat(state).isEqualTo(EventWorkflowState.SPEAKER_OUTREACH);
        assertThat(state.name()).isEqualTo(stateName);
    }

    @Test
    @DisplayName("should_containAllWorkflowPhases_when_enumeratingStates")
    void should_containAllWorkflowPhases_when_enumeratingStates() {
        // Given: Workflow phases from Story 5.1a
        // Phase 1: Event Creation (CREATED)
        // Phase 2: Topic Selection (TOPIC_SELECTION)
        // Phase 3: Speaker Identification (SPEAKER_BRAINSTORMING, SPEAKER_OUTREACH, SPEAKER_CONFIRMATION)
        // Phase 4: Content Collection (CONTENT_COLLECTION, QUALITY_REVIEW)
        // Phase 5: Threshold & Overflow (THRESHOLD_CHECK, OVERFLOW_MANAGEMENT)
        // Phase 6: Slot Assignment (SLOT_ASSIGNMENT)
        // Phase 7: Agenda Finalization (AGENDA_PUBLISHED, AGENDA_FINALIZED)
        // Phase 8: Pre-Event (NEWSLETTER_SENT, EVENT_READY)
        // Phase 9: Post-Event (PARTNER_MEETING_COMPLETE, ARCHIVED)

        // When: Get all states
        EventWorkflowState[] states = EventWorkflowState.values();

        // Then: Verify key workflow states exist
        List<String> stateNames = Arrays.stream(states)
            .map(Enum::name)
            .collect(Collectors.toList());

        // Event creation phase
        assertThat(stateNames).contains("CREATED");

        // Speaker identification phase
        assertThat(stateNames).contains("SPEAKER_BRAINSTORMING");
        assertThat(stateNames).contains("SPEAKER_OUTREACH");
        assertThat(stateNames).contains("SPEAKER_CONFIRMATION");

        // Content & quality phase
        assertThat(stateNames).contains("CONTENT_COLLECTION");
        assertThat(stateNames).contains("QUALITY_REVIEW");

        // Threshold & overflow phase
        assertThat(stateNames).contains("THRESHOLD_CHECK");
        assertThat(stateNames).contains("OVERFLOW_MANAGEMENT");

        // Slot assignment phase
        assertThat(stateNames).contains("SLOT_ASSIGNMENT");

        // Agenda finalization phase
        assertThat(stateNames).contains("AGENDA_PUBLISHED");
        assertThat(stateNames).contains("AGENDA_FINALIZED");

        // Pre-event phase
        assertThat(stateNames).contains("NEWSLETTER_SENT");
        assertThat(stateNames).contains("EVENT_READY");

        // Post-event phase
        assertThat(stateNames).contains("PARTNER_MEETING_COMPLETE");
        assertThat(stateNames).contains("ARCHIVED");
    }
}
