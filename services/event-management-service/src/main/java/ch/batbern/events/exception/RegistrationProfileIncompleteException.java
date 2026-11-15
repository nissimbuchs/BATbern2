package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Thrown by the authenticated quick-register flow when the user's profile is
 * missing first or last name.
 *
 * The public anonymous registration form enforces firstName/lastName at the
 * input boundary. The authenticated path historically trusted whatever was on
 * the {@code user_profiles} row — which produced empty attendee snapshots for
 * users created via JIT/email-fallback or pre-fix paths (2026-05-18 incident:
 * 20 BATbern59 registrations with blank attendee names).
 *
 * The frontend reads {@code code = "profile_incomplete"} on the 409 response
 * and prompts the user inline to complete their profile before retrying.
 */
public class RegistrationProfileIncompleteException extends DomainException {

    static final String CODE = "profile_incomplete";

    private final String username;
    private final boolean missingFirstName;
    private final boolean missingLastName;

    public RegistrationProfileIncompleteException(
            String username, boolean missingFirstName, boolean missingLastName) {
        super(HttpStatus.CONFLICT, Severity.LOW, CODE, "Profile incomplete for user " + username
                + " (missing"
                + (missingFirstName ? " firstName" : "")
                + (missingLastName ? " lastName" : "")
                + ")");
        this.username = username;
        this.missingFirstName = missingFirstName;
        this.missingLastName = missingLastName;
    }

    public String getUsername() {
        return username;
    }

    public boolean isMissingFirstName() {
        return missingFirstName;
    }

    public boolean isMissingLastName() {
        return missingLastName;
    }
}
