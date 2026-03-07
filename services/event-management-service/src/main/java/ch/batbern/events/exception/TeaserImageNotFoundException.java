package ch.batbern.events.exception;

import ch.batbern.shared.exception.NotFoundException;

/**
 * Exception thrown when a teaser image is not found.
 * Story 10.22: Event Teaser Images
 */
public class TeaserImageNotFoundException extends NotFoundException {

    public TeaserImageNotFoundException(String imageId) {
        super("Teaser image not found with ID: " + imageId);
    }
}
