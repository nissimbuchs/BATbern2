package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a teaser image is not found.
 * Story 10.22: Event Teaser Images
 */
public class TeaserImageNotFoundException extends DomainException {

    public TeaserImageNotFoundException(String imageId) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, null, "Teaser image not found with ID: " + imageId);
    }
}
