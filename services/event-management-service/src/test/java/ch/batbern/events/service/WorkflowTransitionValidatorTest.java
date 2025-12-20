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
 * Unit tests for WorkflowTransitionValidator - Story 5.1a AC6 (RED Phase)
 *
 * Test Strategy: TDD Red-Green-Refactor
 * - These tests are written BEFORE implementation (RED Phase)
 * - Tests should FAIL initially because WorkflowTransitionValidator doesn't exist yet
 * - Implementation in Task 4 will make these tests pass (GREEN Phase)
 *
 * This validator defines the state transition matrix and ensures only valid
 * transitions are allowed based on the 16-step workflow.
 *
 * Valid Transitions (examples):
 * - CREATED → TOPIC_SELECTION
 * - TOPIC_SELECTION → SPEAKER_BRAINSTORMING
 * - SPEAKER_BRAINSTORMING → SPEAKER_OUTREACH
 * - SPEAKER_OUTREACH → SPEAKER_CONFIRMATION
 * - ... (all sequential transitions)
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

    // Test: Valid sequential transitions
    @ParameterizedTest
    @CsvSource({
        "CREATED, TOPIC_SELECTION",
        "TOPIC_SELECTION, SPEAKER_BRAINSTORMING",
        "SPEAKER_BRAINSTORMING, SPEAKER_OUTREACH",
        "SPEAKER_OUTREACH, SPEAKER_CONFIRMATION",
        "SPEAKER_CONFIRMATION, CONTENT_COLLECTION",
        "CONTENT_COLLECTION, QUALITY_REVIEW",
        "QUALITY_REVIEW, THRESHOLD_CHECK",
        "THRESHOLD_CHECK, OVERFLOW_MANAGEMENT",
        "OVERFLOW_MANAGEMENT, SLOT_ASSIGNMENT",
        "SLOT_ASSIGNMENT, AGENDA_PUBLISHED",
        "AGENDA_PUBLISHED, AGENDA_FINALIZED",
        "AGENDA_FINALIZED, NEWSLETTER_SENT",
        "NEWSLETTER_SENT, EVENT_READY",
        "EVENT_READY, PARTNER_MEETING_COMPLETE",
        "PARTNER_MEETING_COMPLETE, ARCHIVED"
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

    // Test: Invalid non-sequential transitions
    @ParameterizedTest
    @CsvSource({
        "CREATED, ARCHIVED",                    // Skip all intermediate steps
        "CREATED, SPEAKER_CONFIRMATION",        // Skip multiple steps
        "TOPIC_SELECTION, SLOT_ASSIGNMENT",     // Jump ahead multiple steps
        "SPEAKER_BRAINSTORMING, AGENDA_PUBLISHED", // Jump ahead many steps
        "QUALITY_REVIEW, ARCHIVED"              // Skip to end
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

    // Test: Backwards transitions are not allowed
    @ParameterizedTest
    @CsvSource({
        "TOPIC_SELECTION, CREATED",
        "SPEAKER_CONFIRMATION, SPEAKER_OUTREACH",
        "AGENDA_FINALIZED, AGENDA_PUBLISHED"
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

    // Test: Skip OVERFLOW_MANAGEMENT when not needed
    @Test
    @DisplayName("Should allow skipping OVERFLOW_MANAGEMENT when threshold not exceeded")
    void should_allowSkippingOverflowManagement_when_thresholdNotExceeded() {
        // Given: Event in THRESHOLD_CHECK state with acceptable speaker count
        testEvent.setWorkflowState(EventWorkflowState.THRESHOLD_CHECK);

        // When/Then: Direct transition to SLOT_ASSIGNMENT should be allowed
        assertThatCode(() ->
                validator.validateTransition(EventWorkflowState.THRESHOLD_CHECK, EventWorkflowState.SLOT_ASSIGNMENT, testEvent)
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
