package ch.batbern.agent.loop;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * One content block of a message, as the Messages API defines it for tool use. The thinking blocks
 * come back from the model and go back to it unchanged on the next request (story 3): the history
 * stays append-only, so the model's thinking stays valid.
 */
public sealed interface LlmContent permits LlmContent.Text, LlmContent.Thinking, LlmContent.RedactedThinking,
        LlmContent.ToolUse, LlmContent.ToolResult {

    /** A {@code text} block. */
    record Text(String text) implements LlmContent {
        public Text {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * A {@code thinking} block of the model. With the default display the text is empty; the
     * signature is what the API checks when the block comes back.
     *
     * @param thinking  the thinking text, possibly empty
     * @param signature the API's signature of the block
     */
    record Thinking(String thinking, String signature) implements LlmContent {
        public Thinking {
            Objects.requireNonNull(thinking, "thinking");
            Objects.requireNonNull(signature, "signature");
        }
    }

    /**
     * A {@code redacted_thinking} block: thinking the API returns encrypted.
     *
     * @param data the encrypted thinking, sent back as received
     */
    record RedactedThinking(String data) implements LlmContent {
        public RedactedThinking {
            Objects.requireNonNull(data, "data");
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
