package ch.batbern.agent.gateway;

import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.identity.CumsAgentClient;
import ch.batbern.agent.identity.GrantSource;
import ch.batbern.agent.identity.RunCredentials;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The path guard of {@link PublicApi}: a tool cannot leave {@code /api/v1} with a dot segment, a
 * double slash, a backslash, or their percent-encoded forms. The guard runs before a token is
 * minted or a request sent: the server has no expectation, so any request would fail the test.
 */
@DisplayName("PublicApi refuses paths that could leave /api/v1")
class PublicApiPathTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"companies", "//evil.example/x", "/companies/../internal/x", "/companies/%2e%2e/x",
        "/companies/%2E%2E/x", "/companies%2finternal", "/companies%2Finternal", "/companies\\..\\internal",
        "/companies%5cx", "/companies%5Cx"})
    void should_refusePath_when_itCouldLeaveThePublicApi(String path) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        AgentProperties properties = new AgentProperties(true, new AgentProperties.Loop(25, 16000),
                new AgentProperties.Anthropic("none", "m", "medium", Duration.ofMinutes(1), 0, ""),
                new AgentProperties.Cums("http://cums:8080", "test-secret"),
                new AgentProperties.Gateway("http://gw:8080"));
        // A handed-over grant: obtaining it calls nothing
        RunCredentials credentials = RunCredentials.obtain(new CumsAgentClient(restTemplate, properties),
                new GrantSource.HandedOver(UUID.randomUUID(), "grant-secret"), Clock.systemUTC());
        PublicApi api = new PublicApi(restTemplate, properties.gateway().baseUrl(), credentials);

        assertThatThrownBy(() -> api.get(path)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.delete(path)).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }
}
