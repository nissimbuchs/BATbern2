package ch.batbern.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of agent-service ({@code agent.*}).
 *
 * @param enabled the service's agent switch; {@code false} by default, so a deploy ships the
 *                service dark and nothing in it acts until the owner switches it on
 * @param loop    the agent loop's limits
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(boolean enabled, @DefaultValue Loop loop) {

    /**
     * Limits of one run of the agent loop ({@code agent.loop.*}). Story 8 adds the token, reply and
     * daily caps.
     *
     * @param maxSteps        model calls per run; a model still calling tools after the last one
     *                        hands the run to the organizers (STEP_LIMIT)
     * @param maxOutputTokens the output token limit of each model call
     */
    public record Loop(@DefaultValue("25") int maxSteps, @DefaultValue("4096") int maxOutputTokens) {

        public Loop {
            if (maxSteps < 1) {
                throw new IllegalArgumentException("agent.loop.max-steps must be at least 1");
            }
            if (maxOutputTokens < 1) {
                throw new IllegalArgumentException("agent.loop.max-output-tokens must be at least 1");
            }
        }
    }
}
