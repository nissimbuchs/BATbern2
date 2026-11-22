package ch.batbern.gateway.routing.integration;

import ch.batbern.gateway.config.TestRateLimitConfig;
import ch.batbern.gateway.config.TestSecurityConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The agent route on the wire: the real gateway on a real Tomcat forwards {@code /api/v1/agent/runs}
 * to {@code services.agent.url}, and {@code /api/v1/agent-settings} still to CUMS. Each downstream is
 * a JDK {@link HttpServer} that records the requests it got (the process boundary; nothing of ours is
 * mocked).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestSecurityConfig.class, TestRateLimitConfig.class})
@DisplayName("Agent route on the wire")
class AgentRouteWireIntegrationTest {

    private static final List<String> AGENT_HITS = new CopyOnWriteArrayList<>();
    private static final List<String> OTHER_HITS = new CopyOnWriteArrayList<>();

    private static final HttpServer AGENT = startStub("agent", AGENT_HITS);
    private static final HttpServer OTHERS = startStub("others", OTHER_HITS);

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** Answers 200 with a body naming the stub, and records the path and query it received. */
    private static HttpServer startStub(String name, List<String> hits) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                URI uri = exchange.getRequestURI();
                hits.add(uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
                byte[] body = ("{\"stub\":\"" + name + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("could not start the " + name + " stub", e);
        }
    }

    @DynamicPropertySource
    static void downstreams(DynamicPropertyRegistry registry) {
        String others = "http://127.0.0.1:" + OTHERS.getAddress().getPort();
        registry.add("services.agent.url", () -> "http://127.0.0.1:" + AGENT.getAddress().getPort());
        registry.add("services.event-management.url", () -> others);
        registry.add("services.partner-coordination.url", () -> others);
        registry.add("services.company-user-management.url", () -> others);
    }

    @AfterAll
    static void stopStubs() {
        AGENT.stop(0);
        OTHERS.stop(0);
    }

    @BeforeEach
    void clearHits() {
        AGENT_HITS.clear();
        OTHER_HITS.clear();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer mock-jwt-token")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("should_forwardToAgentService_when_agentRunsRequested")
    void should_forwardToAgentService_when_agentRunsRequested() throws Exception {
        HttpResponse<String> response = get("/api/v1/agent/runs?page=0&limit=20");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"stub\":\"agent\"");
        assertThat(AGENT_HITS).containsExactly("/api/v1/agent/runs?page=0&limit=20");
        assertThat(OTHER_HITS).isEmpty();
    }

    @Test
    @DisplayName("should_forwardToCums_when_agentSettingsRequested")
    void should_forwardToCums_when_agentSettingsRequested() throws Exception {
        HttpResponse<String> response = get("/api/v1/agent-settings");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"stub\":\"others\"");
        assertThat(OTHER_HITS).containsExactly("/api/v1/agent-settings");
        assertThat(AGENT_HITS).isEmpty();
    }
}
