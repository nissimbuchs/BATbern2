package ch.batbern.events.exception;

import ch.batbern.shared.types.SpeakerWorkflowState;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Instant;

/**
 * Exception thrown when speaker has already responded to an invitation.
 * Story 6.2a: Invitation Response Portal - AC7
 *
 * Results in HTTP 409 Conflict response.
 * Includes previous response details for display to user.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AlreadyRespondedException extends RuntimeException {

    private final SpeakerWorkflowState previousResponse;
    private final Instant respondedAt;

    public AlreadyRespondedException(SpeakerWorkflowState previousResponse, Instant respondedAt) {
        super(String.format("You have already responded to this invitation with: %s", previousResponse));
        this.previousResponse = previousResponse;
        this.respondedAt = respondedAt;
    }

    public AlreadyRespondedException(SpeakerWorkflowState previousResponse) {
        this(previousResponse, null);
    }

    public SpeakerWorkflowState getPreviousResponse() {
        return previousResponse;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
