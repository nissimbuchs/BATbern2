package ch.batbern.agent.run;

import ch.batbern.agent.gate.GateFixtureTool;
import ch.batbern.agent.identity.CumsAtTheWire;
import ch.batbern.agent.log.InboundChannel;
import ch.batbern.agent.log.InboundMessage;
import ch.batbern.agent.loop.AgentLoop;
import ch.batbern.agent.loop.AgentRunResult;
import ch.batbern.agent.loop.LlmContent;
import ch.batbern.agent.loop.LlmStopReason;
import ch.batbern.agent.loop.LlmUsage;
import ch.batbern.agent.loop.ScriptedLlm;
import ch.batbern.agent.loop.TestTools;
import ch.batbern.agent.tools.AgentTool;
import ch.batbern.agent.tools.TestToolRegistry;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.OpenApiContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static ch.batbern.agent.identity.CumsAtTheWire.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 9 (R8, AD-9): an organizer reads a scripted run through the read API, with every step,
 * model call, tool call and verdict, and the precedents each verdict cites. The run goes through the
 * real loop and gate with the scripted model and CUMS and the gateway at the wire; the precedents
 * are seeded decision-log rows and past events the gateway answers. Every 2xx response is checked
 * against agent-api.openapi.yml by the response-conformance gate.
 */
