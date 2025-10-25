package ch.batbern.events.exception;

import ch.batbern.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Exception thrown when an event is not found by ID
 * Story 1.15a.1: Events API Consolidation - AC2
 *
 * Extends shared-kernel NotFoundException for consistent error handling across services
 */
public class EventNotFoundException extends NotFoundException {

    public EventNotFoundException(UUID eventId) {
        super("Event", eventId.toString());
    }

    public EventNotFoundException(String message) {
        super(message);
    }
}
