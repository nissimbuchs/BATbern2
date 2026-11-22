package ch.batbern.agent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The adapter's and CUMS's configuration: what counts as a key or secret, and a toString that never shows it. */
@DisplayName("AgentProperties")
class AgentPropertiesTest {

    private static final String KEY = "sk-ant-properties-test-secret-1234";
    private static final String CALLER_SECRET = "caller-secret-properties-test-5678";

    private static AgentProperties.Anthropic anthropic(String key) {
        return new AgentProperties.Anthropic(key, "claude-sonnet-5-5", "medium", Duration.ofMinutes(5), 2, "");
    }

    @Test
    @DisplayName("toString masks the key, of the nested record and of the whole configuration")
    void should_maskKey_when_toString() {
        AgentProperties.Anthropic anthropic = anthropic(KEY);
        AgentProperties properties = new AgentProperties(false, new AgentProperties.Loop(16000),
                new AgentProperties.Limits(25, 200000, 2, 2000000, 2, 1), anthropic,
                new AgentProperties.Cums("http://cums:8080", CALLER_SECRET), new AgentProperties.Gateway("http://gw:8080"));

        assertThat(anthropic.toString()).doesNotContain(KEY).doesNotContain("secret")
                .contains("apiKey=****").contains("model=claude-sonnet-5-5");
        assertThat(properties.toString()).doesNotContain(KEY).contains("apiKey=****")
                .doesNotContain(CALLER_SECRET).contains("callerSecret=****");
        assertThat(anthropic("none").toString()).contains("apiKey=none");
    }

    @Test
    @DisplayName("limits: each below its minimum is refused, naming the property")
    void should_refuse_when_limitBelowMinimum() {
        assertThat(new AgentProperties.Limits(1, 1, 0, 1, 1, 0)).isNotNull();
        assertThatThrownBy(() -> new AgentProperties.Limits(0, 1, 0, 1, 1, 0)).hasMessageContaining("run-steps");
        assertThatThrownBy(() -> new AgentProperties.Limits(1, 0, 0, 1, 1, 0)).hasMessageContaining("run-tokens");
        assertThatThrownBy(() -> new AgentProperties.Limits(1, 1, -1, 1, 1, 0)).hasMessageContaining("run-replies");
        assertThatThrownBy(() -> new AgentProperties.Limits(1, 1, 0, 0, 1, 0)).hasMessageContaining("daily-tokens");
        assertThatThrownBy(() -> new AgentProperties.Limits(1, 1, 0, 1, 0, 0))
                .hasMessageContaining("binding-approvals");
        assertThatThrownBy(() -> new AgentProperties.Limits(1, 1, 0, 1, 1, -1))
                .hasMessageContaining("non-organizer-recipients");
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

    @Test
    @DisplayName("Cums: blank or none in any case is no caller secret; toString masks it; base URLs lose a trailing slash")
    void should_recogniseCallerSecret_when_set() {
        assertThat(new AgentProperties.Cums("http://cums:8080", null).hasCallerSecret()).isFalse();
        assertThat(new AgentProperties.Cums("http://cums:8080", "").hasCallerSecret()).isFalse();
        assertThat(new AgentProperties.Cums("http://cums:8080", " None ").hasCallerSecret()).isFalse();
        AgentProperties.Cums cums = new AgentProperties.Cums("http://cums:8080/", " " + CALLER_SECRET + "\n");
        assertThat(cums.hasCallerSecret()).isTrue();
        assertThat(cums.secret()).isEqualTo(CALLER_SECRET);
        assertThat(cums.baseUrl()).isEqualTo("http://cums:8080");
        assertThat(cums.toString()).doesNotContain(CALLER_SECRET).contains("callerSecret=****");
        assertThat(new AgentProperties.Cums("http://cums:8080", "none").toString()).contains("callerSecret=none");
        assertThatThrownBy(() -> new AgentProperties.Cums("http://cums:8080", "").secret())
                .isInstanceOf(IllegalStateException.class);
        assertThat(new AgentProperties.Gateway("http://api-gateway:8080//").baseUrl()).isEqualTo("http://api-gateway:8080");
        assertThatThrownBy(() -> new AgentProperties.Gateway(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
