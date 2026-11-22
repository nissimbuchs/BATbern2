package ch.batbern.agent.loop;

import ch.batbern.agent.tools.AgentTool;
import ch.batbern.agent.tools.ToolOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Tools for loop tests: plain code the loop runs, recording every input it got. */
final class TestTools {

    static final ObjectMapper JSON = new ObjectMapper();

    private TestTools() {
    }

    /** A tool answering {@code answer.apply(input)}. */
    static RecordingTool answering(String name, Function<JsonNode, String> answer) {
        return new RecordingTool(name, input -> ToolOutcome.ok(answer.apply(input)));
    }

    /** A tool that throws {@code error}. */
    static RecordingTool throwing(String name, RuntimeException error) {
        return new RecordingTool(name, input -> {
            throw error;
        });
    }

    /** {@code {"key": value}}. */
    static ObjectNode input(String key, String value) {
        return JSON.createObjectNode().put(key, value);
    }

    static final class RecordingTool implements AgentTool {

        private final String name;
        private final Function<JsonNode, ToolOutcome> behaviour;
        private final List<JsonNode> inputs = new ArrayList<>();

        RecordingTool(String name, Function<JsonNode, ToolOutcome> behaviour) {
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
        public ToolOutcome execute(JsonNode input) {
            inputs.add(input);
            return behaviour.apply(input);
        }

        List<JsonNode> inputs() {
            return List.copyOf(inputs);
        }
    }
}
