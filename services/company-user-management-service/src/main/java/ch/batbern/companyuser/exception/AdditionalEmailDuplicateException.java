package ch.batbern.companyuser.exception;

/**
 * Story 10.32 — the requested additional email is already registered as a
 * primary email on {@code user_profiles} or as an additional email on any user.
 * Maps to HTTP 409 with error code {@code ADDITIONAL_EMAIL_DUPLICATE}.
 */
public class AdditionalEmailDuplicateException extends RuntimeException {
    public AdditionalEmailDuplicateException(String email) {
        super("Email '" + email + "' is already registered on another profile");
    }
}
