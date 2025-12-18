package ch.batbern.speakers.validator;

import ch.batbern.shared.exception.InvalidStateTransitionException;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Validator for speaker status transitions
 * Story 5.4: Speaker Status Management - Task 5 (GREEN Phase)
 *
 * Implements workflow validation rules from AC12:
 * - Valid transitions: IDENTIFIED → CONTACTED → READY → ACCEPTED/DECLINED
 * - Terminal states: ACCEPTED, DECLINED (cannot transition further in most cases)
 * - ACCEPTED can only transition to SLOT_ASSIGNED
 * - DECLINED is a terminal state (cannot transition out)
 *
 * Valid State Machine:
 * IDENTIFIED → CONTACTED → READY → ACCEPTED → SLOT_ASSIGNED
 *            ↘ DECLINED   ↘ DECLINED
 */
@Component
public class StatusTransitionValidator {

    /**
     * Map of allowed transitions: from state → set of allowed target states
     */
    private static final Map<SpeakerWorkflowState, Set<SpeakerWorkflowState>> ALLOWED_TRANSITIONS = Map.of(
        SpeakerWorkflowState.IDENTIFIED, Set.of(
            SpeakerWorkflowState.CONTACTED,
            SpeakerWorkflowState.DECLINED
        ),
        SpeakerWorkflowState.CONTACTED, Set.of(
            SpeakerWorkflowState.READY,
            SpeakerWorkflowState.DECLINED
        ),
        SpeakerWorkflowState.READY, Set.of(
            SpeakerWorkflowState.ACCEPTED,
            SpeakerWorkflowState.DECLINED
        ),
        SpeakerWorkflowState.ACCEPTED, Set.of(
            SpeakerWorkflowState.SLOT_ASSIGNED,
            SpeakerWorkflowState.CONFIRMED
        ),
        SpeakerWorkflowState.SLOT_ASSIGNED, Set.of(
            SpeakerWorkflowState.CONFIRMED,
            SpeakerWorkflowState.CONTENT_SUBMITTED
        ),
        SpeakerWorkflowState.CONTENT_SUBMITTED, Set.of(
            SpeakerWorkflowState.QUALITY_REVIEWED
        ),
        SpeakerWorkflowState.QUALITY_REVIEWED, Set.of(
            SpeakerWorkflowState.CONFIRMED
        )
        // DECLINED, CONFIRMED, WITHDREW are terminal states - no transitions allowed
    );

    /**
     * Check if a state transition is valid
     *
     * @param from Current state
     * @param to Target state
     * @return true if transition is allowed, false otherwise
     */
    public boolean isValidTransition(SpeakerWorkflowState from, SpeakerWorkflowState to) {
        if (from == null || to == null) {
            return false;
        }

        // Same state is always valid (idempotent)
        if (from == to) {
            return true;
        }

        Set<SpeakerWorkflowState> allowedTargets = ALLOWED_TRANSITIONS.get(from);
        return allowedTargets != null && allowedTargets.contains(to);
    }

    /**
     * Validate state transition and throw exception if invalid
     *
     * @param from Current state
     * @param to Target state
     * @throws InvalidStateTransitionException if transition is not allowed
     */
    public void validateTransition(SpeakerWorkflowState from, SpeakerWorkflowState to) {
        if (!isValidTransition(from, to)) {
            throw new InvalidStateTransitionException(from.name(), to.name());
        }
    }
}
