package ch.batbern.companyuser.exception;

/**
 * Raised by {@code UserService.issueInvitationCredentials} when the underlying Cognito
 * user is in a state that cannot be resolved without operator intervention
 * (e.g., {@code ARCHIVED}, {@code COMPROMISED}, {@code UNKNOWN}).
 *
 * <p>Story 11.E.2 (AC2 item 6). Subclasses {@link IllegalStateException} per the
 * literal AC wording ("throw IllegalStateException with a clear message") while a
 * dedicated {@link GlobalExceptionHandler} maps this specific subclass to HTTP 422
 * Unprocessable Entity. The generic {@code IllegalStateException} handler stays at
 * HTTP 400 for unrelated callers.
 */
public class UnprocessableInvitationStateException extends IllegalStateException {

    public UnprocessableInvitationStateException(String message) {
        super(message);
    }
}
