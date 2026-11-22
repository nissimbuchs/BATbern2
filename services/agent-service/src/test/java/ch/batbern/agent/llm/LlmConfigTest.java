package ch.batbern.agent.llm;

import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.loop.LlmMessage;
import ch.batbern.agent.loop.LlmPort;
import ch.batbern.agent.loop.LlmRequest;
import ch.batbern.agent.loop.LlmResponse;
import ch.batbern.agent.loop.LlmStopReason;
import ch.batbern.agent.loop.UnavailableLlm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The adapter is picked once at startup by the key and the profile (R3): key → Anthropic, no key and
 * profile local → the canned local adapter, otherwise none (runs handed over). The startup line names
 * the adapter and the model, never the key. No test opens a connection: building the Anthropic client
 * sends nothing.
 */
@DisplayName("LlmConfig picks the model adapter")
@ExtendWith(OutputCaptureExtension.class)
class LlmConfigTest {

    private static final String KEY = "sk-ant-config-test-secret-7731";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class, LlmConfig.class);

    @Test
    @DisplayName("key set: AnthropicLlm with the configured model; the log names adapter and model, not the key")
    void should_pickAnthropic_when_keySet(CapturedOutput output) {
        runner.withPropertyValues("agent.anthropic.api-key=" + KEY, "agent.anthropic.model=claude-sonnet-5-5")
                .run(context -> {
                    assertThat(context.getBean(LlmPort.class)).isInstanceOf(AnthropicLlm.class);
                    assertThat(((AnthropicLlm) context.getBean(LlmPort.class)).model()).isEqualTo("claude-sonnet-5-5");
                });

        // Positive control: the line is captured, so the absence of the key below means something
        assertThat(output).contains("Model adapter anthropic, model claude-sonnet-5-5");
        assertThat(output).doesNotContain(KEY).doesNotContain("config-test-secret");
    }

    @Test
    @DisplayName("key set in local profile: still AnthropicLlm")
    void should_pickAnthropic_when_keySetInLocalProfile() {
        runner.withPropertyValues("agent.anthropic.api-key=" + KEY, "spring.profiles.active=local")
                .run(context -> assertThat(context.getBean(LlmPort.class)).isInstanceOf(AnthropicLlm.class));
    }

    @Test
    @DisplayName("key none: UnavailableLlm, the service starts, the log says adapter none")
    void should_pickNone_when_keyNone(CapturedOutput output) {
        runner.withPropertyValues("agent.anthropic.api-key=none").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LlmPort.class)).isInstanceOf(UnavailableLlm.class);
        });

        assertThat(output).contains("Model adapter none, model none");
    }

    @Test
    @DisplayName("key blank, NONE with spaces, or not set: UnavailableLlm")
    void should_pickNone_when_keyBlankOrNoneInAnyCase() {
        for (String key : List.of("", "   ", " NONE ", "None")) {
            runner.withPropertyValues("agent.anthropic.api-key=" + key).run(context ->
                    assertThat(context.getBean(LlmPort.class)).as("key '%s'", key).isInstanceOf(UnavailableLlm.class));
        }
        runner.run(context -> assertThat(context.getBean(LlmPort.class)).isInstanceOf(UnavailableLlm.class));
    }

    @Test
    @DisplayName("key none, profile local: the canned local adapter ends the turn")
    void should_pickLocalDev_when_keyNoneInLocalProfile(CapturedOutput output) {
        runner.withPropertyValues("agent.anthropic.api-key=none", "spring.profiles.active=local").run(context -> {
            LlmPort port = context.getBean(LlmPort.class);
            assertThat(port).isInstanceOf(LocalDevLlm.class);
            LlmResponse response = port.complete(new LlmRequest("s", List.of(LlmMessage.user("hi")), List.of(), 16));
            assertThat(response.stopReason()).isEqualTo(LlmStopReason.END_TURN);
            assertThat(response.model()).isEqualTo(LocalDevLlm.MODEL);
            assertThat(response.toolUses()).isEmpty();
        });

        assertThat(output).contains("Model adapter local-scripted, model local-scripted");
    }

    @EnableConfigurationProperties(AgentProperties.class)
    static class PropertiesConfig {
    }
}
