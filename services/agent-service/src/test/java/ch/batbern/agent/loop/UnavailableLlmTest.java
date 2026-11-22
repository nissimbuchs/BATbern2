package ch.batbern.agent.loop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Until story 3 brings the Anthropic adapter, the default port is unavailable: a run hands itself over. */
@DisplayName("UnavailableLlm")
class UnavailableLlmTest {

    @Test
    @DisplayName("should_throwLlmUnavailable_when_called")
    void should_throwLlmUnavailable_when_called() {
        LlmRequest request = new LlmRequest("system", List.of(LlmMessage.user("hello")), List.of(), 16);

        assertThatThrownBy(() -> new UnavailableLlm().complete(request))
                .isInstanceOf(LlmUnavailableException.class);
    }
}
