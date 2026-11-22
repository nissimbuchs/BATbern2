package ch.batbern.agent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The adapter's configuration: what counts as a key, and a toString that never shows it. */
@DisplayName("AgentProperties.Anthropic")
class AgentPropertiesTest {

    private static final String KEY = "sk-ant-properties-test-secret-1234";

    private static AgentProperties.Anthropic anthropic(String key) {
        return new AgentProperties.Anthropic(key, "claude-sonnet-5-5", "medium", Duration.ofMinutes(5), 2, "");
    }

    @Test
    @DisplayName("toString masks the key, of the nested record and of the whole configuration")
    void should_maskKey_when_toString() {
        AgentProperties.Anthropic anthropic = anthropic(KEY);
        AgentProperties properties = new AgentProperties(false, new AgentProperties.Loop(25, 16000), anthropic);

        assertThat(anthropic.toString()).doesNotContain(KEY).doesNotContain("secret")
                .contains("apiKey=****").contains("model=claude-sonnet-5-5");
        assertThat(properties.toString()).doesNotContain(KEY).contains("apiKey=****");
        assertThat(anthropic("none").toString()).contains("apiKey=none");
    }

    @Test
    @DisplayName("blank or none in any case is no key; anything else is a key, trimmed")
    void should_recogniseKey_when_set() {
        assertThat(anthropic(null).hasKey()).isFalse();
        assertThat(anthropic("").hasKey()).isFalse();
        assertThat(anthropic("  ").hasKey()).isFalse();
        assertThat(anthropic("none").hasKey()).isFalse();
        assertThat(anthropic(" NONE\n").hasKey()).isFalse();
        assertThat(anthropic(" " + KEY + "\n").hasKey()).isTrue();
        assertThat(anthropic(" " + KEY + "\n").key()).isEqualTo(KEY);
        assertThatThrownBy(() -> anthropic("none").key()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("effort must be a level the API knows; model must not be blank")
    void should_refuseBadEffortOrModel() {
        assertThat(new AgentProperties.Anthropic(KEY, "m", " HIGH ", Duration.ofMinutes(1), 0, "").effort())
                .isEqualTo("high");
        assertThatThrownBy(() -> new AgentProperties.Anthropic(KEY, "m", "extreme", Duration.ofMinutes(1), 0, ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(KEY);
        assertThatThrownBy(() -> new AgentProperties.Anthropic(KEY, " ", "low", Duration.ofMinutes(1), 0, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentProperties.Anthropic(KEY, "m", "low", Duration.ZERO, 0, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
