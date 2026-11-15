package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Base of every EMS domain exception (Story 17.14). It carries what the error response needs: the
 * HTTP status, the severity and an optional machine-readable code, rendered as {@code details.code}.
 *
 * <p>{@code GlobalExceptionHandler} maps this type once ({@link DomainErrorResponse}), so a new
 * exception needs no handler. An exception whose body carries more than the code (a deadline, a
 * count) gets a handler in its module's own advice, {@code @Order(HIGHEST_PRECEDENCE)}, which builds
 * the body with {@link DomainErrorResponse} too (model: {@code mailcenter/MailCenterExceptionHandler}).
 */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final Severity severity;
    private final String code;

    protected DomainException(HttpStatus status, Severity severity, String code, String message) {
        super(message);
        this.status = status;
        this.severity = severity;
        this.code = code;
    }

    protected DomainException(HttpStatus status, Severity severity, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.severity = severity;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Severity getSeverity() {
        return severity;
    }

    /** Machine-readable code for {@code details.code}, or {@code null} when the response has none. */
    public String getCode() {
        return code;
    }
}
