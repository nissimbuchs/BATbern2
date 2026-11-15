package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exception thrown when an event is not found by ID
 * Story 1.15a.1: Events API Consolidation - AC2
 *
 * Extends shared-kernel NotFoundException for consistent error handling across services
 */
public class EventNotFoundException extends DomainException {

    public EventNotFoundException(UUID eventId) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, null, "Event with ID '" + eventId + "' not found");
    }

    public EventNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, null, message);
    }
}
