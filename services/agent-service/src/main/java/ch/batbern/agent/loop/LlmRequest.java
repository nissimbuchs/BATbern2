package ch.batbern.agent.loop;

import java.util.List;
import java.util.Objects;

/**
 * One request to the model: the Messages API's {@code system}, {@code messages}, {@code tools} and
 * {@code max_tokens}. The model id is the adapter's configuration (story 3).
 *
 * @param system    the system prompt
 * @param messages  the conversation so far, alternating user and assistant, starting with user
 * @param tools     the tools the model may choose from
 * @param maxTokens the output token limit
 */
public record LlmRequest(String system, List<LlmMessage> messages, List<LlmToolSpec> tools, int maxTokens) {

    public LlmRequest {
        Objects.requireNonNull(system, "system");
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("A request needs at least one message");
        }
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens must be at least 1");
        }
    }
}
