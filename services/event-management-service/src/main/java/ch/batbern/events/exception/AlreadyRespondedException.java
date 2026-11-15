package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

import ch.batbern.shared.types.SpeakerWorkflowState;

import java.time.Instant;

/**
 * Exception thrown when speaker has already responded to an invitation.
 * Story 6.2a: Invitation Response Portal - AC7
 *
 * Results in HTTP 409 Conflict response.
 * Includes previous response details for display to user.
 */
public class AlreadyRespondedException extends DomainException {

    private final SpeakerWorkflowState previousResponse;
    private final Instant respondedAt;

    public AlreadyRespondedException(SpeakerWorkflowState previousResponse, Instant respondedAt) {
        super(HttpStatus.CONFLICT, Severity.MEDIUM, null,
                String.format("You have already responded to this invitation with: %s", previousResponse));
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
