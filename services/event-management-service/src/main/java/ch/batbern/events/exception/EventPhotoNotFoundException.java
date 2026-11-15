package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an event photo is not found in the database.
 * Story 10.21: Event Photos Gallery
 */
public class EventPhotoNotFoundException extends DomainException {

    public EventPhotoNotFoundException(String photoId) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, null, "Event photo not found with ID: " + photoId);
    }
}
