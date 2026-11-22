package ch.batbern.agent.loop;

import java.util.List;
import java.util.Objects;

/**
 * The model's answer to one request.
 *
 * @param id         the model's message id
 * @param model      the model id that answered
 * @param content    the answer's blocks: text and tool_use
 * @param stopReason why the model stopped
 * @param usage      the tokens of the call
 */
public record LlmResponse(String id, String model, List<LlmContent> content, LlmStopReason stopReason,
                          LlmUsage usage) {

    public LlmResponse {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(stopReason, "stopReason");
        Objects.requireNonNull(usage, "usage");
        content = List.copyOf(content);
    }

    /** The tool_use blocks of the answer, in order. */
    public List<LlmContent.ToolUse> toolUses() {
        return content.stream()
                .filter(LlmContent.ToolUse.class::isInstance)
                .map(LlmContent.ToolUse.class::cast)
                .toList();
    }
}
