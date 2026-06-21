package ch.batbern.events.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a slot-assignment request (Story 15.3) is malformed — an unknown target
 * {@code slotKey}, or a mode that contradicts the slot's state (e.g. SWAP onto an empty
 * slot, or ASSIGN onto an occupied slot). Maps to HTTP 400.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidSlotAssignmentException extends RuntimeException {

    public InvalidSlotAssignmentException(String message) {
        super(message);
    }
}
