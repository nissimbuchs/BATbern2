package ch.batbern.companyuser.exception;

/**
 * Raised when an AWS Cognito Admin SDK call fails for a reason other than the documented
 * idempotent branches (e.g., UsernameExistsException is swallowed by
 * {@code adminCreateUserSilently}).
 *
 * <p>Story 11.E.2 (AC3/AC5). Mapped to HTTP 502 Bad Gateway by
 * {@link GlobalExceptionHandler} so cross-service callers (event-management-service)
 * see a clean upstream-failure signal rather than a 500.
 *
 * <p>Carries the failed Cognito action name and the masked email (never the full email,
 * never any password material) for log correlation.
 */
public class CognitoOperationException extends RuntimeException {

    private final String action;
    private final String maskedEmail;

    public CognitoOperationException(String action, String maskedEmail, Throwable cause) {
        super(String.format("Cognito %s failed for %s: %s",
                action, maskedEmail, cause != null ? cause.getMessage() : "unknown"), cause);
        this.action = action;
        this.maskedEmail = maskedEmail;
    }

    public String getAction() {
        return action;
    }

    public String getMaskedEmail() {
        return maskedEmail;
    }
}
