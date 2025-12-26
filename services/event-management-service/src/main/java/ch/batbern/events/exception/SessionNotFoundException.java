package ch.batbern.events.exception;

/**
 * Exception thrown when a session cannot be found
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(String sessionSlug) {
        super("Session not found: " + sessionSlug);
    }

    public SessionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
