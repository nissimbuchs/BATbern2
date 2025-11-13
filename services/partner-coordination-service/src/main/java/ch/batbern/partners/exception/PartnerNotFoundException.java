package ch.batbern.partners.exception;

import ch.batbern.shared.exception.NotFoundException;

/**
 * Exception thrown when a partner is not found.
 * Extends shared-kernel NotFoundException.
 */
public class PartnerNotFoundException extends NotFoundException {

    public PartnerNotFoundException(String message) {
        super(message);
    }
}
