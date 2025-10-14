package ch.batbern.events.exception;

import java.util.UUID;

/**
 * Exception thrown when an event is not found by ID
 * Story 1.15a.1: Events API Consolidation - AC2
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(UUID eventId) {
        super("Event not found with ID: " + eventId);
    }

    public EventNotFoundException(String message) {
        super(message);
    }
}
