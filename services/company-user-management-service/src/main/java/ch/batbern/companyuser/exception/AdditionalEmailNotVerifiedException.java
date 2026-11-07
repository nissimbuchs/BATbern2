package ch.batbern.companyuser.exception;

/**
 * #1244 — a setting that needs a confirmed address (copy of event mails) was requested for an
 * additional address that is not confirmed yet. Maps to HTTP 409 with error code
 * {@code NOT_VERIFIED}.
 */
public class AdditionalEmailNotVerifiedException extends RuntimeException {
    public AdditionalEmailNotVerifiedException(String email) {
        super("Additional email is not confirmed yet: " + email);
    }
}
