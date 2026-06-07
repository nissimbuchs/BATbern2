package ch.batbern.companyuser.exception;

/**
 * Additional-email verification (v2) — resend was requested for an email that is
 * already verified. Maps to HTTP 409 with error code {@code ALREADY_VERIFIED}.
 */
public class AdditionalEmailAlreadyVerifiedException extends RuntimeException {
    public AdditionalEmailAlreadyVerifiedException(String email) {
        super("Additional email is already verified: " + email);
    }
}
