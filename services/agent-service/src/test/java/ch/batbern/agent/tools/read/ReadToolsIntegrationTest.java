package ch.batbern.agent.tools.read;

import ch.batbern.agent.gateway.Operation;
import ch.batbern.agent.identity.CumsAtTheWire;
import ch.batbern.agent.identity.GrantSource;
import ch.batbern.agent.loop.AgentLoop;
import ch.batbern.agent.loop.AgentRunRequest;
import ch.batbern.agent.loop.AgentRunResult;
import ch.batbern.agent.loop.LlmContent;
import ch.batbern.agent.loop.LlmResponse;
import ch.batbern.agent.loop.LlmStopReason;
import ch.batbern.agent.loop.LlmToolSpec;
import ch.batbern.agent.loop.LlmUsage;
import ch.batbern.agent.loop.ScriptedLlm;
import ch.batbern.agent.loop.TestTools;
import ch.batbern.agent.run.AgentRunStatus;
import ch.batbern.agent.tools.OperationClass;
import ch.batbern.agent.tools.TestToolRegistry;
import ch.batbern.agent.tools.ToolOutcome;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer;
import ch.batbern.shared.test.contract.OpenApiContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Story 5 (R4, AD-6, AD-8): the loop offers the registry's read tools, and a scripted run reads past
 * events, their sessions and speakers through the public API with its delegated token. CUMS and the
 * gateway are answered at the wire ({@link CumsAtTheWire}, every request validated against the CUMS
 * internal, events-core and sessions specs); only the model is scripted. The shipped tools run
 * as they are: the registry is the shipped one ({@link TestToolRegistry} offers it until a test
 * installs others).
 */
