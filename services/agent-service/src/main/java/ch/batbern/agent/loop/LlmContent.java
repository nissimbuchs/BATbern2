package ch.batbern.agent.loop;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/** One content block of a message, as the Messages API defines it for tool use. */
public sealed interface LlmContent permits LlmContent.Text, LlmContent.ToolUse, LlmContent.ToolResult {

    /** A {@code text} block. */
    record Text(String text) implements LlmContent {
        public Text {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * A {@code tool_use} block: the model asks to run a tool.
     *
     * @param id    the model's id of this call, which the tool result names
     * @param name  the tool's name
     * @param input the tool's input, a JSON object
     */
    record ToolUse(String id, String name, JsonNode input) implements LlmContent {
        public ToolUse {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(input, "input");
        }
    }

    /**
     * A {@code tool_result} block: the answer to one tool_use.
     *
     * @param toolUseId the id of the tool_use it answers
     * @param content   the tool's output, or what went wrong
     * @param isError   whether the call failed
     */
    record ToolResult(String toolUseId, String content, boolean isError) implements LlmContent {
        public ToolResult {
            Objects.requireNonNull(toolUseId, "toolUseId");
            Objects.requireNonNull(content, "content");
        }
    }
}
