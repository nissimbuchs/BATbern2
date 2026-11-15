package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * A confirmation link of a registration that is no longer pending: cancelled, on the waitlist,
 * attended or no-show (#1315). The old link must not revive it. Mapped to HTTP 409 with
 * {@code details.code = "REGISTRATION_ALREADY_PROCESSED"} through its {@link DomainException} base.
 */
public class RegistrationAlreadyProcessedException extends DomainException {

    static final String CODE = "REGISTRATION_ALREADY_PROCESSED";

    public RegistrationAlreadyProcessedException(String registrationCode, String status) {
        super(HttpStatus.CONFLICT, Severity.LOW, CODE,
                "Registration " + registrationCode + " is " + status + " and can no longer be confirmed");
    }
}
