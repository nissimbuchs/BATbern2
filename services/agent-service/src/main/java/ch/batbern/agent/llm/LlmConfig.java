package ch.batbern.agent.llm;

import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.loop.LlmPort;
import ch.batbern.agent.loop.UnavailableLlm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Picks the model adapter once at startup (story 3, AD-14), in one place:
 * <ul>
 *   <li>a key is installed ({@code agent.anthropic.api-key} neither blank nor {@code none}):
 *       {@link AnthropicLlm} with the configured model;</li>
 *   <li>no key, profile {@code local}: {@link LocalDevLlm}, a canned answer;</li>
 *   <li>otherwise: {@link UnavailableLlm}, every run is handed to the organizers.</li>
 * </ul>
 * It logs one line naming the adapter and the model, never the key. In tests the scripted model is
 * {@code @Primary} and answers instead.
 */
@Configuration
class LlmConfig {

    private static final Logger LOG = LoggerFactory.getLogger(LlmConfig.class);

    static final String ANTHROPIC = "anthropic";
    static final String NONE = "none";

    @Bean
    LlmPort llmPort(AgentProperties properties, Environment environment) {
        AgentProperties.Anthropic anthropic = properties.anthropic();
        if (anthropic.hasKey()) {
            AnthropicLlm llm = AnthropicLlm.create(anthropic);
            LOG.info("Model adapter {}, model {}, effort {}", ANTHROPIC, llm.model(), anthropic.effort());
            return llm;
        }
        if (environment.acceptsProfiles(Profiles.of("local"))) {
            LOG.info("Model adapter {}, model {}: no key, profile local", LocalDevLlm.MODEL, LocalDevLlm.MODEL);
            return new LocalDevLlm();
        }
        LOG.info("Model adapter {}, model {}: no key installed, runs are handed to the organizers", NONE, NONE);
        return new UnavailableLlm();
    }
}
