package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.shared.exception.InvalidStateTransitionException;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for WorkflowTransitionValidator - Story 5.7 (Updated for 9-State Model)
 *
 * Test Strategy: TDD Red-Green-Refactor
 * - Tests updated for 9-state consolidated workflow model
 * - Tests ensure only valid transitions are allowed
 *
 * This validator defines the state transition matrix and ensures only valid
 * transitions are allowed based on the 9-step workflow.
 *
 * Valid Transitions (9-State Model):
 * - CREATED → TOPIC_SELECTION or SPEAKER_IDENTIFICATION
 * - TOPIC_SELECTION → SPEAKER_IDENTIFICATION
 * - SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT
 * - SLOT_ASSIGNMENT → AGENDA_PUBLISHED
 * - AGENDA_PUBLISHED → AGENDA_FINALIZED
 * - AGENDA_FINALIZED → EVENT_LIVE
 * - EVENT_LIVE → EVENT_COMPLETED
 * - EVENT_COMPLETED → ARCHIVED
 *
 * Invalid Transitions (examples):
 * - CREATED → ARCHIVED (skipping all intermediate steps)
 * - AGENDA_PUBLISHED → CREATED (backwards transition)
 * - Any non-sequential jump
 */
@DisplayName("WorkflowTransitionValidator Unit Tests")
class WorkflowTransitionValidatorTest {

