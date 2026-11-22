package ch.batbern.agent.loop;

/**
 * The model could not be reached, or answered with an error. The message is for the decision log
 * only; the application log names the class (#1202).
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
