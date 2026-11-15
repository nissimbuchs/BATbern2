package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Story 10.11: Thrown when attempting to promote a registration that is not on the waitlist.
 * Maps to HTTP 409 through its {@link DomainException} base.
 */
public class RegistrationNotOnWaitlistException extends DomainException {

    public RegistrationNotOnWaitlistException(String registrationCode) {
        super(HttpStatus.CONFLICT, Severity.MEDIUM, null, "Registration is not on the waitlist: " + registrationCode);
    }
}
