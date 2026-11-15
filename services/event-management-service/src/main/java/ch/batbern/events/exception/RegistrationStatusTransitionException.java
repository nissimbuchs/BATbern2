package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * An organizer status change that the transition table of {@code RegistrationService.changeStatus}
 * does not allow, for example {@code cancelled} to {@code confirmed} or anything to {@code waitlist}
 * (#1315). Mapped to HTTP 409 with {@code details.code = "REGISTRATION_TRANSITION_NOT_ALLOWED"}
 * through its {@link DomainException} base.
 */
public class RegistrationStatusTransitionException extends DomainException {

    static final String CODE = "REGISTRATION_TRANSITION_NOT_ALLOWED";

    public RegistrationStatusTransitionException(String registrationCode, String from, String to) {
        super(HttpStatus.CONFLICT, Severity.LOW, CODE,
                "Registration " + registrationCode + " cannot change from " + from + " to " + to);
    }
}
