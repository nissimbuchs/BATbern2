package ch.batbern.events.exception;

import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.Getter;

/**
 * Thrown by {@code SpeakerStatusController.promoteSpeakerToReady} when {@code /promote}
 * is called on a speaker whose current state is neither {@code CONTACTED} nor {@code READY}.
 *
 * <p>Mapped to HTTP 409 Conflict by {@code GlobalExceptionHandler} with body
 * {@code details.code = "INVALID_PROMOTION_STATE"} and
 * {@code details.currentState = <state>} so the frontend can render a tailored message.
 *
 * <p>The {@code CONTACTED → READY} happy path returns 200; the {@code READY → READY}
 * same-state branch is also an idempotent 200 (handled by
 * {@code SpeakerWorkflowService.transition()} per Story 11.B.2). Every other state — including
 * the terminal {@code DECLINED} and the still-needs-outreach {@code IDENTIFIED} — returns
 * this 409 with a state-specific message.
 *
 * @see SlotCapacityReachedException
 * @see ReadyRequiresPromoteException
 */
@Getter
public class InvalidPromotionStateException extends RuntimeException {

    private final SpeakerWorkflowState currentState;

    public InvalidPromotionStateException(SpeakerWorkflowState currentState, String message) {
        super(message);
        this.currentState = currentState;
    }
}
