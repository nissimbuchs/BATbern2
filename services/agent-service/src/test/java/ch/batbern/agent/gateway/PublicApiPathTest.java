package ch.batbern.agent.gateway;

import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.identity.CumsAgentClient;
import ch.batbern.agent.identity.GrantSource;
import ch.batbern.agent.identity.RunCredentials;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The path guard of {@link PublicApi}: a tool cannot leave {@code /api/v1} with a dot segment, a
 * double slash, a backslash, or their percent-encoded forms, in the path part; and a limited API
 * (story 5) lets a tool reach only the operations it declares. The guard runs before a token is
 * minted or a request sent: the server has no expectation, so any request would fail the test.
 */
@DisplayName("PublicApi refuses paths that could leave /api/v1")
class PublicApiPathTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"companies", "//evil.example/x", "/companies/../internal/x", "/companies/%2e%2e/x",
        "/companies/%2E%2E/x", "/companies%2finternal", "/companies%2Finternal", "/companies\\..\\internal",
        "/companies%5cx", "/companies%5Cx", "/companies%2f..?q=1", "/companies#x", "/companies?q=1#x"})
    void should_refusePath_when_itCouldLeaveThePublicApi(String path) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PublicApi api = new PublicApi(restTemplate, PROPERTIES.gateway().baseUrl(), credentials(restTemplate));

        assertThatThrownBy(() -> api.get(path)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.delete(path)).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    @DisplayName("an encoded slash or dot in a query value passes: it cannot change the route")
    void should_sendQuery_when_aValueHoldsAnEncodedSlashOrDot() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://cums:8080/internal/agent-tokens")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"token\":\"t-1\",\"expiresAt\":\"" + Instant.now().plusSeconds(300)
                        + "\"}", MediaType.APPLICATION_JSON));
        String path = "/events?filter=%7B%22title%22%3A%22a%2Fb%2E%22%7D";
        server.expect(requestTo("http://gw:8080/api/v1" + path)).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer t-1"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        RunCredentials credentials = credentials(restTemplate);
        credentials.start();
        PublicApi api = new PublicApi(restTemplate, PROPERTIES.gateway().baseUrl(), credentials)
                .limitedTo(Set.of(Operation.of("GET /events")));

        assertThat(api.get(path).status()).isEqualTo(200);
        server.verify();
    }

    @Test
    @DisplayName("a limited API refuses an operation the tool does not declare, before any token or request")
    void should_refuseCall_when_operationNotDeclared() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        // Credentials never started: reading a token would throw something else than the refusal
        PublicApi api = new PublicApi(restTemplate, PROPERTIES.gateway().baseUrl(), credentials(restTemplate))
                .limitedTo(Set.of(Operation.of("GET /events/{eventCode}/sessions")));

        assertThatThrownBy(() -> api.get("/events")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not declare");
        assertThatThrownBy(() -> api.delete("/events/BATbern56/sessions")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not declare");
        assertThatThrownBy(() -> api.get("/events/BATbern56/sessions/x")).isInstanceOf(IllegalStateException.class);
        // A tool cannot widen the API it was handed: a second limit only narrows
        PublicApi widened = api.limitedTo(Set.of(Operation.of("GET /events"),
                Operation.of("GET /events/{eventCode}/sessions")));
        assertThatThrownBy(() -> widened.get("/events")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not declare");
        server.verify();
    }

    private static final AgentProperties PROPERTIES = new AgentProperties(true, new AgentProperties.Loop(25, 16000),
            new AgentProperties.Anthropic("none", "m", "medium", Duration.ofMinutes(1), 0, ""),
            new AgentProperties.Cums("http://cums:8080", "test-secret"),
            new AgentProperties.Gateway("http://gw:8080"));

    /** A handed-over grant: obtaining it calls nothing. */
    private static RunCredentials credentials(RestTemplate restTemplate) {
        return RunCredentials.obtain(new CumsAgentClient(restTemplate, PROPERTIES),
                new GrantSource.HandedOver(UUID.randomUUID(), "grant-secret"), Clock.systemUTC());
    }
}
