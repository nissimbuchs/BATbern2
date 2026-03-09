package ch.batbern.events.exception;

/**
 * Story 10.11: Thrown when attempting to promote a registration that is not on the waitlist.
 * Maps to HTTP 409 via GlobalExceptionHandler (IllegalStateException → 409).
 */
public class RegistrationNotOnWaitlistException extends IllegalStateException {

    public RegistrationNotOnWaitlistException(String registrationCode) {
        super("Registration is not on the waitlist: " + registrationCode);
    }
}