@DisplayName("Read tools see past events through the public API")
@TestPropertySource(properties = {"agent.enabled=true", "agent.limits.run-steps=5"})
class ReadToolsIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENTS_CORE = "docs/api/events-core-api.openapi.yml";
    private static final String SESSIONS = "docs/api/event-sessions-api.openapi.yml";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LlmUsage TOKENS = new LlmUsage(1, 1, 0, 0);
    private static final GrantSource SCHEDULER = new GrantSource.SchedulerJob(CumsAtTheWire.JOB);

    @Autowired
    private AgentLoop loop;

    @Autowired
    private ScriptedLlm model;

    @Autowired
    private TestToolRegistry toolRegistry;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RestTemplate restTemplate;

    private CumsAtTheWire cums;

    /** The Authorization header of every gateway request, in order. */
    private final List<String> gatewayAuthorizations = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    @AfterEach
    void clean() {
        model.reset();
        toolRegistry.reset();
        jdbc.execute("TRUNCATE agent_runs CASCADE");
        gatewayAuthorizations.clear();
    }

    @BeforeEach
    void bindCums() {
        cums = CumsAtTheWire.bindTo(restTemplate, EVENTS_CORE, SESSIONS);
    }

    @AfterEach
    void unbindCums() {
        cums.close();
    }

    @Test
    @DisplayName("findPastEvents: GET /events before now, archived included, newest first, with the run's token")
    void should_findPastEvents_when_modelAsksForAYear() throws IOException {
        cums.grants().mints();
        List<ContractServer.Call> calls = Collections.synchronizedList(new ArrayList<>());
        cums.server().expect(HttpMethod.GET, "/api/v1/events").andExpect(this::recordAuthorization)
                .respondPerRequest(HttpStatus.OK, call -> {
                    calls.add(call);
                    return example(EVENTS_CORE, "/api/v1/events");
                });
        model.then(use("findPastEvents",
                        JSON.createObjectNode().put("year", 2025).put("searchText", "Cloud/Native")))
                .then(endTurn());

        AgentRunResult result = loop.run(new AgentRunRequest(SCHEDULER, "You look up BATbern history.", "2025?"));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        cums.verify();
        // The model was offered the three shipped tools, each with its schema
        assertThat(model.requests().getFirst().tools()).extracting(LlmToolSpec::name)
                .containsExactly("findPastEvents", "listEventSessions", "listSessionSpeakers");
        assertThat(model.requests().getFirst().tools().get(1).inputSchema().path("required").toString())
                .isEqualTo("[\"eventCode\"]");
        // The query: past only (date before now), the year, the title text, archived, newest first
        ContractServer.Call call = calls.getFirst();
        JsonNode filter = JSON.readTree(call.queryParam("filter"));
        assertThat(Instant.parse(filter.path("date").path("$lt").asText()))
                .isCloseTo(Instant.now(), within(Duration.ofMinutes(1)));
        assertThat(filter.path("year").asInt()).isEqualTo(2025);
        assertThat(filter.path("title").path("$contains").asText()).isEqualTo("Cloud/Native");
        assertThat(call.queryParam("includeArchived")).isEqualTo("true");
        assertThat(call.queryParam("sort")).isEqualTo("-date");
        assertThat(call.queryParam("limit")).isEqualTo("10");
        // With the minted token (one mint: reads reuse it)
        assertThat(gatewayAuthorizations).containsExactly("Bearer " + cums.tokens().getFirst());
        assertThat(cums.mintRequests()).hasSize(1);
        // The model got the projection: the event fields, nothing else
        JsonNode output = JSON.readTree(toolOutput(result.runId(), "SUCCEEDED"));
        JsonNode exampleEvent = JSON.readTree(example(EVENTS_CORE, "/api/v1/events")).path("data").get(0);
        assertThat(output.path("events")).hasSize(1);
        JsonNode event = output.path("events").get(0);
        assertThat(event.path("eventCode").asText()).isEqualTo(exampleEvent.path("eventCode").asText());
        assertThat(fieldNames(event)).isSubsetOf(Projections.EVENT).contains("eventCode", "title", "date");
        assertThat(exampleEvent.has("venueAddress")).as("positive control: the API sent more").isTrue();
        assertThat(event.has("venueAddress")).isFalse();
    }

    @Test
    @DisplayName("listEventSessions then listSessionSpeakers: two GETs, one token, no picture to the model")
    void should_readSessionsAndSpeakers_when_modelAsksForAnEvent() throws IOException {
        cums.grants().mints();
        cums.server().expect(HttpMethod.GET, "/api/v1/events/BATbern56/sessions").andExpect(this::recordAuthorization)
                .withQueryParam("limit", "100").respondWith(HttpStatus.OK, sessionsPage());
        cums.server().expect(HttpMethod.GET, "/api/v1/events/BATbern56/sessions/blockchain-security/speakers")
                .andExpect(this::recordAuthorization).respondWithExample();
        model.then(use("listEventSessions", JSON.createObjectNode().put("eventCode", "BATbern56")))
                .then(use("listSessionSpeakers", JSON.createObjectNode().put("eventCode", "BATbern56")
                        .put("sessionSlug", "blockchain-security")))
                .then(endTurn());

        AgentRunResult result = loop.run(new AgentRunRequest(SCHEDULER, "You look up BATbern history.", "Who spoke?"));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        cums.verify();
        assertThat(cums.mintRequests()).as("reads reuse the run's token").hasSize(1);
        assertThat(gatewayAuthorizations).hasSize(2).containsOnly("Bearer " + cums.tokens().getFirst());

        List<String> outputs = jdbc.queryForList("SELECT t.output FROM agent_tool_calls t "
                + "JOIN agent_steps s ON s.id = t.step_id WHERE t.run_id = ? AND t.status = 'SUCCEEDED' "
                + "ORDER BY s.step_number", String.class, result.runId());
        assertThat(outputs).hasSize(2);
        JsonNode sessions = JSON.readTree(outputs.get(0));
        assertThat(sessions.path("eventCode").asText()).isEqualTo("BATbern56");
        JsonNode session = sessions.path("sessions").get(0);
        assertThat(fieldNames(session)).contains("sessionSlug", "title", "speakers")
                .doesNotContain("materials", "description");
        assertThat(session.path("speakers")).isNotEmpty();
        assertThat(fieldNames(session.path("speakers").get(0))).isSubsetOf(Projections.SPEAKER).contains("username");
        JsonNode speakers = JSON.readTree(outputs.get(1));
        assertThat(speakers.path("sessionSlug").asText()).isEqualTo("blockchain-security");
        JsonNode speaker = speakers.path("speakers").get(0);
        assertThat(speaker.path("username").asText()).isNotBlank();
        assertThat(fieldNames(speaker)).isSubsetOf(Projections.SPEAKER);
        // Positive control: the API sent the picture; the model did not get it
        assertThat(example(SESSIONS, "/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers"))
                .contains("profilePictureUrl");
        assertThat(outputs.get(1)).doesNotContain("profilePictureUrl", "cdn.batbern.ch");
    }

    @Test
    @DisplayName("a bad eventCode is an error result for the model, and nothing is sent")
    void should_returnError_when_eventCodeIsNotAnAdr003Id() {
        cums.grants().mints();
        // No gateway expectation: any GET fails the test
        model.then(use("listEventSessions", JSON.createObjectNode().put("eventCode", "56"))).then(endTurn());

        AgentRunResult result = loop.run(new AgentRunRequest(SCHEDULER, "You look up BATbern history.", "56?"));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        cums.verify();
        assertThat(toolOutput(result.runId(), "ERRORED")).contains("eventCode").contains("BATbern");
        assertThat(lastToolResult().isError()).isTrue();
        assertThat(gatewayAuthorizations).isEmpty();
    }

    @Test
    @DisplayName("a 404 of the API is an error result naming the status; the run goes on")
    void should_returnError_when_apiAnswers404() {
        cums.grants().mints();
        cums.server().expect(HttpMethod.GET, "/api/v1/events/BATbern9/sessions").andExpect(this::recordAuthorization)
                .respondPerRequest(call -> new ContractServer.Reply(HttpStatus.NOT_FOUND, notFound()));
        model.then(use("listEventSessions", JSON.createObjectNode().put("eventCode", "BATbern9"))).then(endTurn());

        AgentRunResult result = loop.run(new AgentRunRequest(SCHEDULER, "You look up BATbern history.", "BATbern9?"));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        assertThat(result.steps()).isEqualTo(2);
        cums.verify();
        assertThat(toolOutput(result.runId(), "ERRORED")).contains("404").contains("Event not found");
        assertThat(lastToolResult().isError()).isTrue();
    }

    @Test
    @DisplayName("a tool calling an operation it does not declare fails, and nothing is sent")
    void should_failToolCall_when_toolCallsUndeclaredOperation() {
        cums.grants().mints();
        // Declares GET /events (read), calls the sessions operation: refused before the token
        toolRegistry.install(List.of(TestTools.calling("findSneakily", OperationClass.READ,
                Set.of(Operation.of("GET /events")), (input, context) -> {
                    context.api().get("/events/BATbern56/sessions");
                    return ToolOutcome.ok("read");
                })));
        model.then(use("findSneakily", JSON.createObjectNode())).then(endTurn());

        AgentRunResult result = loop.run(new AgentRunRequest(SCHEDULER, "You look up BATbern history.", "Sneak"));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        cums.verify();
        assertThat(jdbc.queryForObject("SELECT status FROM agent_tool_calls WHERE run_id = ?", String.class,
                result.runId())).isEqualTo("FAILED");
        assertThat(gatewayAuthorizations).isEmpty();
        assertThat(lastToolResult().isError()).isTrue();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

    private void recordAuthorization(ClientHttpRequest request) {
        gatewayAuthorizations.add(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    private String toolOutput(UUID runId, String status) {
        return jdbc.queryForObject("SELECT output FROM agent_tool_calls WHERE run_id = ? AND status = ?", String.class,
                runId, status);
    }

    /** The last tool_result the model got. */
    private LlmContent.ToolResult lastToolResult() {
        List<LlmContent> content = model.requests().getLast().messages().getLast().content();
        return (LlmContent.ToolResult) content.getLast();
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static String example(String spec, String path) {
        return OpenApiContract.load(spec).example("GET", path, 200, null);
    }

    /** One page of sessions: the spec's single-session example (with its speakers) as a list. */
    private static String sessionsPage() throws IOException {
        ObjectNode page = JSON.createObjectNode();
        page.putArray("data")
                .add(JSON.readTree(example(SESSIONS, "/api/v1/events/{eventCode}/sessions/{sessionSlug}")));
        page.putObject("pagination").put("page", 1).put("limit", 100).put("totalItems", 1).put("totalPages", 1)
                .put("hasNext", false).put("hasPrev", false);
        return page.toString();
    }

    private static String notFound() {
        ObjectNode body = JSON.createObjectNode()
                .put("timestamp", Instant.now().toString())
                .put("path", "/api/v1/events/BATbern9/sessions")
                .put("status", 404)
                .put("error", "Not Found")
                .put("message", "Event not found: BATbern9")
                .put("severity", "LOW");
        return body.toString();
    }

    private LlmResponse use(String tool, JsonNode input) {
        return model.response(LlmStopReason.TOOL_USE, TOKENS, new LlmContent.ToolUse("tu_" + tool, tool, input));
    }

    private LlmResponse endTurn() {
        return model.response(LlmStopReason.END_TURN, TOKENS, new LlmContent.Text("Done."));
    }
}
