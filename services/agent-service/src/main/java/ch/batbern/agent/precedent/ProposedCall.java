package ch.batbern.agent.precedent;

import ch.batbern.agent.tools.AgentTool;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.UUID;

/**
 * A tool call the model proposed, before the gate decides on it (story 6, AD-7, AD-8). The
 * principal kind is not part of it: the retrieval reads it from the run.
 *
 * @param runId the run in {@code agent_runs} that proposes the call
 * @param tool  the registry's tool the model chose
 * @param input the input the model chose
 */
public record ProposedCall(UUID runId, AgentTool tool, JsonNode input) {

    public ProposedCall {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(input, "input");
    }
}
