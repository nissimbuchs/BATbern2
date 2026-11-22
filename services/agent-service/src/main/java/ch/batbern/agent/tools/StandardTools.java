package ch.batbern.agent.tools;

import ch.batbern.agent.tools.read.FindPastEventsTool;
import ch.batbern.agent.tools.read.ListEventSessionsTool;
import ch.batbern.agent.tools.read.ListSessionSpeakersTool;

import java.time.Clock;
import java.util.List;

/**
 * Every tool agent-service ships, in the order the model is offered them. A tool not listed here is
 * not offered; a test fails for a concrete {@link AgentTool} in main sources that is missing here,
 * so none escapes the class check.
 */
public final class StandardTools {

    private StandardTools() {
    }

    /** The shipped tools. */
    public static List<AgentTool> create(Clock clock) {
        return List.of(
                new FindPastEventsTool(clock),
                new ListEventSessionsTool(),
                new ListSessionSpeakersTool());
    }
}
