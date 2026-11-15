package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Story 7.5 "The Apéro Continues": thrown when someone tries to post to a Q&A window that has
 * already frozen (closed). Rejected as HTTP 409 Conflict by {@code GlobalExceptionHandler} with
 * {@code details.code = "QNA_WINDOW_FROZEN"}; no post is created (AC5).
 */
public class QnaWindowFrozenException extends DomainException {

    static final String CODE = "QNA_WINDOW_FROZEN";

    public QnaWindowFrozenException(String message) {
        super(HttpStatus.CONFLICT, Severity.LOW, CODE, message);
    }
}
