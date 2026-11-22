package ch.batbern.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of agent-service ({@code agent.*}).
 *
 * @param enabled the service's agent switch; {@code false} by default, so a deploy ships the
 *                service dark and nothing in it acts until the owner switches it on
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(boolean enabled) {
}
