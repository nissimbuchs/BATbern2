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
 * @param loop      the agent loop's model call settings
 * @param limits    the caps and budget that stop a run, and the gate's two owner values
 * @param anthropic the Anthropic model adapter
 * @param cums      CUMS, which grants runs and mints the agent's tokens
 * @param gateway   the Spring gateway, through which tools reach the public API
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(boolean enabled, @DefaultValue Loop loop, @DefaultValue Limits limits,
                              @DefaultValue Anthropic anthropic, @DefaultValue Cums cums,
                              @DefaultValue Gateway gateway) {

    /**
     * Settings of each model call of the agent loop ({@code agent.loop.*}). The caps of a run are
     * {@link Limits}.
     *
     * @param maxOutputTokens the output token limit of each model call; adaptive thinking spends
     *                        output tokens too, so the default is the non-streaming 16000
     */
    public record Loop(@DefaultValue("16000") int maxOutputTokens) {

        public Loop {
            if (maxOutputTokens < 1) {
                throw new IllegalArgumentException("agent.loop.max-output-tokens must be at least 1");
            }
        }
    }

    /**
     * The limits of agent-service ({@code agent.limits.*}, story 8, R9, Operational envelope "Limits",
     * owner values 2026-11-22), all in one place. The four caps stop a run: reaching one ends it
     * HANDED_TO_ORGANIZERS with reason {@code CAP_REACHED} and the limit as end detail. The loop
     * checks steps, run tokens and the daily budget before every model call, against the tokens
     * already recorded in {@code agent_model_calls}; the reply cap is checked by the gate. The last
     * two values are the gate's rules (story 7).
     *
     * @param runSteps               model calls per run; a model still calling tools after the last
     *                               one stops the run ({@code STEPS})
     * @param runTokens              model tokens per run (input, output, cache creation and cache
     *                               read); no further model call once reached ({@code RUN_TOKENS})
     * @param runReplies             {@code reply} calls the gate lets run per run; the next one stops
     *                               the run ({@code REPLIES})
     * @param dailyTokens            model tokens of all runs per day (Europe/Zurich); no further model
     *                               call once reached ({@code DAILY_TOKENS})
     * @param bindingApprovals       matching approved precedents a {@code binding} call needs to run
     *                               alone; at least 1, so no binding call ever runs without one
     * @param nonOrganizerRecipients distinct non-organizer recipients a run or a day may reach before
     *                               every further mail is {@code audience}
     */
    public record Limits(@DefaultValue("25") int runSteps,
                         @DefaultValue("200000") long runTokens,
                         @DefaultValue("2") int runReplies,
                         @DefaultValue("2000000") long dailyTokens,
                         @DefaultValue("2") int bindingApprovals,
                         @DefaultValue("1") int nonOrganizerRecipients) {

        public Limits {
            atLeast(runSteps, 1, "agent.limits.run-steps");
            atLeast(runTokens, 1, "agent.limits.run-tokens");
            atLeast(runReplies, 0, "agent.limits.run-replies");
            atLeast(dailyTokens, 1, "agent.limits.daily-tokens");
            atLeast(bindingApprovals, 1, "agent.limits.binding-approvals");
            atLeast(nonOrganizerRecipients, 0, "agent.limits.non-organizer-recipients");
        }

        private static void atLeast(long value, long minimum, String property) {
            if (value < minimum) {
                throw new IllegalArgumentException(property + " must be at least " + minimum + ", was " + value);
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

    /**
     * CUMS as the agent's identity provider ({@code agent.cums.*}, story 4, R2, AD-3). agent-service
     * calls exactly {@code POST /internal/agent-grants} and {@code POST /internal/agent-tokens},
     * presenting its caller secret (Secrets Manager, {@code AGENT_CUMS_CALLER_SECRET}). Blank or
     * {@code none} (any case) means no secret: every run is handed to the organizers
     * (CALLER_SECRET_MISSING) and CUMS is not called. {@link #toString()} never shows the secret.
     *
     * @param baseUrl      CUMS's base URL ({@code http://company-user-management:8080})
     * @param callerSecret agent-service's caller secret, or blank
     */
    public record Cums(@DefaultValue("http://company-user-management:8080") String baseUrl,
                       @DefaultValue("") String callerSecret) {

        public Cums {
            baseUrl = trimBaseUrl(baseUrl, "agent.cums.base-url");
        }

        /** Whether a caller secret is installed: not blank and not {@code none} (trimmed, any case). */
        public boolean hasCallerSecret() {
            return callerSecret != null && !callerSecret.isBlank() && !"none".equalsIgnoreCase(callerSecret.trim());
        }

        /** The caller secret, trimmed; only for the CUMS client. */
        public String secret() {
            if (!hasCallerSecret()) {
                throw new IllegalStateException("No CUMS caller secret is installed");
            }
            return callerSecret.trim();
        }

        @Override
        public String toString() {
            return "Cums[baseUrl=" + baseUrl + ", callerSecret=" + (hasCallerSecret() ? "****" : "none") + "]";
        }
    }

    /**
     * The Spring gateway ({@code agent.gateway.*}, story 4): every tool call to the public API goes
     * there with the run's minted token, never to a service directly.
     *
     * @param baseUrl the gateway's base URL without {@code /api/v1} ({@code http://api-gateway:8080})
     */
    public record Gateway(@DefaultValue("http://api-gateway:8080") String baseUrl) {

        public Gateway {
            baseUrl = trimBaseUrl(baseUrl, "agent.gateway.base-url");
        }
    }

    private static String trimBaseUrl(String baseUrl, String property) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
