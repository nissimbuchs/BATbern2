package ch.batbern.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Configuration of agent-service ({@code agent.*}).
 *
 * @param enabled   the service's agent switch; {@code false} by default, so a deploy ships the
 *                  service dark and nothing in it acts until the owner switches it on
 * @param loop      the agent loop's limits
 * @param anthropic the Anthropic model adapter
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(boolean enabled, @DefaultValue Loop loop, @DefaultValue Anthropic anthropic) {

    /**
     * Limits of one run of the agent loop ({@code agent.loop.*}). Story 8 adds the token, reply and
     * daily caps.
     *
     * @param maxSteps        model calls per run; a model still calling tools after the last one
     *                        hands the run to the organizers (STEP_LIMIT)
     * @param maxOutputTokens the output token limit of each model call; adaptive thinking spends
     *                        output tokens too, so the default is the non-streaming 16000
     */
    public record Loop(@DefaultValue("25") int maxSteps, @DefaultValue("16000") int maxOutputTokens) {

        public Loop {
            if (maxSteps < 1) {
                throw new IllegalArgumentException("agent.loop.max-steps must be at least 1");
            }
            if (maxOutputTokens < 1) {
                throw new IllegalArgumentException("agent.loop.max-output-tokens must be at least 1");
            }
        }
    }

    /**
     * The Anthropic adapter ({@code agent.anthropic.*}, story 3, AD-14). The key comes from the
     * Secrets Manager secret through {@code AGENT_ANTHROPIC_API_KEY}; blank or {@code none} (any case)
     * means no key, and the service still starts. {@link #toString()} never shows the key.
     *
     * @param apiKey     the API key, or {@code none}
     * @param model      the model id sent with every request
     * @param effort     {@code output_config.effort}: low, medium, high, xhigh or max
     * @param timeout    the request timeout of one call
     * @param maxRetries the SDK's transport retries on 408, 409, 429, 5xx and connection errors;
     *                   the loop itself never retries
     * @param baseUrl    the API's base URL; blank means the SDK's default (api.anthropic.com).
     *                   Tests point it at a local server
     */
    public record Anthropic(@DefaultValue("none") String apiKey,
                            @DefaultValue("claude-sonnet-5-5") String model,
                            @DefaultValue("medium") String effort,
                            @DefaultValue("5m") Duration timeout,
                            @DefaultValue("2") int maxRetries,
                            @DefaultValue("") String baseUrl) {

        private static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");

        public Anthropic {
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("agent.anthropic.model must not be blank");
            }
            model = model.trim();
            effort = effort == null ? "" : effort.trim().toLowerCase(Locale.ROOT);
            if (!EFFORTS.contains(effort)) {
                // The value is configuration, not a secret: naming it helps the owner fix it
                throw new IllegalArgumentException("agent.anthropic.effort must be one of " + EFFORTS
                        + ", was '" + effort + "'");
            }
            if (timeout == null || timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("agent.anthropic.timeout must be positive");
            }
            if (maxRetries < 0) {
                throw new IllegalArgumentException("agent.anthropic.max-retries must not be negative");
            }
            baseUrl = baseUrl == null ? "" : baseUrl.trim();
        }

        /** Whether a key is installed: not blank and not {@code none} (trimmed, any case). */
        public boolean hasKey() {
            return apiKey != null && !apiKey.isBlank() && !"none".equalsIgnoreCase(apiKey.trim());
        }

        /** The key, trimmed; only for the client. */
        public String key() {
            if (!hasKey()) {
                throw new IllegalStateException("No Anthropic API key is installed");
            }
            return apiKey.trim();
        }

        @Override
        public String toString() {
            return "Anthropic[apiKey=" + (hasKey() ? "****" : "none") + ", model=" + model + ", effort=" + effort
                    + ", timeout=" + timeout + ", maxRetries=" + maxRetries
                    + ", baseUrl=" + (baseUrl.isEmpty() ? "default" : baseUrl) + "]";
        }
    }
}