    private WorkflowTransitionValidator validator;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        validator = new WorkflowTransitionValidator();
        testEvent = Event.builder()
                .eventCode("BATbern56")
                .title("Test Event")
                .workflowState(EventWorkflowState.CREATED)
                .build();
    }

    // Test: Valid sequential transitions (9-State Model)
    @ParameterizedTest
    @CsvSource({
        "CREATED, TOPIC_SELECTION",
        "CREATED, SPEAKER_IDENTIFICATION",        // Can skip TOPIC_SELECTION if speaker added directly
        "TOPIC_SELECTION, SPEAKER_IDENTIFICATION",
        "SPEAKER_IDENTIFICATION, SLOT_ASSIGNMENT",
        "SLOT_ASSIGNMENT, AGENDA_PUBLISHED",
        "AGENDA_PUBLISHED, AGENDA_FINALIZED",
        "AGENDA_FINALIZED, EVENT_LIVE",
        "EVENT_LIVE, EVENT_COMPLETED",
        "EVENT_COMPLETED, ARCHIVED"
    })
    @DisplayName("Should allow valid sequential state transitions")
    void should_allowTransition_when_validSequentialTransition(EventWorkflowState fromState, EventWorkflowState toState) {
        // Given: Event in fromState
        testEvent.setWorkflowState(fromState);

        // When/Then: Valid transition should not throw exception
        assertThatCode(() ->
                validator.validateTransition(fromState, toState, testEvent)
        ).doesNotThrowAnyException();
    }

    // Test: Invalid non-sequential transitions (9-State Model)
    @ParameterizedTest
    @CsvSource({
        "CREATED, ARCHIVED",                       // Skip all intermediate steps
        "CREATED, SLOT_ASSIGNMENT",                // Skip multiple steps
        "TOPIC_SELECTION, SLOT_ASSIGNMENT",        // Jump ahead multiple steps
        "SPEAKER_IDENTIFICATION, AGENDA_PUBLISHED", // Jump ahead multiple steps
        "SLOT_ASSIGNMENT, EVENT_LIVE",             // Skip agenda phases
        "AGENDA_PUBLISHED, EVENT_COMPLETED"        // Skip multiple steps
    })
    @DisplayName("Should reject invalid non-sequential state transitions")
    void should_rejectTransition_when_invalidNonSequentialTransition(EventWorkflowState fromState, EventWorkflowState toState) {
        // Given: Event in fromState
        testEvent.setWorkflowState(fromState);

        // When/Then: Invalid transition should throw exception
        assertThatThrownBy(() ->
                validator.validateTransition(fromState, toState, testEvent)
        )
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Invalid state transition from '" + fromState + "' to '" + toState + "'");
    }

    // Test: Backwards transitions are not allowed (9-State Model)
    @ParameterizedTest
    @CsvSource({
        "TOPIC_SELECTION, CREATED",
        "SPEAKER_IDENTIFICATION, TOPIC_SELECTION",
        "SLOT_ASSIGNMENT, SPEAKER_IDENTIFICATION",
        "AGENDA_PUBLISHED, SLOT_ASSIGNMENT",
        "AGENDA_FINALIZED, AGENDA_PUBLISHED",
        "EVENT_LIVE, AGENDA_FINALIZED",
        "EVENT_COMPLETED, EVENT_LIVE"
    })
    @DisplayName("Should reject backwards state transitions")
    void should_rejectTransition_when_backwardsTransition(EventWorkflowState fromState, EventWorkflowState toState) {
        // Given: Event in fromState
        testEvent.setWorkflowState(fromState);

        // When/Then: Backwards transition should throw exception
        assertThatThrownBy(() ->
                validator.validateTransition(fromState, toState, testEvent)
        )
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Invalid state transition from '" + fromState + "' to '" + toState + "'");
    }

    // Test: Transition from same state to same state (idempotent)
    @Test
    @DisplayName("Should allow idempotent transitions (same state to same state)")
    void should_allowTransition_when_idempotentTransition() {
        // Given: Event in CREATED state
        EventWorkflowState currentState = EventWorkflowState.CREATED;
        testEvent.setWorkflowState(currentState);

        // When/Then: Transition to same state should be allowed (idempotent)
        assertThatCode(() ->
                validator.validateTransition(currentState, currentState, testEvent)
        ).doesNotThrowAnyException();
    }

    // Test: Transition from ARCHIVED state is not allowed
    @Test
    @DisplayName("Should reject any transition from ARCHIVED state (terminal state)")
    void should_rejectTransition_when_fromArchivedState() {
        // Given: Event in ARCHIVED state (terminal state)
        testEvent.setWorkflowState(EventWorkflowState.ARCHIVED);

        // When/Then: Any transition from ARCHIVED should fail (except to ARCHIVED itself)
        assertThatThrownBy(() ->
                validator.validateTransition(EventWorkflowState.ARCHIVED, EventWorkflowState.CREATED, testEvent)
        )
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Cannot transition from terminal state ARCHIVED");
    }

    // Test: Skip TOPIC_SELECTION when speaker added directly
    @Test
    @DisplayName("Should allow skipping TOPIC_SELECTION when speaker added directly")
    void should_allowSkippingTopicSelection_when_speakerAddedDirectly() {
        // Given: Event in CREATED state
        testEvent.setWorkflowState(EventWorkflowState.CREATED);

        // When/Then: Direct transition to SPEAKER_IDENTIFICATION should be allowed
        assertThatCode(() ->
                validator.validateTransition(EventWorkflowState.CREATED, EventWorkflowState.SPEAKER_IDENTIFICATION, testEvent)
        ).doesNotThrowAnyException();
    }

    // Test: Null event should throw exception
    @Test
    @DisplayName("Should throw exception when event is null")
    void should_throwException_when_eventIsNull() {
        // When/Then: Null event should throw IllegalArgumentException
        assertThatThrownBy(() ->
                validator.validateTransition(EventWorkflowState.CREATED, EventWorkflowState.TOPIC_SELECTION, null)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event cannot be null");
    }

    // Test: Null states should throw exception
    @Test
    @DisplayName("Should throw exception when fromState is null")
    void should_throwException_when_fromStateIsNull() {
        // When/Then: Null fromState should throw IllegalArgumentException
        assertThatThrownBy(() ->
                validator.validateTransition(null, EventWorkflowState.TOPIC_SELECTION, testEvent)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("State cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when toState is null")
    void should_throwException_when_toStateIsNull() {
        // When/Then: Null toState should throw IllegalArgumentException
        assertThatThrownBy(() ->
                validator.validateTransition(EventWorkflowState.CREATED, null, testEvent)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("State cannot be null");
    }
}
