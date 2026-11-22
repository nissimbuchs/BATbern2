package ch.batbern.agent.precedent;

import ch.batbern.agent.gateway.Operation;
import ch.batbern.agent.tools.AgentTool;
import ch.batbern.agent.tools.OperationClass;
import ch.batbern.agent.tools.PastEventQuery;
import ch.batbern.agent.tools.ToolContext;
import ch.batbern.agent.tools.ToolOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.Set;

/**
 * A {@code binding} tool that exists only in tests (the epic ships read tools only), to propose
 * calls to the precedent retrieval. With {@code topic} in its input it asks for the past events
 * matching that topic; without, for none. Running it is a test error.
 */
record PrecedentFixtureTool(String name) implements AgentTool {

    static final int PAST_EVENT_LIMIT = 5;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String description() {
        return "Fixture " + name;
    }

    @Override
    public JsonNode inputSchema() {
        return JSON.createObjectNode().put("type", "object");
    }

    @Override
    public OperationClass operationClass() {
        return OperationClass.BINDING;
    }

    @Override
    public Set<Operation> operations() {
        return Set.of(Operation.of("POST /companies"));
    }

    @Override
    public Optional<PastEventQuery> pastEventQuery(JsonNode input) {
        JsonNode topic = input.path("topic");
        return topic.isTextual() ? Optional.of(PastEventQuery.matching(topic.asText(), PAST_EVENT_LIMIT))
                : Optional.empty();
    }

    @Override
    public ToolOutcome execute(JsonNode input, ToolContext context) {
        throw new AssertionError("The precedent fixture tool is never run");
    }
}
