package ch.batbern.agent.loop;

import ch.batbern.agent.gateway.Operation;
import ch.batbern.agent.tools.AgentTool;
import ch.batbern.agent.tools.OperationClass;
import ch.batbern.agent.tools.ToolContext;
import ch.batbern.agent.tools.ToolOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Tools for loop tests: plain code the loop runs, recording every input it got. A tool that calls
 * nothing is {@code read}; one that calls the public API declares its class and operations, which
 * {@code TestToolRegistry} checks against its table.
 */
public final class TestTools {

    public static final ObjectMapper JSON = new ObjectMapper();

    private TestTools() {
    }

    /** A tool answering {@code answer.apply(input)}. */
    public static RecordingTool answering(String name, Function<JsonNode, String> answer) {
        return new RecordingTool(name, input -> ToolOutcome.ok(answer.apply(input)));
    }

    /** A tool that throws {@code error}. */
    public static RecordingTool throwing(String name, RuntimeException error) {
        return new RecordingTool(name, input -> {
            throw error;
        });
    }

    /** {@code {"key": value}}. */
    public static ObjectNode input(String key, String value) {
        return JSON.createObjectNode().put(key, value);
    }

    /** A tool that uses the run's public API ({@link ToolContext#api()}) for {@code operations}. */
    public static RecordingTool calling(String name, OperationClass operationClass, Set<Operation> operations,
                                        BiFunction<JsonNode, ToolContext, ToolOutcome> behaviour) {
        return new RecordingTool(name, operationClass, operations, behaviour);
    }

    public static final class RecordingTool implements AgentTool {

        private final String name;
        private final OperationClass operationClass;
        private final Set<Operation> operations;
        private final BiFunction<JsonNode, ToolContext, ToolOutcome> behaviour;
        private final List<JsonNode> inputs = new ArrayList<>();

        RecordingTool(String name, Function<JsonNode, ToolOutcome> behaviour) {
            this(name, OperationClass.READ, Set.of(), (input, context) -> behaviour.apply(input));
        }

        RecordingTool(String name, OperationClass operationClass, Set<Operation> operations,
                      BiFunction<JsonNode, ToolContext, ToolOutcome> behaviour) {
            this.name = name;
            this.operationClass = operationClass;
            this.operations = Set.copyOf(operations);
            this.behaviour = behaviour;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "Test tool " + name;
        }

        @Override
        public JsonNode inputSchema() {
            ObjectNode schema = JSON.createObjectNode().put("type", "object");
            schema.putObject("properties").putObject("query").put("type", "string");
            return schema;
        }

        @Override
        public OperationClass operationClass() {
            return operationClass;
        }

        @Override
        public Set<Operation> operations() {
            return operations;
        }

        @Override
        public ToolOutcome execute(JsonNode input, ToolContext context) {
            inputs.add(input);
            return behaviour.apply(input, context);
        }

        public List<JsonNode> inputs() {
            return List.copyOf(inputs);
        }
    }
}
