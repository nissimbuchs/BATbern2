package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Story 10.11: Thrown when a registration cannot be found by registration code.
 * Maps to HTTP 404 via GlobalExceptionHandler (NotFoundException → 404).
 */
public class RegistrationNotFoundException extends DomainException {

    public RegistrationNotFoundException(String registrationCode) {
        super(HttpStatus.NOT_FOUND, Severity.LOW, null, "Registration with ID '" + registrationCode + "' not found");
    }
}
