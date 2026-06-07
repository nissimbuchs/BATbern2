package ch.batbern.companyuser.exception;

/**
 * Additional-email verification (v2) — the verification token is malformed,
 * has a bad signature, or carries the wrong type. Maps to HTTP 400 with error
 * code {@code TOKEN_INVALID}. Never a 500.
 */
public class VerificationTokenInvalidException extends RuntimeException {
    public VerificationTokenInvalidException() {
        super("Verification token is invalid");
    }
}
