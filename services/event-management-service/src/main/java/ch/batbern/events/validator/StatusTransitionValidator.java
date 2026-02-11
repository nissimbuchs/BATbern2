package ch.batbern.events.validator;

import ch.batbern.shared.exception.InvalidStateTransitionException;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Validator for speaker status transitions
 * Story 5.4: Speaker Status Management - Task 5 (GREEN Phase)
 *
 * Implements workflow validation rules:
 * - Any non-terminal state can transition to DECLINED (speaker can decline at any point)
 * - CONFIRMED can also transition to DECLINED (organizer can remove from agenda)
 * - DECLINED is a terminal state (cannot transition out)
 *
 * Valid State Machine:
 * IDENTIFIED → INVITED → CONTACTED → READY → ACCEPTED → SLOT_ASSIGNED →
 * CONTENT_SUBMITTED → QUALITY_REVIEWED → CONFIRMED
 * All states ↘ DECLINED
 */
@Component
public class StatusTransitionValidator {

    /**
     * Map of allowed transitions: from state → set of allowed target states
     */
    private static final Map<SpeakerWorkflowState, Set<SpeakerWorkflowState>> ALLOWED_TRANSITIONS = Map.ofEntries(
        Map.entry(SpeakerWorkflowState.IDENTIFIED, Set.of(
            SpeakerWorkflowState.INVITED,
            SpeakerWorkflowState.CONTACTED,
            SpeakerWorkflowState.DECLINED
        )),
        Map.entry(SpeakerWorkflowState.INVITED, Set.of(
            SpeakerWorkflowState.CONTACTED,
            SpeakerWorkflowState.READY,
            SpeakerWorkflowState.ACCEPTED,
            SpeakerWorkflowState.DECLINED
        )),
        Map.entry(SpeakerWorkflowState.CONTACTED, Set.of(
            SpeakerWorkflowState.READY,
            SpeakerWorkflowState.DECLINED
        )),
        Map.entry(SpeakerWorkflowState.READY, Set.of(
            SpeakerWorkflowState.ACCEPTED,
            SpeakerWorkflowState.DECLINED
        )),
        Map.entry(SpeakerWorkflowState.ACCEPTED, Set.of(
            SpeakerWorkflowState.SLOT_ASSIGNED,
            SpeakerWorkflowState.CONFIRMED,
            SpeakerWorkflowState.DECLINED
        )),
        Map.entry(SpeakerWorkflowState.SLOT_ASSIGNED, Set.of(
            SpeakerWorkflowState.CONFIRMED,
            SpeakerWorkflowState.CONTENT_SUBMITTED,
            SpeakerWorkflowState.DECLINED
        )),
        Map.entry(SpeakerWorkflowState.CONTENT_SUBMITTED, Set.of(
            SpeakerWorkflowState.QUALITY_REVIEWED,
            SpeakerWorkflowState.DECLINED
        )),
        Map.entry(SpeakerWorkflowState.QUALITY_REVIEWED, Set.of(
            SpeakerWorkflowState.CONFIRMED,
            SpeakerWorkflowState.DECLINED
        )),
        Map.entry(SpeakerWorkflowState.CONFIRMED, Set.of(
            SpeakerWorkflowState.DECLINED
        ))
        // DECLINED, WITHDREW are terminal states - no transitions allowed
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