@DisplayName("Organizers read every run, step and verdict through the console API")
@TestPropertySource(properties = "agent.enabled=true")
class RunLogReadIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENTS_CORE = "docs/api/events-core-api.openapi.yml";
    private static final String RUNS = "/api/v1/agent/runs/";
    private static final String SYSTEM = "You coordinate BATbern speakers.";
    private static final LlmUsage TOKENS = new LlmUsage(10, 5, 2, 1);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Timestamp EARLIER = Timestamp.from(Instant.parse("2026-10-01T08:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Autowired
    private AgentLoop loop;

    @Autowired
    private ScriptedLlm model;

    @Autowired
    private TestToolRegistry toolRegistry;

    @Autowired
    private AgentRunRepository runs;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RestTemplate restTemplate;

    private CumsAtTheWire cums;

    @BeforeEach
    @AfterEach
    void clean() {
        model.reset();
        toolRegistry.reset();
        jdbc.execute("TRUNCATE agent_runs CASCADE");
    }

    @BeforeEach
    void bindCums() {
        cums = CumsAtTheWire.bindTo(restTemplate, EVENTS_CORE).grants().mints();
    }

    @AfterEach
    void unbindCums() {
        cums.close();
    }

    @Test
    @DisplayName("an organizer reads a scripted run: detail, every step, model call, tool call and verdict with its precedents")
    void should_readEveryStepAndVerdict_when_organizerReadsScriptedRun() throws Exception {
        // The decision log the gate leans on: an approval of createCompany, an undo of rescheduleSession
        UUID decided = seededRun();
        UUID approval = approval(decided, toolCall(decided, "createCompany", 1));
        UUID undo = undo(decided, toolCall(decided, "rescheduleSession", 2));
        cums.server().expect(HttpMethod.GET, "/api/v1/events")
                .respondPerRequest(HttpStatus.OK, call -> pastEventsPage("BATbern58", "BATbern57"));

        AgentTool lookUp = TestTools.answering("lookUp", input -> "{\"found\":true}");
        GateFixtureTool reschedule = GateFixtureTool.reversible();
        GateFixtureTool createCompany = GateFixtureTool.binding();
        GateFixtureTool reply = GateFixtureTool.reply();
        ObjectNode topic = JSON.createObjectNode().put("topic", "Cloud Native");
        ObjectNode unresolved = JSON.createObjectNode();
        unresolved.putArray("to").add("not an address");
        model.then(model.response(LlmStopReason.TOOL_USE, TOKENS,
                        new LlmContent.ToolUse("tu_1", "lookUp", JSON.createObjectNode().put("q", "sessions")),
                        new LlmContent.ToolUse("tu_2", reschedule.name(), topic)))
             .then(model.response(LlmStopReason.TOOL_USE, TOKENS,
                        new LlmContent.ToolUse("tu_3", createCompany.name(), JSON.createObjectNode()),
                        new LlmContent.ToolUse("tu_4", reply.name(), unresolved)))
             .then(model.response(LlmStopReason.END_TURN, TOKENS, new LlmContent.Text("Done.")));
        InboundMessage inbound = new InboundMessage(InboundChannel.MAIL, "<read-api@example.com>",
                Instant.parse("2026-11-20T07:00:00Z"), JSON.createObjectNode().put("subject", "Session times"));
        AgentRunResult result = loop.run(toolRegistry.request(source(RunTrigger.ORGANIZER), SYSTEM,
                "Handle the request.", List.of(lookUp, reschedule, createCompany, reply), inbound));
        assertThat(result.reason()).isEqualTo(RunEndReason.COMPLETED);
        String run = RUNS + result.runId();

        // Run detail: why it ended, the totals, the inbound message
        JsonNode detail = getJson(run);
        assertThat(detail.at("/run/id").asText()).isEqualTo(result.runId().toString());
        assertThat(detail.at("/run/status").asText()).isEqualTo("DONE");
        assertThat(detail.at("/run/endReason").asText()).isEqualTo("COMPLETED");
        assertThat(detail.at("/run/trigger").asText()).isEqualTo("ORGANIZER");
        assertThat(detail.at("/run/principalKind").asText()).isEqualTo("ORGANIZER");
        assertThat(detail.at("/run/grantRunId").asText()).isEqualTo(jdbc.queryForObject(
                "SELECT grant_run_id FROM agent_runs WHERE id = ?", UUID.class, result.runId()).toString());
        assertThat(detail.at("/totals/steps").asLong()).isEqualTo(3);
        assertThat(detail.at("/totals/modelCalls").asLong()).isEqualTo(3);
        assertThat(detail.at("/totals/toolCalls").asLong()).isEqualTo(4);
        assertThat(detail.at("/totals/verdicts").asLong()).isEqualTo(3);
        assertThat(detail.at("/totals/pendingApprovals").asLong()).isEqualTo(1);
        assertThat(detail.at("/totals/inputTokens").asLong()).isEqualTo(30);
        assertThat(detail.at("/totals/outputTokens").asLong()).isEqualTo(15);
        assertThat(detail.at("/totals/cacheCreationInputTokens").asLong()).isEqualTo(6);
        assertThat(detail.at("/totals/cacheReadInputTokens").asLong()).isEqualTo(3);
        assertThat(detail.at("/inboundMessages/0/channel").asText()).isEqualTo("MAIL");
        assertThat(detail.at("/inboundMessages/0/externalId").asText()).isEqualTo("<read-api@example.com>");
        assertThat(detail.at("/inboundMessages/0/content/subject").asText()).isEqualTo("Session times");

        // Steps: three, in order, each with its model call and the tool calls it asked for
        JsonNode steps = getJson(run + "/steps");
        assertThat(numbers(steps.path("data"), "stepNumber")).containsExactly(1, 2, 3);
        assertThat(sizes(steps.path("data"), "modelCallIds")).containsExactly(1, 1, 1);
        assertThat(sizes(steps.path("data"), "toolCallIds")).containsExactly(2, 2, 0);
        assertThat(steps.at("/pagination/totalItems").asLong()).isEqualTo(3);

        // Model calls: tokens, stop reason and chosen tools; no prompt in the list
        JsonNode modelCalls = getJson(run + "/model-calls");
        assertThat(texts(modelCalls.path("data"), "stopReason")).containsExactly("TOOL_USE", "TOOL_USE", "END_TURN");
        JsonNode first = modelCalls.at("/data/0");
        assertThat(first.at("/usage/inputTokens").asLong()).isEqualTo(10);
        assertThat(first.at("/usage/outputTokens").asLong()).isEqualTo(5);
        assertThat(first.at("/usage/cacheCreationInputTokens").asLong()).isEqualTo(2);
        assertThat(first.at("/usage/cacheReadInputTokens").asLong()).isEqualTo(1);
        assertThat(texts(first.path("chosenTools"), "name")).containsExactly("lookUp", "rescheduleSession");
        assertThat(first.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(first.has("prompt")).isFalse();
        assertThat(first.path("id").asText()).isEqualTo(steps.at("/data/0/modelCallIds/0").asText());

        // Model-call detail: the full prompt and the answer, as sent and stored
        JsonNode call = getJson(run + "/model-calls/" + first.path("id").asText());
        assertThat(call.at("/modelCall/id").asText()).isEqualTo(first.path("id").asText());
        assertThat(call.at("/prompt/system").asText()).isEqualTo(SYSTEM);
        assertThat(call.at("/prompt/messages/0/content").toString()).contains("Handle the request.");
        assertThat(call.at("/prompt/tools").size()).isEqualTo(4);
        assertThat(call.at("/response/stop_reason").isMissingNode()).isFalse();

        // Tool calls: input, outcome, and the verdict of every gated call
        JsonNode toolCalls = getJson(run + "/tool-calls");
        assertThat(texts(toolCalls.path("data"), "toolName"))
                .containsExactly("lookUp", "rescheduleSession", "createCompany", "replyToRequester");
        assertThat(texts(toolCalls.path("data"), "status"))
                .containsExactly("SUCCEEDED", "SUCCEEDED", "PENDING_APPROVAL", "REFUSED");
        assertThat(numbers(toolCalls.path("data"), "stepNumber")).containsExactly(1, 1, 2, 2);
        assertThat(toolCalls.at("/data/0/verdictId").isNull() || toolCalls.at("/data/0/verdictId").isMissingNode())
                .isTrue();
        assertThat(toolCalls.at("/data/0/output").asText()).isEqualTo("{\"found\":true}");
        assertThat(toolCalls.at("/data/1/input/topic").asText()).isEqualTo("Cloud Native");
        assertThat(toolCalls.at("/data/3/output").asText()).contains("MAIL_UNRESOLVED");

        // Verdicts: class, outcome, rule, confidence, inputs, cited precedents resolved, approval
        JsonNode verdicts = getJson(run + "/verdicts");
        assertThat(verdicts.at("/pagination/totalItems").asLong()).isEqualTo(3);
        JsonNode ran = verdicts.at("/data/0");
        assertThat(ran.path("toolName").asText()).isEqualTo("rescheduleSession");
        assertThat(ran.path("toolCallId").asText()).isEqualTo(toolCalls.at("/data/1/id").asText());
        assertThat(toolCalls.at("/data/1/verdictId").asText()).isEqualTo(ran.path("id").asText());
        assertThat(ran.path("operationClass").asText()).isEqualTo("REVERSIBLE");
        assertThat(ran.path("outcome").asText()).isEqualTo("RUN");
        assertThat(ran.path("reason").asText()).isEqualTo("RUNS_ALONE");
        assertThat(ran.at("/inputs/precedents/pastEvents").asInt()).isEqualTo(2);
        assertThat(texts(ran.path("citedPrecedents"), "sourceId"))
                .containsExactly("agent_undos:" + undo, "event:BATbern58", "event:BATbern57");
        JsonNode undoCited = ran.at("/citedPrecedents/0");
        assertThat(undoCited.path("source").asText()).isEqualTo("UNDO");
        assertThat(undoCited.path("resolved").asBoolean()).isTrue();
        assertThat(undoCited.path("toolName").asText()).isEqualTo("rescheduleSession");
        assertThat(undoCited.path("runId").asText()).isEqualTo(decided.toString());
        assertThat(undoCited.path("undoOutcome").asText()).isEqualTo("SUCCEEDED");
        assertThat(undoCited.path("at").asText()).isEqualTo("2026-10-06T08:00:00Z");
        assertThat(ran.at("/citedPrecedents/1/source").asText()).isEqualTo("PAST_EVENT");
        assertThat(ran.at("/citedPrecedents/1/eventCode").asText()).isEqualTo("BATbern58");
        assertThat(ran.path("approval").isNull() || ran.path("approval").isMissingNode()).isTrue();

        JsonNode pending = verdicts.at("/data/1");
        assertThat(pending.path("toolName").asText()).isEqualTo("createCompany");
        assertThat(pending.path("operationClass").asText()).isEqualTo("BINDING");
        assertThat(pending.path("outcome").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(pending.path("reason").asText()).isEqualTo("BINDING_BELOW_THRESHOLD");
        assertThat(pending.path("confidence").decimalValue()).isEqualByComparingTo("0.5");
        assertThat(texts(pending.path("citedPrecedents"), "sourceId")).containsExactly("agent_approvals:" + approval);
        JsonNode approvalCited = pending.at("/citedPrecedents/0");
        assertThat(approvalCited.path("source").asText()).isEqualTo("APPROVAL");
        assertThat(approvalCited.path("approvalState").asText()).isEqualTo("APPROVED");
        assertThat(approvalCited.path("toolName").asText()).isEqualTo("createCompany");
        assertThat(approvalCited.path("runId").asText()).isEqualTo(decided.toString());
        assertThat(pending.at("/approval/state").asText()).isEqualTo("PENDING");
        assertThat(pending.at("/approval/id").asText()).isEqualTo(jdbc.queryForObject(
                "SELECT a.id FROM agent_approvals a JOIN agent_verdicts v ON v.id = a.verdict_id WHERE v.run_id = ?",
                UUID.class, result.runId()).toString());

        JsonNode refused = verdicts.at("/data/2");
        assertThat(refused.path("toolName").asText()).isEqualTo("replyToRequester");
        assertThat(refused.path("outcome").asText()).isEqualTo("REFUSED");
        assertThat(refused.path("reason").asText()).isEqualTo("MAIL_UNRESOLVED");
        assertThat(refused.path("citedPrecedents").size()).isZero();
    }

    @Test
    @DisplayName("a run stopped by a cap names the limit; a refused grant names the CUMS code")
    void should_showEndReasonAndDetail_when_runHandedOver() throws Exception {
        Instant at = Instant.parse("2026-11-20T08:00:00Z");
        AgentRunEntity capped = AgentRunEntity.started(UUID.randomUUID(), RunTrigger.INBOUND_MAIL, at);
        capped.end(RunEndReason.CAP_REACHED, RunLimit.STEPS.name(), at.plusSeconds(60));
        AgentRunEntity refused = AgentRunEntity.started(UUID.randomUUID(), RunTrigger.SCHEDULED, at);
        refused.end(RunEndReason.GRANT_REFUSED, "AGENT_SWITCHED_OFF", at.plusSeconds(1));
        runs.saveAll(List.of(capped, refused));

        JsonNode cap = getJson(RUNS + capped.getId());
        assertThat(cap.at("/run/status").asText()).isEqualTo("HANDED_TO_ORGANIZERS");
        assertThat(cap.at("/run/endReason").asText()).isEqualTo("CAP_REACHED");
        assertThat(cap.at("/run/endDetail").asText()).isEqualTo("STEPS");
        assertThat(cap.at("/totals/steps").asLong()).isZero();
        assertThat(cap.path("inboundMessages").size()).isZero();

        JsonNode grant = getJson(RUNS + refused.getId());
        assertThat(grant.at("/run/endReason").asText()).isEqualTo("GRANT_REFUSED");
        assertThat(grant.at("/run/endDetail").asText()).isEqualTo("AGENT_SWITCHED_OFF");

        // The list carries the same end reason and detail
        JsonNode list = getJson("/api/v1/agent/runs");
        assertThat(texts(list.path("data"), "endDetail")).containsExactlyInAnyOrder("STEPS", "AGENT_SWITCHED_OFF");
    }

    @Test
    @DisplayName("sub-collections page in step order; a limit above 100 answers 400")
    void should_pageSteps_when_limitIsOne() throws Exception {
        UUID runId = seededRun();
        toolCall(runId, "a", 1);
        toolCall(runId, "b", 2);
        toolCall(runId, "c", 3);

        JsonNode second = getJson(RUNS + runId + "/steps?page=1&limit=1");
        assertThat(numbers(second.path("data"), "stepNumber")).containsExactly(2);
        assertThat(second.at("/pagination/page").asInt()).isEqualTo(1);
        assertThat(second.at("/pagination/limit").asInt()).isEqualTo(1);
        assertThat(second.at("/pagination/totalItems").asLong()).isEqualTo(3);
        assertThat(second.at("/pagination/totalPages").asInt()).isEqualTo(3);
        assertThat(second.at("/pagination/hasNext").asBoolean()).isTrue();
        assertThat(second.at("/pagination/hasPrev").asBoolean()).isTrue();

        JsonNode last = getJson(RUNS + runId + "/tool-calls?page=2&limit=1");
        assertThat(texts(last.path("data"), "toolName")).containsExactly("c");
        assertThat(last.at("/pagination/hasNext").asBoolean()).isFalse();

        JsonNode empty = getJson(RUNS + runId + "/verdicts");
        assertThat(empty.path("data").size()).isZero();
        assertThat(empty.at("/pagination/totalPages").asInt()).isZero();

        mockMvc.perform(get(RUNS + runId + "/steps").param("limit", "101").with(organizer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an unknown run, or a model call of another run, answers 404 with the shared error envelope")
    void should_return404_when_runOrModelCallUnknown() throws Exception {
        UUID unknown = UUID.randomUUID();
        for (String path : List.of("", "/steps", "/model-calls", "/tool-calls", "/verdicts",
                "/model-calls/" + UUID.randomUUID())) {
            String body = mockMvc.perform(get(RUNS + unknown + path).with(organizer()))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();
            JsonNode error = JSON.readTree(body);
            assertThat(error.path("status").asInt()).isEqualTo(404);
            assertThat(error.path("errorCode").asText()).isEqualTo("ERR_NOT_FOUND");
        }

        // A model call that exists, asked for under another run that exists
        UUID owner = seededRun();
        UUID other = seededRun();
        toolCall(owner, "seed", 1);
        UUID step = jdbc.queryForObject("SELECT id FROM agent_steps WHERE run_id = ?", UUID.class, owner);
        UUID modelCall = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_model_calls (id, run_id, step_id, status, prompt, started_at) "
                + "VALUES (?, ?, ?, 'STARTED', '{\"system\":\"s\"}'::jsonb, ?)", modelCall, owner, step, EARLIER);
        // Positive control: under its own run it is found, with no answer yet
        JsonNode own = getJson(RUNS + owner + "/model-calls/" + modelCall);
        assertThat(own.at("/modelCall/status").asText()).isEqualTo("STARTED");
        mockMvc.perform(get(RUNS + other + "/model-calls/" + modelCall).with(organizer()))
                .andExpect(status().isNotFound());
    }

    // ── Reading ──────────────────────────────────────────────────────────────────────────────────

    private JsonNode getJson(String path) throws Exception {
        String body = mockMvc.perform(get(path).with(organizer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private JwtRequestPostProcessor organizer() {
        Converter<Jwt, Collection<GrantedAuthority>> realConversion =
                token -> jwtAuthenticationConverter.convert(token).getAuthorities();
        return jwt()
                .jwt(token -> token.subject("run-log-organizer")
                        .claim("custom:role", "ORGANIZER")
                        .claim("custom:username", "run.log.organizer"))
                .authorities(realConversion);
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.path(field).asText()));
        return values;
    }

    private static List<Integer> numbers(JsonNode array, String field) {
        List<Integer> values = new ArrayList<>();
        array.forEach(item -> values.add(item.path(field).asInt()));
        return values;
    }

    private static List<Integer> sizes(JsonNode array, String field) {
        List<Integer> values = new ArrayList<>();
        array.forEach(item -> values.add(item.path(field).size()));
        return values;
    }

    // ── Seeding the decision log ─────────────────────────────────────────────────────────────────

    private UUID seededRun() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_runs (id, status, started_at, ended_at, trigger, end_reason, grant_run_id, "
                + "principal_kind) VALUES (?, 'DONE', ?, ?, 'ORGANIZER', 'COMPLETED', ?, 'ORGANIZER')",
                id, EARLIER, EARLIER, UUID.randomUUID());
        return id;
    }

    private UUID toolCall(UUID runId, String toolName, int stepNumber) {
        UUID step = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_steps (id, run_id, step_number, started_at) VALUES (?, ?, ?, ?)",
                step, runId, stepNumber, EARLIER);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_tool_calls (id, run_id, step_id, tool_use_id, tool_name, input, status, "
                + "started_at) VALUES (?, ?, ?, ?, ?, '{}'::jsonb, 'STARTED', ?)", id, runId, step,
                "toolu_seed_" + stepNumber, toolName, Timestamp.from(EARLIER.toInstant().plusSeconds(stepNumber)));
        return id;
    }

    private UUID approval(UUID runId, UUID toolCallId) {
        UUID verdict = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_verdicts (id, run_id, tool_call_id, operation_class, outcome, inputs, "
                + "cited_precedents, decided_at) VALUES (?, ?, ?, 'BINDING', 'PENDING_APPROVAL', '{}'::jsonb, "
                + "'[]'::jsonb, ?)", verdict, runId, toolCallId, EARLIER);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_approvals (id, run_id, verdict_id, state, decided_by, created_at, decided_at) "
                + "VALUES (?, ?, ?, 'APPROVED', 'organizer.test', ?, ?)", id, runId, verdict, EARLIER,
                Timestamp.from(Instant.parse("2026-10-05T08:00:00Z")));
        return id;
    }

    private UUID undo(UUID runId, UUID toolCallId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_undos (id, run_id, tool_call_id, undo_operation, outcome, performed_by, "
                + "performed_at) VALUES (?, ?, ?, 'PATCH /events/{eventCode}/sessions/{sessionSlug}', 'SUCCEEDED', "
                + "'organizer.test', ?)", id, runId, toolCallId, Timestamp.from(Instant.parse("2026-10-06T08:00:00Z")));
        return id;
    }

    /** One page of past events: the spec's example event per code, newest first. */
    private static String pastEventsPage(String... codes) {
        try {
            JsonNode example = JSON.readTree(OpenApiContract.load(EVENTS_CORE).example("GET", "/api/v1/events", 200,
                    null)).path("data").get(0);
            ObjectNode page = JSON.createObjectNode();
            ArrayNode data = page.putArray("data");
            for (String code : codes) {
                ObjectNode event = example.deepCopy();
                event.put("eventCode", code).put("eventNumber", Integer.parseInt(code.substring("BATbern".length())))
                        .put("title", code + " title");
                data.add(event);
            }
            page.putObject("pagination").put("page", 1).put("limit", GateFixtureTool.PAST_EVENT_LIMIT)
                    .put("totalItems", codes.length).put("totalPages", 1).put("hasNext", false).put("hasPrev", false);
            return page.toString();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
