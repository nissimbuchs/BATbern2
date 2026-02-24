package ch.batbern.partners.exception;

import ch.batbern.shared.exception.NotFoundException;

/**
 * Exception thrown when a partner note is not found — Story 8.4.
 */
public class PartnerNoteNotFoundException extends NotFoundException {

    public PartnerNoteNotFoundException(String message) {
        super(message);
    }
}
