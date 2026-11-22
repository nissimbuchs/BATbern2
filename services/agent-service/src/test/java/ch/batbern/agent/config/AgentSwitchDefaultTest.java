package ch.batbern.agent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The service ships dark (R1): {@code agent.enabled} is false unless {@code AGENT_ENABLED} says
 * otherwise, read from the production {@code application.yml}, not from a test default.
 */
@DisplayName("agent.enabled is off by default")
class AgentSwitchDefaultTest {

    @Test
    @DisplayName("should_beOff_when_noEnvironmentOverride")
    void should_beOff_when_noEnvironmentOverride() throws IOException {
        runnerWithProductionYaml().run(context ->
                assertThat(context.getBean(AgentProperties.class).enabled()).isFalse());
    }

    @Test
    @DisplayName("should_beOn_when_environmentSwitchesItOn")
    void should_beOn_when_environmentSwitchesItOn() throws IOException {
        runnerWithProductionYaml().withSystemProperties("AGENT_ENABLED=true").run(context ->
                assertThat(context.getBean(AgentProperties.class).enabled()).isTrue());
    }

    @Test
    @DisplayName("should_haveNoKeyAndSonnet_when_noEnvironmentOverride")
    void should_haveNoKeyAndSonnet_when_noEnvironmentOverride() throws IOException {
        runnerWithProductionYaml().run(context -> {
            AgentProperties properties = context.getBean(AgentProperties.class);
            assertThat(properties.anthropic().hasKey()).isFalse();
            assertThat(properties.anthropic().model()).isEqualTo("claude-sonnet-5-5");
            assertThat(properties.anthropic().effort()).isEqualTo("medium");
            assertThat(properties.anthropic().baseUrl()).isEmpty();
            assertThat(properties.loop().maxOutputTokens()).isEqualTo(16000);
        });
    }

    @Test
    @DisplayName("should_readKey_when_environmentInstallsIt")
    void should_readKey_when_environmentInstallsIt() throws IOException {
        runnerWithProductionYaml().withSystemProperties("AGENT_ANTHROPIC_API_KEY=sk-ant-env-test").run(context ->
                assertThat(context.getBean(AgentProperties.class).anthropic().key()).isEqualTo("sk-ant-env-test"));
    }

    private static ApplicationContextRunner runnerWithProductionYaml() throws IOException {
        // The first document of application.yml is the base every profile starts from
        List<PropertySource<?>> documents = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addLast(documents.get(0)))
                .withUserConfiguration(PropertiesConfig.class);
    }

    @EnableConfigurationProperties(AgentProperties.class)
    static class PropertiesConfig {
    }
}
