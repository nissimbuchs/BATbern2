package ch.batbern.agent.identity;

import ch.batbern.agent.run.RunTrigger;
import ch.batbern.shared.test.contract.ContractServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CUMS at the wire for agent-service tests (story 4): the repository's S2S harness
 * ({@link ContractServer}) bound to agent-service's one {@code RestTemplate}, validating every request
 * against the CUMS internal spec and the companies spec, which stands for the public API behind
 * the gateway. Grants and mints are answered per request (a fresh run id and grant secret per grant,
 * a distinct token per mint), or refused with a CUMS code. Every request is recorded with its
 * headers and body, so tests assert what agent-service sent.
 *
 * <p>Call {@link #close()} after each test: the {@code RestTemplate} is the shared context bean.
 */
public final class CumsAtTheWire implements AutoCloseable {

    public static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";
    public static final String COMPANIES = "docs/api/companies-api.openapi.yml";
    public static final String GRANTS = "/internal/agent-grants";
    public static final String TOKENS = "/internal/agent-tokens";
    /** The caller secret of application-test.yml. */
    public static final String CALLER_SECRET = "test-only-agent-caller-secret";
    /** The username CUMS names in every grant answer: it must never reach a log. */
    public static final String PRINCIPAL_USERNAME = "grant.principal.probe";
    /** The scheduler job of {@link #source(RunTrigger)}. */
    public static final String JOB = "morning-brief";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ContractServer server;
    private final List<Recorded> grantRequests = new ArrayList<>();
    private final List<Recorded> mintRequests = new ArrayList<>();
    private final List<String> grantSecrets = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();

    /** One request as sent: its headers and body. */
    public record Recorded(HttpHeaders headers, JsonNode body) {
    }

    private CumsAtTheWire(RestTemplate restTemplate) {
        this.server = ContractServer.bindTo(restTemplate, CUMS_INTERNAL, COMPANIES);
    }

    public static CumsAtTheWire bindTo(RestTemplate restTemplate) {
        return new CumsAtTheWire(restTemplate);
    }

    /** A grant source per trigger, for loop tests that only care about the trigger. */
    public static GrantSource source(RunTrigger trigger) {
        return switch (trigger) {
            case SCHEDULED -> new GrantSource.SchedulerJob(JOB);
            case ORGANIZER -> new GrantSource.OrganizerLogin("cognito-organizer-jwt");
            case LINKED_RUN -> new GrantSource.LinkedRun(UUID.randomUUID().toString(), grantSecret());
            case INBOUND_MAIL -> new GrantSource.HandedOver(UUID.randomUUID(), grantSecret());
        };
    }

    /** The harness, for gateway expectations ({@code /api/v1/companies}). */
    public ContractServer server() {
        return server;
    }

    /** Every grant request is answered 201 with a fresh run id and grant secret, any number of times. */
    public CumsAtTheWire grants() {
        server.expect(HttpMethod.POST, GRANTS).times(ExpectedCount.manyTimes()).andExpect(this::recordGrant)
                .respondPerRequest(HttpStatus.CREATED, call -> grantAnswer(call.body()));
        return this;
    }

    /** The one grant request is refused with {@code status} and {@code details.code}. */
    public CumsAtTheWire refusesGrant(HttpStatus status, String code) {
        server.expect(HttpMethod.POST, GRANTS).andExpect(this::recordGrant)
                .respondPerRequest(call -> new ContractServer.Reply(status, error(status, GRANTS, code)));
        return this;
    }

    /** The one grant request fails in transport (CUMS unreachable). */
    public CumsAtTheWire grantUnreachable() {
        server.expect(HttpMethod.POST, GRANTS).andExpect(this::recordGrant).respondWith(request -> {
            throw new IOException("Connection refused");
        });
        return this;
    }

    /**
     * The one grant request is answered {@code status} without a body, a status the grant
     * operation does not declare (a proxy in front of CUMS answering 502 or 503); the request is
     * still validated.
     */
    public CumsAtTheWire grantAnswersWithoutCode(HttpStatus status) {
        server.expect(HttpMethod.POST, GRANTS).andExpect(this::recordGrant)
                .respondWith(MockRestResponseCreators.withStatus(status));
        return this;
    }

    /** The first {@code okCount} mints get a token (300 s), then every mint fails in transport. */
    public CumsAtTheWire mintsThenUnreachable(int okCount) {
        AtomicInteger calls = new AtomicInteger();
        server.expect(HttpMethod.POST, TOKENS).times(ExpectedCount.manyTimes()).andExpect(this::recordMint)
                .respondWith(request -> {
                    if (calls.incrementAndGet() > okCount) {
                        throw new IOException("Connection refused");
                    }
                    String token = "agent-token-" + calls.get() + "-" + UUID.randomUUID();
                    synchronized (tokens) {
                        tokens.add(token);
                    }
                    return MockRestResponseCreators.withSuccess(JSON.createObjectNode().put("token", token)
                            .put("expiresAt", Instant.now().plusSeconds(300).toString()).toString(),
                            MediaType.APPLICATION_JSON).createResponse(request);
                });
        return this;
    }

    /** Every mint is answered 200 with a distinct token living {@code lifetime}, any number of times. */
    public CumsAtTheWire mints(Duration lifetime) {
        return mintsThenRefuses(Integer.MAX_VALUE, null, null, lifetime);
    }

    /** Every mint is answered with a token living 300 s. */
    public CumsAtTheWire mints() {
        return mints(Duration.ofSeconds(300));
    }

    /**
     * The first {@code okCount} mints get a token (300 s), every later one is refused with
     * {@code status} and {@code details.code} ({@code null} code: no body).
     */
    public CumsAtTheWire mintsThenRefuses(int okCount, HttpStatus status, String code) {
        return mintsThenRefuses(okCount, status, code, Duration.ofSeconds(300));
    }

    /** As {@link #mintsThenRefuses(int, HttpStatus, String)}, with tokens living {@code lifetime}. */
    public CumsAtTheWire mintsThenRefuses(int okCount, HttpStatus status, String code, Duration lifetime) {
        AtomicInteger calls = new AtomicInteger();
        server.expect(HttpMethod.POST, TOKENS).times(ExpectedCount.manyTimes()).andExpect(this::recordMint)
                .respondPerRequest(call -> {
                    if (calls.incrementAndGet() > okCount) {
                        return new ContractServer.Reply(status, code == null ? null : error(status, TOKENS, code));
                    }
                    String token = "agent-token-" + calls.get() + "-" + UUID.randomUUID();
                    synchronized (tokens) {
                        tokens.add(token);
                    }
                    return ContractServer.Reply.ok(JSON.createObjectNode().put("token", token)
                            .put("expiresAt", Instant.now().plus(lifetime).toString()).toString());
                });
        return this;
    }

    /** The grant requests sent, in order. */
    public List<Recorded> grantRequests() {
        return List.copyOf(grantRequests);
    }

    /** The mint requests sent, in order. */
    public List<Recorded> mintRequests() {
        return List.copyOf(mintRequests);
    }

    /** The grant secrets CUMS answered, in order. */
    public List<String> grantSecrets() {
        return List.copyOf(grantSecrets);
    }

    /** The tokens CUMS minted, in order. */
    public List<String> tokens() {
        synchronized (tokens) {
            return List.copyOf(tokens);
        }
    }

    public void verify() {
        server.verify();
    }

    @Override
    public void close() {
        server.close();
    }

    /** 32 random bytes, base64url without padding: 43 characters, as CUMS issues them. */
    public static String grantSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String grantAnswer(String requestBody) {
        JsonNode request = read(requestBody);
        String trigger = request.path("trigger").asText();
        String principalKind = switch (trigger) {
            case "ORGANIZER_LOGIN" -> "ORGANIZER";
            case "EMS_HANDOFF" -> "REQUESTER";
            default -> "ORGANIZER_DELEGATE";
        };
        String secret = grantSecret();
        synchronized (grantSecrets) {
            grantSecrets.add(secret);
        }
        Instant issued = Instant.now();
        ObjectNode answer = JSON.createObjectNode()
                .put("runId", UUID.randomUUID().toString())
                .put("grantSecret", secret)
                .put("principalKind", principalKind)
                .put("username", PRINCIPAL_USERNAME)
                .put("trigger", trigger)
                .put("issuedAt", issued.toString())
                .put("expiresAt", issued.plus(Duration.ofHours(1)).toString());
        answer.set("linkedRunId", request.has("linkedRunId") ? request.get("linkedRunId") : JSON.nullNode());
        answer.set("jobName", request.has("jobName") ? request.get("jobName") : JSON.nullNode());
        return answer.toString();
    }

    private static String error(HttpStatus status, String path, String code) {
        ObjectNode body = JSON.createObjectNode()
                .put("timestamp", Instant.now().toString())
                .put("path", path)
                .put("status", status.value())
                .put("error", status.getReasonPhrase())
                .put("message", "refused by the test")
                .put("severity", "LOW");
        body.putObject("details").put("code", code);
        return body.toString();
    }

    private void recordGrant(ClientHttpRequest request) {
        synchronized (grantRequests) {
            grantRequests.add(record(request));
        }
    }

    private void recordMint(ClientHttpRequest request) {
        synchronized (mintRequests) {
            mintRequests.add(record(request));
        }
    }

    private static Recorded record(ClientHttpRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(request.getHeaders());
        String body = request instanceof MockClientHttpRequest mock ? mock.getBodyAsString(StandardCharsets.UTF_8) : "";
        return new Recorded(headers, read(body));
    }

    private static JsonNode read(String json) {
        try {
            return json == null || json.isEmpty() ? JSON.nullNode() : JSON.readTree(json);
        } catch (IOException e) {
            throw new AssertionError("Not JSON: " + json, e);
        }
    }
}
