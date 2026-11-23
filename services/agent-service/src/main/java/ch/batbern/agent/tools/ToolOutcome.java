package ch.batbern.agent.tools;

import java.util.Objects;

/**
 * What a tool hands back to the model.
 *
 * @param content the text the model reads
 * @param error   whether the tool reports a failure the model should see as such (an error
 *                tool_result); a tool that throws is a failure too, logged as FAILED
 */
public record ToolOutcome(String content, boolean error) {

    public ToolOutcome {
        Objects.requireNonNull(content, "content");
    }

    /** A result. */
    public static ToolOutcome ok(String content) {
        return new ToolOutcome(content, false);
    }

    /** A failure the tool reports itself. */
    public static ToolOutcome error(String content) {
        return new ToolOutcome(content, true);
    }
}
