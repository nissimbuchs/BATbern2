package ch.batbern.gateway.observability;

import ch.batbern.gateway.config.TestRateLimitConfig;
import ch.batbern.gateway.config.TestSecurityConfig;
import ch.batbern.gateway.security.AccountActiveFilter;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #1313: the {@code GATEWAY_API_REQUEST} line must carry the status the client actually got.
 *
 * <p>{@code ProxyController} returns a {@code CompletableFuture}, so for every proxied request
 * {@code chain.doFilter} returns at the START of async processing, while the status is still
 * 200. In production that logged 56,808 {@code status=200} lines in 7 days against 11,712
 * downstream 4xx, and left the 4xx-ratio alarm blind to every downstream error.
 *
 * <p>This test boots the real gateway on a real Tomcat (MockMvc does not run the async dispatch
 * the way the container does) and proxies to a JDK {@link HttpServer} standing in for EMS. The
 * downstream is the process boundary; nothing of ours is mocked.
 */
// The is_active gate runs as in production (SECURITY_ACTIVE_GATE_ENABLED=true in the stack).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "security.active-gate.enabled=true")
@ActiveProfiles("test")
@Import({TestSecurityConfig.class, TestRateLimitConfig.class})
class ClientErrorMetricsFilterIntegrationTest {

    /** Calls the is_active gate made to the stub; the gate caches, so typically one. */
    private static final AtomicInteger GATE_LOOKUPS = new AtomicInteger();

    private static final HttpServer DOWNSTREAM = startDownstream();

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private Logger filterLogger;
    private ListAppender<ILoggingEvent> appender;
    private Logger gateLogger;
    private ListAppender<ILoggingEvent> gateAppender;

    /** Answers with the status named in the last path segment: {@code /api/v1/events/stub-404}. */
    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            // The is_active gate (on in production) asks CUMS for the caller: an active user, as
            // GET /internal/users/{username} answers it (cums-internal.openapi.yml, UserResponse).
            server.createContext("/internal/users/", exchange -> {
                GATE_LOOKUPS.incrementAndGet();
                byte[] body = "{\"id\":\"test-user-123\",\"active\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.createContext("/api/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                int status = Integer.parseInt(path.substring(path.lastIndexOf("stub-") + "stub-".length()));
                byte[] body = ("{\"status\":" + status + "}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("could not start the downstream stub", e);
        }
    }

    @DynamicPropertySource
    static void downstream(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort();
        registry.add("services.event-management.url", () -> url);
        registry.add("services.partner-coordination.url", () -> url);
        registry.add("services.company-user-management.url", () -> url);
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @BeforeEach
    void captureLog() {
        filterLogger = (Logger) LoggerFactory.getLogger(ClientErrorMetricsFilter.class);
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
        gateLogger = (Logger) LoggerFactory.getLogger(AccountActiveFilter.class);
        gateAppender = new ListAppender<>();
        gateAppender.start();
        gateLogger.addAppender(gateAppender);
    }

    @AfterEach
    void releaseLog() {
        filterLogger.detachAppender(appender);
        gateLogger.detachAppender(gateAppender);
        assertThat(messages(gateAppender))
                .as("the gate must have answered from the stub, not failed open")
                .noneMatch(line -> line.contains("failing open"));
    }

    /**
     * A copy taken under the appender's lock: Tomcat threads append while the test reads, and
     * {@code AppenderBase.doAppend} is synchronized on the appender.
     */
    private static List<String> messages(ListAppender<ILoggingEvent> source) {
        synchronized (source) {
            return source.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        }
    }

    private int get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer mock-jwt-token")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /**
     * The request lines for {@code path}. The async listener runs after the response is
     * complete, which can be a moment after the client has its bytes, so wait briefly for the
     * first line and then a little longer for a second one that must not come.
     */
    private List<String> linesFor(String path) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (matching(path).isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        Thread.sleep(200);
        return matching(path);
    }

    private List<String> matching(String path) {
        return messages(appender).stream()
                .filter(line -> line.startsWith(ClientErrorMetricsFilter.LOG_MARKER))
                .filter(line -> line.endsWith("path=" + path))
                .toList();
    }

    @Test
    void should_logDownstream404_when_proxiedRequestFails() throws Exception {
        String path = "/api/v1/events/stub-404";

        assertThat(get(path)).isEqualTo(404);

        assertThat(linesFor(path))
                .as("one line, with the status the client got, not the 200 at async start")
                .containsExactly("GATEWAY_API_REQUEST status=404 clientError=true method=GET path=" + path);
    }

    @Test
    void should_log200_when_proxiedRequestSucceeds() throws Exception {
        String path = "/api/v1/events/stub-200";

        assertThat(get(path)).isEqualTo(200);
        assertThat(GATE_LOOKUPS.get())
                .as("the is_active gate ran against the stub, as in production")
                .isPositive();

        assertThat(linesFor(path))
                .containsExactly("GATEWAY_API_REQUEST status=200 clientError=false method=GET path=" + path);
    }

    @Test
    void should_logDownstream5xxAsNoClientError_when_proxiedRequestFailsOnTheServer() throws Exception {
        String path = "/api/v1/events/stub-500";

        assertThat(get(path)).isEqualTo(500);

        assertThat(linesFor(path))
                .containsExactly("GATEWAY_API_REQUEST status=500 clientError=false method=GET path=" + path);
    }

    @Test
    void should_logGatewayLocal404Once_when_noRouteMatches() throws Exception {
        // Synchronous: DomainRouter throws before any future exists, GlobalExceptionHandler
        // answers 404 on the request thread. This case was already right and must stay single.
        String path = "/api/v1/no-such-domain";

        assertThat(get(path)).isEqualTo(404);

        assertThat(linesFor(path))
                .containsExactly("GATEWAY_API_REQUEST status=404 clientError=true method=GET path=" + path);
    }
}
