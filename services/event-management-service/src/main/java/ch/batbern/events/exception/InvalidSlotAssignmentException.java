package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a slot-assignment request (Story 15.3) is malformed — an unknown target
 * {@code slotKey}, or a mode that contradicts the slot's state (e.g. SWAP onto an empty
 * slot, or ASSIGN onto an occupied slot). Maps to HTTP 400 (until Story 17.14 it answered 500:
 * no handler mapped it).
 */
public class InvalidSlotAssignmentException extends DomainException {

    public InvalidSlotAssignmentException(String message) {
        super(HttpStatus.BAD_REQUEST, Severity.MEDIUM, null, message);
    }
}
