package ch.batbern.companyuser.exception;

/**
 * Story 10.32 — no matching additional email on the caller's profile.
 * Maps to HTTP 404.
 */
public class AdditionalEmailNotFoundException extends RuntimeException {
    public AdditionalEmailNotFoundException(String email) {
        super("Additional email not found on this profile: " + email);
    }
}
