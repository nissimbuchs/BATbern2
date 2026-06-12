package ch.batbern.events.exception;

/**
 * Story 7.5 "The Apéro Continues": thrown when someone tries to post to a Q&A window that has
 * already frozen (closed). Rejected as HTTP 409 Conflict by {@code GlobalExceptionHandler} with
 * {@code details.code = "QNA_WINDOW_FROZEN"}; no post is created (AC5).
 */
public class QnaWindowFrozenException extends RuntimeException {

    public QnaWindowFrozenException(String message) {
        super(message);
    }
}
