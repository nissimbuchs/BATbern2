package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.exception.InvalidStateTransitionException;
import ch.batbern.shared.types.EventWorkflowState;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Validator for event workflow state transitions.
 *
 * Defines the state transition matrix that determines which transitions are valid.
 * The 16-step workflow follows a mostly sequential pattern with a few allowed skips.
 *
 * Valid Transitions:
 * 1. CREATED → TOPIC_SELECTION
 * 2. TOPIC_SELECTION → SPEAKER_BRAINSTORMING
 * 3. SPEAKER_BRAINSTORMING → SPEAKER_OUTREACH
 * 4. SPEAKER_OUTREACH → SPEAKER_CONFIRMATION
 * 5. SPEAKER_CONFIRMATION → CONTENT_COLLECTION
 * 6. CONTENT_COLLECTION → QUALITY_REVIEW
 * 7. QUALITY_REVIEW → THRESHOLD_CHECK
 * 8. THRESHOLD_CHECK → OVERFLOW_MANAGEMENT or SLOT_ASSIGNMENT (can skip OVERFLOW)
 * 9. OVERFLOW_MANAGEMENT → SLOT_ASSIGNMENT
 * 10. SLOT_ASSIGNMENT → AGENDA_PUBLISHED
 * 11. AGENDA_PUBLISHED → AGENDA_FINALIZED
 * 12. AGENDA_FINALIZED → NEWSLETTER_SENT
 * 13. NEWSLETTER_SENT → EVENT_READY
 * 14. EVENT_READY → PARTNER_MEETING_COMPLETE
 * 15. PARTNER_MEETING_COMPLETE → ARCHIVED
 *
 * Special Rules:
 * - Idempotent transitions (same state to same state) are allowed
 * - ARCHIVED is a terminal state (no transitions out allowed)
 * - Backwards transitions are NOT allowed
 * - Skipping OVERFLOW_MANAGEMENT is allowed when threshold not exceeded
 *
 * Story 5.1a: Workflow State Machine Foundation - AC6
 *
 * @see EventWorkflowState
 * @see EventWorkflowStateMachine
 */
@Component
public class WorkflowTransitionValidator {

    /**
     * State transition matrix defining valid transitions.
     * Key: From state, Value: Set of valid target states
     */
    private static final Map<EventWorkflowState, Set<EventWorkflowState>> VALID_TRANSITIONS = Map.ofEntries(
            Map.entry(EventWorkflowState.CREATED, EnumSet.of(
                    EventWorkflowState.CREATED,          // Idempotent
                    EventWorkflowState.TOPIC_SELECTION
            )),
            Map.entry(EventWorkflowState.TOPIC_SELECTION, EnumSet.of(
                    EventWorkflowState.TOPIC_SELECTION,  // Idempotent
                    EventWorkflowState.SPEAKER_BRAINSTORMING
            )),
            Map.entry(EventWorkflowState.SPEAKER_BRAINSTORMING, EnumSet.of(
                    EventWorkflowState.SPEAKER_BRAINSTORMING, // Idempotent
                    EventWorkflowState.SPEAKER_OUTREACH
            )),
            Map.entry(EventWorkflowState.SPEAKER_OUTREACH, EnumSet.of(
                    EventWorkflowState.SPEAKER_OUTREACH,  // Idempotent
                    EventWorkflowState.SPEAKER_CONFIRMATION
            )),
            Map.entry(EventWorkflowState.SPEAKER_CONFIRMATION, EnumSet.of(
                    EventWorkflowState.SPEAKER_CONFIRMATION, // Idempotent
                    EventWorkflowState.CONTENT_COLLECTION
            )),
            Map.entry(EventWorkflowState.CONTENT_COLLECTION, EnumSet.of(
                    EventWorkflowState.CONTENT_COLLECTION, // Idempotent
                    EventWorkflowState.QUALITY_REVIEW
            )),
            Map.entry(EventWorkflowState.QUALITY_REVIEW, EnumSet.of(
                    EventWorkflowState.QUALITY_REVIEW,    // Idempotent
                    EventWorkflowState.THRESHOLD_CHECK
            )),
            Map.entry(EventWorkflowState.THRESHOLD_CHECK, EnumSet.of(
                    EventWorkflowState.THRESHOLD_CHECK,   // Idempotent
                    EventWorkflowState.OVERFLOW_MANAGEMENT,
                    EventWorkflowState.SLOT_ASSIGNMENT    // Can skip OVERFLOW_MANAGEMENT
            )),
            Map.entry(EventWorkflowState.OVERFLOW_MANAGEMENT, EnumSet.of(
                    EventWorkflowState.OVERFLOW_MANAGEMENT, // Idempotent
                    EventWorkflowState.SLOT_ASSIGNMENT
            )),
            Map.entry(EventWorkflowState.SLOT_ASSIGNMENT, EnumSet.of(
                    EventWorkflowState.SLOT_ASSIGNMENT,   // Idempotent
                    EventWorkflowState.AGENDA_PUBLISHED
            )),
            Map.entry(EventWorkflowState.AGENDA_PUBLISHED, EnumSet.of(
                    EventWorkflowState.AGENDA_PUBLISHED,  // Idempotent
                    EventWorkflowState.AGENDA_FINALIZED
            )),
            Map.entry(EventWorkflowState.AGENDA_FINALIZED, EnumSet.of(
                    EventWorkflowState.AGENDA_FINALIZED,  // Idempotent
                    EventWorkflowState.NEWSLETTER_SENT
            )),
            Map.entry(EventWorkflowState.NEWSLETTER_SENT, EnumSet.of(
                    EventWorkflowState.NEWSLETTER_SENT,   // Idempotent
                    EventWorkflowState.EVENT_READY
            )),
            Map.entry(EventWorkflowState.EVENT_READY, EnumSet.of(
                    EventWorkflowState.EVENT_READY,       // Idempotent
                    EventWorkflowState.PARTNER_MEETING_COMPLETE
            )),
            Map.entry(EventWorkflowState.PARTNER_MEETING_COMPLETE, EnumSet.of(
                    EventWorkflowState.PARTNER_MEETING_COMPLETE, // Idempotent
                    EventWorkflowState.ARCHIVED
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
                throw new InvalidStateTransitionException(fromState, toState,
                        "Cannot transition from terminal state ARCHIVED");
            }

            throw new InvalidStateTransitionException(fromState, toState);
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
