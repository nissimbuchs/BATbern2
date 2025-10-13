package ch.batbern.events.exception;

/**
 * Exception thrown when an event is not found by ID
 * Story 1.15a.1: Events API Consolidation - AC2
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(String eventId) {
        super("Event not found with ID: " + eventId);
    }
}
