package ch.batbern.agent.gateway;

/**
 * What the public API answered a tool: the status and the body as sent ({@code null} for none).
 * An HTTP error is an answer too; the tool decides what it means.
 *
 * @param status the HTTP status
 * @param body   the response body, or {@code null}
 */
public record ApiResponse(int status, String body) {

    /** Whether the status is 2xx. */
    public boolean ok() {
        return status >= 200 && status < 300;
    }
}
