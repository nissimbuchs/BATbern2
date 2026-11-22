package ch.batbern.agent.tools;

import ch.batbern.agent.gateway.Operation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A tool that exists only in tests, to prove the class check (the epic ships read tools only). It
 * declares whatever the test plants; running it is a test error.
 */
record FixtureTool(String name, OperationClass operationClass, Set<Operation> operations,
                   Optional<Operation> undoOperation) implements AgentTool {

    private static final ObjectMapper JSON = new ObjectMapper();

    static FixtureTool of(String name, OperationClass declared, String... operations) {
        return new FixtureTool(name, declared, operations(operations), Optional.empty());
    }

    FixtureTool undoneBy(String operation) {
        return new FixtureTool(name, operationClass, operations, Optional.of(Operation.of(operation)));
    }

    private static Set<Operation> operations(String... operations) {
        return Arrays.stream(operations).map(Operation::of).collect(Collectors.toSet());
    }

    @Override
    public String description() {
        return "Fixture " + name;
    }

    @Override
    public JsonNode inputSchema() {
        return JSON.createObjectNode().put("type", "object");
    }

    @Override
    public ToolOutcome execute(JsonNode input, ToolContext context) {
        throw new AssertionError("A fixture tool is never run");
    }
}
