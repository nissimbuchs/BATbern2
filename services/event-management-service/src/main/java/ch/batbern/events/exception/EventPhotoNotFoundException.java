package ch.batbern.events.exception;

import ch.batbern.shared.exception.NotFoundException;

/**
 * Exception thrown when an event photo is not found in the database.
 * Story 10.21: Event Photos Gallery
 */
public class EventPhotoNotFoundException extends NotFoundException {

    public EventPhotoNotFoundException(String photoId) {
        super("Event photo not found with ID: " + photoId);
    }
}
