package ch.batbern.agent.loop;

import java.util.List;
import java.util.Objects;

/**
 * One message of the conversation: the Messages API's {@code role} and {@code content} blocks.
 *
 * @param role    who wrote it
 * @param content its blocks: text, tool_use (assistant) or tool_result (user)
 */
public record LlmMessage(Role role, List<LlmContent> content) {

    /** The Messages API roles. */
    public enum Role {
        USER,
        ASSISTANT
    }

    public LlmMessage {
        Objects.requireNonNull(role, "role");
        content = List.copyOf(content);
        if (content.isEmpty()) {
            throw new IllegalArgumentException("A message needs at least one content block");
        }
    }

    /** A user message of one text block. */
    public static LlmMessage user(String text) {
        return new LlmMessage(Role.USER, List.of(new LlmContent.Text(text)));
    }

    /** A user message of blocks, such as the tool results of one step. */
    public static LlmMessage user(List<? extends LlmContent> content) {
        return new LlmMessage(Role.USER, List.copyOf(content));
    }

    /** An assistant message, as the model answered it. */
    public static LlmMessage assistant(List<? extends LlmContent> content) {
        return new LlmMessage(Role.ASSISTANT, List.copyOf(content));
    }
}
