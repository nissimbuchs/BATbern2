package ch.batbern.agent.loop;

/**
 * The tokens of one model call: the Messages API's {@code usage}.
 *
 * @param inputTokens              uncached input tokens
 * @param outputTokens             output tokens
 * @param cacheCreationInputTokens input tokens written to the prompt cache
 * @param cacheReadInputTokens     input tokens read from the prompt cache
 */
public record LlmUsage(long inputTokens, long outputTokens, long cacheCreationInputTokens,
                       long cacheReadInputTokens) {
}
