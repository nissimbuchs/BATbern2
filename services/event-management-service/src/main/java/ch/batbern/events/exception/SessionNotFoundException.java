package ch.batbern.events.exception;

/**
 * Exception thrown when a session cannot be found
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(String sessionSlug) {
        super("Session not found: " + sessionSlug);
    }

    /**
     * W4.2 review fix item 3: includes eventCode so multi-event debugging is faster.
     */
    public SessionNotFoundException(String sessionSlug, String eventCode) {
        super(String.format("sessionSlug '%s' not found in event '%s'", sessionSlug, eventCode));
    }

    public SessionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
