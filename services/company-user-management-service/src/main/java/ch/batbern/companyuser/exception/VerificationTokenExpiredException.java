package ch.batbern.companyuser.exception;

/**
 * Additional-email verification (v2) — the verification token has expired
 * (issued more than the configured TTL ago, default 48h). Maps to HTTP 400 with
 * error code {@code TOKEN_EXPIRED}. Never a 500.
 */
public class VerificationTokenExpiredException extends RuntimeException {
    public VerificationTokenExpiredException() {
        super("Verification token has expired");
    }
}
