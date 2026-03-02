package ch.batbern.events.exception;

import ch.batbern.shared.exception.NotFoundException;

/**
 * Story 10.11: Thrown when a registration cannot be found by registration code.
 * Maps to HTTP 404 via GlobalExceptionHandler (NotFoundException → 404).
 */
public class RegistrationNotFoundException extends NotFoundException {

    public RegistrationNotFoundException(String registrationCode) {
        super("Registration", registrationCode);
    }
}
