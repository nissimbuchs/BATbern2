package ch.batbern.agent.loop;

import ch.batbern.agent.log.InboundMessage;
import ch.batbern.agent.run.RunTrigger;
import ch.batbern.agent.tools.AgentTool;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What one run of the loop is given. Story 5 brings the tool registry; until then the caller hands
 * the run its tools.
 *
 * @param trigger     what started the run
 * @param system      the system prompt
 * @param userMessage the first user message
 * @param tools       the tools the model may choose, names unique
 * @param inbound     the message that started the run, logged in full; {@code null} when there is none
 */
public record AgentRunRequest(RunTrigger trigger, String system, String userMessage, List<AgentTool> tools,
                              InboundMessage inbound) {

    public AgentRunRequest {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(userMessage, "userMessage");
        tools = List.copyOf(tools);
        Set<String> names = new HashSet<>();
        for (AgentTool tool : tools) {
            if (!names.add(tool.name())) {
                throw new IllegalArgumentException("Tool names must be unique in a run: " + tool.name());
            }
        }
    }

    /** A run without an inbound message. */
    public AgentRunRequest(RunTrigger trigger, String system, String userMessage, List<AgentTool> tools) {
        this(trigger, system, userMessage, tools, null);
    }
}
