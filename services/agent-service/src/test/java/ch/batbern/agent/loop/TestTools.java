package ch.batbern.agent.loop;

import ch.batbern.agent.tools.AgentTool;
import ch.batbern.agent.tools.ToolContext;
import ch.batbern.agent.tools.ToolOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Tools for loop tests: plain code the loop runs, recording every input it got. */
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

    /** A tool that uses the run's public API ({@link ToolContext#api()}). */
    public static RecordingTool calling(String name, BiFunction<JsonNode, ToolContext, ToolOutcome> behaviour) {
        return new RecordingTool(name, behaviour);
    }

    public static final class RecordingTool implements AgentTool {

        private final String name;
        private final BiFunction<JsonNode, ToolContext, ToolOutcome> behaviour;
        private final List<JsonNode> inputs = new ArrayList<>();

        RecordingTool(String name, Function<JsonNode, ToolOutcome> behaviour) {
            this(name, (input, context) -> behaviour.apply(input));
        }

        RecordingTool(String name, BiFunction<JsonNode, ToolContext, ToolOutcome> behaviour) {
            this.name = name;
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
        public ToolOutcome execute(JsonNode input, ToolContext context) {
            inputs.add(input);
            return behaviour.apply(input, context);
        }

        public List<JsonNode> inputs() {
            return List.copyOf(inputs);
        }
    }
}
