package ch.batbern.events.exception;

/**
 * Thrown when a caller attempts to transition a speaker to {@code READY} via
 * {@code PUT /api/v1/events/{eventCode}/speakers/{speakerId}/status} — the {@code READY}
 * state requires an email payload for User provisioning and is reachable only via
 * {@code POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote} (Story 11.D.1).
 *
 * <p>Mapped to HTTP 400 Bad Request with code
 * {@code READY_REQUIRES_PROMOTE_ENDPOINT} by {@code GlobalExceptionHandler}: the request
 * is syntactically valid (Jackson accepts {@code READY} as a SpeakerWorkflowState enum
 * value) but the {@code PUT /status} endpoint has no field to carry the email payload
 * that READY needs.
 *
 * <p>Per Story 11.B.3 AC5, the rejection lives at the controller layer (not inside
 * {@code SpeakerWorkflowService.transition()}) so the workflow service can still accept
 * {@code target = READY} from the future {@code POST /promote} endpoint.
 *
 * @see SlotCapacityReachedException
 */
public class ReadyRequiresPromoteException extends RuntimeException {

    /**
     * @param eventCode the event code (e.g., "BATbern56") — included in the message for
     *                  client-side error context.
     */
    public ReadyRequiresPromoteException(String eventCode) {
        super(String.format(
                "Speakers cannot be transitioned to READY via PUT /status — the READY state requires "
                        + "an email payload for User provisioning. Use POST /api/v1/events/%s/speakers/{speakerId}/promote "
                        + "(Story 11.D.1) instead.",
                eventCode));
    }
}
