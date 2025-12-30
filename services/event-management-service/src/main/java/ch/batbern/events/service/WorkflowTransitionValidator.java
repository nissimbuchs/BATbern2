package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.shared.exception.InvalidStateTransitionException;
import ch.batbern.shared.types.EventWorkflowState;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Validator for event workflow state transitions.
 *
 * Defines the state transition matrix that determines which transitions are valid.
 * The 9-step workflow follows a mostly sequential pattern with automatic and manual transitions.
 *
 * Valid Transitions (9-State Model):
 * 1. CREATED → TOPIC_SELECTION (manual)
 * 2. CREATED → SPEAKER_IDENTIFICATION (automatic when speaker added, skip TOPIC_SELECTION)
 * 3. TOPIC_SELECTION → SPEAKER_IDENTIFICATION (automatic when topic selected)
 * 4. SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT (automatic when speaker accepted)
 * 5. SLOT_ASSIGNMENT → AGENDA_PUBLISHED (automatic when all sessions timed + phase ready)
 * 6. AGENDA_PUBLISHED → AGENDA_FINALIZED (manual, typically 14 days before event)
 * 7. AGENDA_FINALIZED → EVENT_LIVE (automatic via cron when event date reached)
 * 8. EVENT_LIVE → EVENT_COMPLETED (automatic via cron when event date passed)
 * 9. EVENT_COMPLETED → ARCHIVED (manual archival)
 *
 * Special Rules:
 * - Idempotent transitions (same state to same state) are allowed
 * - ARCHIVED is a terminal state (no transitions out allowed)
 * - Backwards transitions are NOT allowed
 * - Skipping TOPIC_SELECTION is allowed when speaker added directly to pool
 *
 * State Consolidation:
 * - SPEAKER_IDENTIFICATION consolidates 7 previous states: SPEAKER_BRAINSTORMING,
 *   SPEAKER_OUTREACH, SPEAKER_CONFIRMATION, CONTENT_COLLECTION, QUALITY_REVIEW,
 *   THRESHOLD_CHECK, OVERFLOW_MANAGEMENT
 * - AGENDA_FINALIZED consolidates: NEWSLETTER_SENT, EVENT_READY
 * - ARCHIVED consolidates: PARTNER_MEETING_COMPLETE
 *
 * Story 5.7: Slot Assignment & Progressive Publishing - Workflow Reconciliation
 *
 * @see EventWorkflowState
 * @see EventWorkflowStateMachine
 */
@Component
public class WorkflowTransitionValidator {

    /**
     * State transition matrix defining valid transitions for the 9-state model.
     * Key: From state, Value: Set of valid target states
     */
    private static final Map<EventWorkflowState, Set<EventWorkflowState>> VALID_TRANSITIONS = Map.ofEntries(
            Map.entry(EventWorkflowState.CREATED, EnumSet.of(
                    EventWorkflowState.CREATED,              // Idempotent
                    EventWorkflowState.TOPIC_SELECTION,      // Manual transition
                    EventWorkflowState.SPEAKER_IDENTIFICATION // Auto-transition when speaker added (skip TOPIC_SELECTION)
            )),
            Map.entry(EventWorkflowState.TOPIC_SELECTION, EnumSet.of(
                    EventWorkflowState.TOPIC_SELECTION,      // Idempotent
                    EventWorkflowState.SPEAKER_IDENTIFICATION // Auto-transition when topic selected
            )),
            Map.entry(EventWorkflowState.SPEAKER_IDENTIFICATION, EnumSet.of(
                    EventWorkflowState.SPEAKER_IDENTIFICATION, // Idempotent
                    EventWorkflowState.SLOT_ASSIGNMENT        // Auto-transition when speaker accepted
            )),
            Map.entry(EventWorkflowState.SLOT_ASSIGNMENT, EnumSet.of(
                    EventWorkflowState.SLOT_ASSIGNMENT,   // Idempotent
                    EventWorkflowState.AGENDA_PUBLISHED   // Auto-transition when all sessions timed + phase ready
            )),
            Map.entry(EventWorkflowState.AGENDA_PUBLISHED, EnumSet.of(
                    EventWorkflowState.AGENDA_PUBLISHED,  // Idempotent
                    EventWorkflowState.AGENDA_FINALIZED   // Manual transition (typically 14 days before event)
            )),
            Map.entry(EventWorkflowState.AGENDA_FINALIZED, EnumSet.of(
                    EventWorkflowState.AGENDA_FINALIZED,  // Idempotent
                    EventWorkflowState.EVENT_LIVE         // Auto-transition via cron when event date reached
            )),
            Map.entry(EventWorkflowState.EVENT_LIVE, EnumSet.of(
                    EventWorkflowState.EVENT_LIVE,        // Idempotent
                    EventWorkflowState.EVENT_COMPLETED    // Auto-transition via cron when event date passed
            )),
            Map.entry(EventWorkflowState.EVENT_COMPLETED, EnumSet.of(
                    EventWorkflowState.EVENT_COMPLETED,   // Idempotent
                    EventWorkflowState.ARCHIVED           // Manual archival
            )),
            Map.entry(EventWorkflowState.ARCHIVED, EnumSet.of(
                    EventWorkflowState.ARCHIVED           // Terminal state - only idempotent allowed
            ))
    );

    /**
     * Validates whether a state transition is allowed.
     *
     * @param fromState Current workflow state
     * @param toState   Target workflow state
     * @param event     Event being transitioned (for additional context/validation)
     * @throws InvalidStateTransitionException if transition is not allowed
     * @throws IllegalArgumentException        if any parameter is null
     */
    public void validateTransition(EventWorkflowState fromState, EventWorkflowState toState, Event event) {
        // Validate parameters
        if (fromState == null || toState == null) {
            throw new IllegalArgumentException("State cannot be null");
        }
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        // Check if transition is in the valid transition matrix
        Set<EventWorkflowState> allowedTargets = VALID_TRANSITIONS.get(fromState);
        if (allowedTargets == null || !allowedTargets.contains(toState)) {
            // Special error message for terminal state
            if (fromState == EventWorkflowState.ARCHIVED && toState != EventWorkflowState.ARCHIVED) {
                throw new InvalidStateTransitionException(
                        "Cannot transition from terminal state ARCHIVED");
            }

            throw new InvalidStateTransitionException(fromState.name(), toState.name());
        }

        // Transition is valid
    }

    /**
     * Gets all valid target states from a given state.
     *
     * @param fromState Current workflow state
     * @return Set of valid target states
     */
    public Set<EventWorkflowState> getValidTargetStates(EventWorkflowState fromState) {
        return VALID_TRANSITIONS.getOrDefault(fromState, EnumSet.noneOf(EventWorkflowState.class));
    }

    /**
     * Checks if a transition is valid without throwing an exception.
     *
     * @param fromState Current workflow state
     * @param toState   Target workflow state
     * @return true if transition is valid, false otherwise
     */
    public boolean isValidTransition(EventWorkflowState fromState, EventWorkflowState toState) {
        if (fromState == null || toState == null) {
            return false;
        }

        Set<EventWorkflowState> allowedTargets = VALID_TRANSITIONS.get(fromState);
        return allowedTargets != null && allowedTargets.contains(toState);
    }
}
