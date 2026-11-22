package ch.batbern.agent.loop;

import ch.batbern.agent.log.InboundChannel;
import ch.batbern.agent.log.InboundMessage;
import ch.batbern.agent.identity.CumsAtTheWire;
import ch.batbern.agent.identity.GrantSource;
import ch.batbern.agent.loop.TestTools.RecordingTool;
import ch.batbern.agent.run.AgentRunStatus;
import ch.batbern.agent.run.RunEndReason;
import ch.batbern.agent.run.RunTrigger;
import ch.batbern.agent.tools.ToolOutcome;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.batbern.agent.identity.CumsAtTheWire.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * The agent loop end to end against the real decision log on PostgreSQL, with the model scripted
 * (the model is a process boundary). Every row of the story's I/O matrix but the switch, which
 * {@code AgentSwitchOffIntegrationTest} covers in the default context. Rows are read through
 * {@link JdbcTemplate}, not the entities, so the stored shape is what is asserted.
 */
@DisplayName("AgentLoop runs a scripted model and logs every step")
@TestPropertySource(properties = {"agent.enabled=true", "agent.loop.max-steps=4"})
class AgentLoopIntegrationTest extends AbstractIntegrationTest {

    private static final GrantSource ORGANIZER_LOGIN = source(RunTrigger.ORGANIZER);
    private static final String SYSTEM = "You coordinate BATbern speakers.";
    private static final LlmUsage NO_TOKENS = new LlmUsage(1, 1, 0, 0);

    @Autowired
    private AgentLoop loop;

    @Autowired
    private ScriptedLlm model;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RestTemplate restTemplate;

    /** CUMS grants and mints at the wire, spec-validated; every run obtains a grant and a token first. */
    private CumsAtTheWire cums;

    @BeforeEach
    @AfterEach
    void clean() {
        model.reset();
        jdbc.execute("TRUNCATE agent_runs CASCADE");
    }

    @BeforeEach
    void bindCums() {
        cums = CumsAtTheWire.bindTo(restTemplate).grants().mints();
    }

    @AfterEach
    void unbindCums() {
        cums.close();
    }

    @Test
    @DisplayName("three-step run: DONE, every model call with tokens and tool choice, every step and tool call")
    void should_logEveryStep_when_modelUsesToolsThenEndsTurn() {
        RecordingTool lookup = TestTools.answering("lookup_event", in -> "event BATbern61 on 2027-03-05");
        RecordingTool slots = TestTools.answering("list_slots", in -> "slots: 14:00, 15:30");
        RecordingTool speaker = TestTools.answering("find_speaker", in -> "speaker found: " + in.get("query").asText());
        model.then(model.response(LlmStopReason.TOOL_USE, new LlmUsage(100, 20, 5, 0),
                        new LlmContent.Text("Let me look up the event."),
                        new LlmContent.ToolUse("tu_1", "lookup_event", TestTools.input("query", "BATbern61"))))
             .then(model.response(LlmStopReason.TOOL_USE, new LlmUsage(150, 30, 0, 95),
                        new LlmContent.ToolUse("tu_2", "list_slots", TestTools.input("query", "afternoon")),
                        new LlmContent.ToolUse("tu_3", "find_speaker", TestTools.input("query", "architecture"))))
             .then(model.response(LlmStopReason.END_TURN, new LlmUsage(210, 40, 0, 140),
                        new LlmContent.Text("The slot 14:00 is free.")));
        InboundMessage inbound = new InboundMessage(InboundChannel.MAIL, "<msg-1@example.com>",
                Instant.parse("2026-11-22T09:00:00Z"), TestTools.input("subject", "Slot for my talk"));

        AgentRunResult result = loop.run(new AgentRunRequest(source(RunTrigger.INBOUND_MAIL), SYSTEM,
                "Which slot is free?", List.of(lookup, slots, speaker), inbound));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        assertThat(result.reason()).isEqualTo(RunEndReason.COMPLETED);
        assertThat(result.steps()).isEqualTo(3);
        assertThat(model.exhausted()).isTrue();
        UUID runId = result.runId();

        // The run
        Map<String, Object> run = jdbc.queryForMap(
                "SELECT status, trigger, end_reason, ended_at FROM agent_runs WHERE id = ?", runId);
        assertThat(run).containsEntry("status", "DONE").containsEntry("trigger", "INBOUND_MAIL")
                .containsEntry("end_reason", "COMPLETED");
        assertThat(run.get("ended_at")).isNotNull();

        // The inbound message, in full
        Map<String, Object> message = jdbc.queryForMap("SELECT channel, external_id, content->>'subject' AS subject "
                + "FROM agent_inbound_messages WHERE run_id = ?", runId);
        assertThat(message).containsEntry("channel", "MAIL").containsEntry("external_id", "<msg-1@example.com>")
                .containsEntry("subject", "Slot for my talk");

        // The steps, numbered, each ended
        List<Map<String, Object>> steps = jdbc.queryForList(
                "SELECT step_number, ended_at FROM agent_steps WHERE run_id = ? ORDER BY step_number", runId);
        assertThat(steps).extracting(row -> row.get("step_number")).containsExactly(1, 2, 3);
        assertThat(steps).allSatisfy(row -> assertThat(row.get("ended_at")).isNotNull());

        // The model calls: one per step, with tokens, stop reason, model and the tools chosen
        List<Map<String, Object>> calls = jdbc.queryForList("SELECT s.step_number, m.status, m.model, m.stop_reason, "
                + "m.input_tokens, m.output_tokens, m.cache_creation_input_tokens, m.cache_read_input_tokens, "
                + "(SELECT string_agg(t->>'name', ',' ORDER BY ord) FROM jsonb_array_elements(m.chosen_tools) "
                + "WITH ORDINALITY AS c(t, ord)) AS chosen, "
                + "m.prompt->>'system' AS system, (m.prompt->>'max_tokens')::int AS max_tokens, "
                + "jsonb_array_length(m.prompt->'messages') AS message_count, "
                + "m.prompt->'messages'->0->'content'->0->>'text' AS first_text, "
                + "(SELECT string_agg(t->>'name', ',' ORDER BY ord) FROM jsonb_array_elements(m.prompt->'tools') "
                + "WITH ORDINALITY AS c(t, ord)) AS offered, "
                + "m.response->'content'->0->>'type' AS first_block, m.error "
                + "FROM agent_model_calls m JOIN agent_steps s ON s.id = m.step_id "
                + "WHERE m.run_id = ? ORDER BY s.step_number", runId);
        assertThat(calls).hasSize(3);
        assertThat(calls).allSatisfy(call -> assertThat(call)
                .containsEntry("status", "SUCCEEDED")
                .containsEntry("model", ScriptedLlm.MODEL)
                .containsEntry("system", SYSTEM)
                .containsEntry("max_tokens", 16000)
                .containsEntry("first_text", "Which slot is free?")
                .containsEntry("offered", "lookup_event,list_slots,find_speaker")
                .containsEntry("error", null));
        assertThat(calls).extracting(c -> c.get("stop_reason"), c -> c.get("chosen"), c -> c.get("message_count"))
                .containsExactly(
                        tuple("TOOL_USE", "lookup_event", 1),
                        tuple("TOOL_USE", "list_slots,find_speaker", 3),
                        tuple("END_TURN", null, 5));
        assertThat(calls).extracting(c -> c.get("input_tokens"), c -> c.get("output_tokens"),
                        c -> c.get("cache_creation_input_tokens"), c -> c.get("cache_read_input_tokens"))
                .containsExactly(
                        tuple(100L, 20L, 5L, 0L),
                        tuple(150L, 30L, 0L, 95L),
                        tuple(210L, 40L, 0L, 140L));
        assertThat(calls).extracting(c -> c.get("first_block")).containsExactly("text", "tool_use", "text");

        // The tool calls: input and output, in the step that asked for them
        List<Map<String, Object>> tools = jdbc.queryForList("SELECT s.step_number, t.tool_use_id, t.tool_name, "
                + "t.input->>'query' AS query, t.status, t.output, t.error, t.ended_at "
                + "FROM agent_tool_calls t JOIN agent_steps s ON s.id = t.step_id "
                + "WHERE t.run_id = ? ORDER BY s.step_number, t.started_at, t.tool_use_id", runId);
        assertThat(tools).extracting(t -> t.get("step_number"), t -> t.get("tool_use_id"), t -> t.get("tool_name"),
                        t -> t.get("query"), t -> t.get("status"), t -> t.get("output"), t -> t.get("error"))
                .containsExactly(
                        tuple(1, "tu_1", "lookup_event", "BATbern61", "SUCCEEDED",
                                "event BATbern61 on 2027-03-05", null),
                        tuple(2, "tu_2", "list_slots", "afternoon", "SUCCEEDED",
                                "slots: 14:00, 15:30", null),
                        tuple(2, "tu_3", "find_speaker", "architecture", "SUCCEEDED",
                                "speaker found: architecture", null));
        assertThat(tools).allSatisfy(t -> assertThat(t.get("ended_at")).isNotNull());

        // What the model got back: the third request holds both results of step 2 as one user message
        LlmRequest third = model.requests().get(2);
        assertThat(third.messages()).extracting(LlmMessage::role).containsExactly(
                LlmMessage.Role.USER, LlmMessage.Role.ASSISTANT, LlmMessage.Role.USER,
                LlmMessage.Role.ASSISTANT, LlmMessage.Role.USER);
        assertThat(third.messages().get(4).content()).containsExactly(
                new LlmContent.ToolResult("tu_2", "slots: 14:00, 15:30", false),
                new LlmContent.ToolResult("tu_3", "speaker found: architecture", false));
        assertThat(lookup.inputs()).hasSize(1);
        assertThat(slots.inputs()).hasSize(1);
        assertThat(speaker.inputs()).hasSize(1);
    }

    @Test
    @DisplayName("thinking blocks of step 1 go back to the model unchanged on step 2, and are logged")
    void should_returnThinkingUnchanged_when_modelThoughtBeforeToolUse() {
        RecordingTool lookup = TestTools.answering("lookup_event", in -> "event BATbern61 on 2027-03-05");
        LlmContent.Thinking thinking = new LlmContent.Thinking("", "sig-of-step-1");
        LlmContent.RedactedThinking redacted = new LlmContent.RedactedThinking("encrypted-step-1");
        LlmContent.ToolUse use = new LlmContent.ToolUse("tu_1", "lookup_event", TestTools.input("query", "BATbern61"));
        model.then(model.response(LlmStopReason.TOOL_USE, NO_TOKENS, thinking, redacted, use))
             .then(model.response(LlmStopReason.END_TURN, NO_TOKENS, new LlmContent.Text("Done.")));

        AgentRunResult result = loop.run(new AgentRunRequest(ORGANIZER_LOGIN, SYSTEM, "Which event?",
                List.of(lookup)));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        LlmRequest second = model.requests().get(1);
        assertThat(second.messages().get(1).role()).isEqualTo(LlmMessage.Role.ASSISTANT);
        assertThat(second.messages().get(1).content()).containsExactly(thinking, redacted, use);

        // The decision log stores them in the Messages API's shape
        Map<String, Object> logged = jdbc.queryForMap("SELECT "
                + "m.response->'content'->0->>'type' AS t0, m.response->'content'->0->>'signature' AS sig, "
                + "m.response->'content'->1->>'type' AS t1, m.response->'content'->1->>'data' AS data "
                + "FROM agent_model_calls m JOIN agent_steps s ON s.id = m.step_id "
                + "WHERE m.run_id = ? AND s.step_number = 1", result.runId());
        assertThat(logged).containsEntry("t0", "thinking").containsEntry("sig", "sig-of-step-1")
                .containsEntry("t1", "redacted_thinking").containsEntry("data", "encrypted-step-1");
        String secondPrompt = jdbc.queryForObject("SELECT m.prompt->'messages'->1->'content'->0->>'signature' "
                + "FROM agent_model_calls m JOIN agent_steps s ON s.id = m.step_id "
                + "WHERE m.run_id = ? AND s.step_number = 2", String.class, result.runId());
        assertThat(secondPrompt).isEqualTo("sig-of-step-1");
    }

    @Test
    @DisplayName("model unreachable: HANDED_TO_ORGANIZERS, MODEL_UNAVAILABLE, the failed model call keeps the error, no retry")
    void should_handToOrganizers_when_modelUnavailable() {
        RecordingTool lookup = TestTools.answering("lookup_event", in -> "never");
        model.thenThrow(new LlmUnavailableException("connect timed out"));

        AgentRunResult result = loop.run(new AgentRunRequest(ORGANIZER_LOGIN, SYSTEM, "Hello", List.of(lookup)));

        assertThat(result.status()).isEqualTo(AgentRunStatus.HANDED_TO_ORGANIZERS);
        assertThat(result.reason()).isEqualTo(RunEndReason.MODEL_UNAVAILABLE);
        assertThat(model.requests()).as("no retry").hasSize(1);
        // Positive control: the run is logged, so "no tool call" below is about this run
        assertThat(jdbc.queryForMap("SELECT status, end_reason FROM agent_runs WHERE id = ?", result.runId()))
                .containsEntry("status", "HANDED_TO_ORGANIZERS").containsEntry("end_reason", "MODEL_UNAVAILABLE");
        Map<String, Object> call = jdbc.queryForMap("SELECT status, error, prompt->>'system' AS system, response, "
                + "ended_at FROM agent_model_calls WHERE run_id = ?", result.runId());
        assertThat(call).containsEntry("status", "FAILED").containsEntry("system", SYSTEM)
                .containsEntry("response", null);
        assertThat((String) call.get("error"))
                .isEqualTo(LlmUnavailableException.class.getName() + ": connect timed out");
        assertThat(call.get("ended_at")).isNotNull();
        assertThat(countToolCalls(result.runId())).isZero();
        assertThat(lookup.inputs()).isEmpty();
    }

    @Test
    @DisplayName("unknown tool: UNKNOWN_TOOL, an error tool_result goes back, the loop continues")
    void should_returnErrorResult_when_modelAsksForUnknownTool() {
        RecordingTool lookup = TestTools.answering("lookup_event", in -> "never");
        model.then(model.response(LlmStopReason.TOOL_USE, NO_TOKENS,
                        new LlmContent.ToolUse("tu_x", "delete_everything", TestTools.input("query", "all"))))
             .then(model.response(LlmStopReason.END_TURN, NO_TOKENS, new LlmContent.Text("Sorry.")));

        AgentRunResult result = loop.run(new AgentRunRequest(ORGANIZER_LOGIN, SYSTEM, "Hello", List.of(lookup)));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        assertThat(result.steps()).isEqualTo(2);
        Map<String, Object> call = jdbc.queryForMap("SELECT tool_name, status, input->>'query' AS query, output, error "
                + "FROM agent_tool_calls WHERE run_id = ?", result.runId());
        assertThat(call).containsEntry("tool_name", "delete_everything").containsEntry("status", "UNKNOWN_TOOL")
                .containsEntry("query", "all").containsEntry("output", null);
        assertThat((String) call.get("error")).contains("lookup_event");
        LlmContent.ToolResult back = lastToolResult(model.requests().get(1));
        assertThat(back.toolUseId()).isEqualTo("tu_x");
        assertThat(back.isError()).isTrue();
        assertThat(lookup.inputs()).isEmpty();
    }

    @Test
    @DisplayName("tool throws: FAILED with the error, an error tool_result goes back, the loop continues")
    void should_returnErrorResult_when_toolThrows() {
        RecordingTool broken = TestTools.throwing("lookup_event", new IllegalStateException("upstream 503"));
        model.then(model.response(LlmStopReason.TOOL_USE, NO_TOKENS,
                        new LlmContent.ToolUse("tu_b", "lookup_event", TestTools.input("query", "BATbern61"))))
             .then(model.response(LlmStopReason.END_TURN, NO_TOKENS, new LlmContent.Text("It failed.")));

        AgentRunResult result = loop.run(new AgentRunRequest(ORGANIZER_LOGIN, SYSTEM, "Hello", List.of(broken)));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        Map<String, Object> call = jdbc.queryForMap("SELECT status, output, error, ended_at FROM agent_tool_calls "
                + "WHERE run_id = ?", result.runId());
        assertThat(call).containsEntry("status", "FAILED").containsEntry("output", null)
                .containsEntry("error", IllegalStateException.class.getName() + ": upstream 503");
        assertThat(call.get("ended_at")).isNotNull();
        LlmContent.ToolResult back = lastToolResult(model.requests().get(1));
        assertThat(back.toolUseId()).isEqualTo("tu_b");
        assertThat(back.isError()).isTrue();
        assertThat(back.content()).doesNotContain("upstream 503");
        assertThat(broken.inputs()).hasSize(1);
    }

    @Test
    @DisplayName("a tool's own error result is ERRORED and reaches the model as is_error")
    void should_logErrored_when_toolReportsError() {
        RecordingTool refusing = new RecordingTool("lookup_event",
                in -> ToolOutcome.error("no such event"));
        model.then(model.response(LlmStopReason.TOOL_USE, NO_TOKENS,
                        new LlmContent.ToolUse("tu_e", "lookup_event", TestTools.input("query", "BATbern99"))))
             .then(model.response(LlmStopReason.END_TURN, NO_TOKENS, new LlmContent.Text("Not found.")));

        AgentRunResult result = loop.run(new AgentRunRequest(ORGANIZER_LOGIN, SYSTEM, "Hello", List.of(refusing)));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        assertThat(jdbc.queryForMap("SELECT status, output FROM agent_tool_calls WHERE run_id = ?", result.runId()))
                .containsEntry("status", "ERRORED").containsEntry("output", "no such event");
        assertThat(lastToolResult(model.requests().get(1)))
                .isEqualTo(new LlmContent.ToolResult("tu_e", "no such event", true));
    }

    @Test
    @DisplayName("output cut off: HANDED_TO_ORGANIZERS, MODEL_MAX_TOKENS")
    void should_handToOrganizers_when_outputCutOff() {
        model.then(model.response(LlmStopReason.MAX_TOKENS, new LlmUsage(10, 4096, 0, 0),
                new LlmContent.Text("The answer is")));

        AgentRunResult result = loop.run(new AgentRunRequest(source(RunTrigger.SCHEDULED), SYSTEM, "Hello", List.of()));

        assertThat(result.status()).isEqualTo(AgentRunStatus.HANDED_TO_ORGANIZERS);
        assertThat(result.reason()).isEqualTo(RunEndReason.MODEL_MAX_TOKENS);
        assertThat(jdbc.queryForMap("SELECT status, end_reason, trigger FROM agent_runs WHERE id = ?", result.runId()))
                .containsEntry("status", "HANDED_TO_ORGANIZERS").containsEntry("end_reason", "MODEL_MAX_TOKENS")
                .containsEntry("trigger", "SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT output_tokens FROM agent_model_calls WHERE run_id = ?",
                Long.class, result.runId())).isEqualTo(4096L);
    }

    @ParameterizedTest
    @EnumSource(value = LlmStopReason.class, names = {"REFUSAL", "UNKNOWN", "STOP_SEQUENCE", "PAUSE_TURN"})
    @DisplayName("model refuses or stops otherwise: HANDED_TO_ORGANIZERS, MODEL_STOPPED")
    void should_handToOrganizers_when_modelStopsOtherwise(LlmStopReason stopReason) {
        model.then(model.response(stopReason, NO_TOKENS, new LlmContent.Text("I cannot help with that.")));

        AgentRunResult result = loop.run(new AgentRunRequest(ORGANIZER_LOGIN, SYSTEM, "Hello", List.of()));

        assertThat(result.status()).isEqualTo(AgentRunStatus.HANDED_TO_ORGANIZERS);
        assertThat(result.reason()).isEqualTo(RunEndReason.MODEL_STOPPED);
        assertThat(jdbc.queryForObject("SELECT stop_reason FROM agent_model_calls WHERE run_id = ?",
                String.class, result.runId())).isEqualTo(stopReason.name());
        assertThat(jdbc.queryForObject("SELECT end_reason FROM agent_runs WHERE id = ?",
                String.class, result.runId())).isEqualTo("MODEL_STOPPED");
    }

    @Test
    @DisplayName("tool_use stop without a tool_use block: HANDED_TO_ORGANIZERS, MODEL_STOPPED")
    void should_handToOrganizers_when_toolUseStopHasNoToolUse() {
        model.then(model.response(LlmStopReason.TOOL_USE, NO_TOKENS, new LlmContent.Text("Calling...")));

        AgentRunResult result = loop.run(new AgentRunRequest(ORGANIZER_LOGIN, SYSTEM, "Hello", List.of()));

        assertThat(result.reason()).isEqualTo(RunEndReason.MODEL_STOPPED);
    }

    @Test
    @DisplayName("step ceiling: a model still calling tools after max-steps model calls hands the run over, STEP_LIMIT")
    void should_handToOrganizers_when_stepLimitReached() {
        RecordingTool lookup = TestTools.answering("lookup_event", in -> "again");
        for (int i = 1; i <= 5; i++) {
            model.then(model.response(LlmStopReason.TOOL_USE, NO_TOKENS,
                    new LlmContent.ToolUse("tu_" + i, "lookup_event", TestTools.input("query", "q" + i))));
        }

        AgentRunResult result = loop.run(new AgentRunRequest(ORGANIZER_LOGIN, SYSTEM, "Hello", List.of(lookup)));

        assertThat(result.status()).isEqualTo(AgentRunStatus.HANDED_TO_ORGANIZERS);
        assertThat(result.reason()).isEqualTo(RunEndReason.STEP_LIMIT);
        assertThat(result.steps()).isEqualTo(4);
        assertThat(model.requests()).as("max-steps=4 model calls, no fifth").hasSize(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_steps WHERE run_id = ?", Integer.class,
                result.runId())).isEqualTo(4);
        assertThat(countToolCalls(result.runId())).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT end_reason FROM agent_runs WHERE id = ?", String.class,
                result.runId())).isEqualTo("STEP_LIMIT");
    }

    @Test
    @DisplayName("a fault in agent-service: FAILED, INTERNAL_ERROR, the trace up to the fault stays")
    void should_fail_when_unexpectedRuntimeException() {
        RecordingTool lookup = TestTools.answering("lookup_event", in -> "found");
        model.then(model.response(LlmStopReason.TOOL_USE, NO_TOKENS,
                        new LlmContent.ToolUse("tu_1", "lookup_event", TestTools.input("query", "BATbern61"))))
             // Not LlmUnavailableException: an adapter bug is agent-service's own fault
             .thenThrow(new IllegalStateException("adapter bug"));

        AgentRunResult result = loop.run(new AgentRunRequest(ORGANIZER_LOGIN, SYSTEM, "Hello", List.of(lookup)));

        assertThat(result.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.reason()).isEqualTo(RunEndReason.INTERNAL_ERROR);
        assertThat(jdbc.queryForMap("SELECT status, end_reason FROM agent_runs WHERE id = ?", result.runId()))
                .containsEntry("status", "FAILED").containsEntry("end_reason", "INTERNAL_ERROR");
        // The trace: step 1 complete, step 2's model call FAILED with its prompt, step 2 ended
        assertThat(jdbc.queryForList("SELECT s.step_number, m.status FROM agent_model_calls m "
                        + "JOIN agent_steps s ON s.id = m.step_id WHERE m.run_id = ? ORDER BY s.step_number",
                result.runId()))
                .extracting(row -> row.get("step_number"), row -> row.get("status"))
                .containsExactly(tuple(1, "SUCCEEDED"),
                        tuple(2, "FAILED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_steps WHERE run_id = ? AND ended_at IS NULL",
                Integer.class, result.runId())).isZero();
        assertThat(countToolCalls(result.runId())).isEqualTo(1);
    }

    @Test
    @DisplayName("an Error from a tool: the run ends FAILED, INTERNAL_ERROR, and the Error goes on")
    void should_endRunFailed_when_toolThrowsError() {
        AssertionError error = new AssertionError("tool invariant broken");
        RecordingTool broken = new RecordingTool("lookup_event", in -> {
            throw error;
        });
        model.then(model.response(LlmStopReason.TOOL_USE, NO_TOKENS,
                new LlmContent.ToolUse("tu_1", "lookup_event", TestTools.input("query", "BATbern61"))));

        assertThatThrownBy(() -> loop.run(new AgentRunRequest(ORGANIZER_LOGIN, SYSTEM, "Hello", List.of(broken))))
                .isSameAs(error);

        assertThat(broken.inputs()).as("the tool ran").hasSize(1);
        assertThat(jdbc.queryForList("SELECT status, end_reason, ended_at FROM agent_runs"))
                .singleElement()
                .satisfies(run -> {
                    assertThat(run).containsEntry("status", "FAILED").containsEntry("end_reason", "INTERNAL_ERROR");
                    assertThat(run.get("ended_at")).isNotNull();
                });
    }

    @Test
    @DisplayName("a port fault other than unavailable: the model call is FAILED, the run FAILED")
    void should_failModelCall_when_portThrowsOtherRuntimeException() {
        model.thenThrow(new IllegalStateException("adapter bug"));

        AgentRunResult result = loop.run(new AgentRunRequest(ORGANIZER_LOGIN, SYSTEM, "Hello", List.of()));

        assertThat(result.reason()).isEqualTo(RunEndReason.INTERNAL_ERROR);
        assertThat(jdbc.queryForMap("SELECT status, error FROM agent_model_calls WHERE run_id = ?", result.runId()))
                .containsEntry("status", "FAILED")
                .containsEntry("error", IllegalStateException.class.getName() + ": adapter bug");
    }

    @Test
    @DisplayName("a NUL in tool output, tool input and model text is stored as U+FFFD; the run is DONE")
    void should_storeReplacementCharacter_when_textHoldsNul() {
        RecordingTool nul = TestTools.answering("lookup_event", in -> "before\u0000after");
        model.then(model.response(LlmStopReason.TOOL_USE, NO_TOKENS,
                        new LlmContent.Text("text\u0000"),
                        new LlmContent.ToolUse("tu_1", "lookup_event", TestTools.input("query", "q\u0000"))))
             .then(model.response(LlmStopReason.END_TURN, NO_TOKENS, new LlmContent.Text("done")));

        AgentRunResult result = loop.run(new AgentRunRequest(ORGANIZER_LOGIN, SYSTEM, "Hello", List.of(nul)));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        assertThat(jdbc.queryForMap("SELECT status, output, input->>'query' AS query FROM agent_tool_calls "
                        + "WHERE run_id = ?", result.runId()))
                .containsEntry("status", "SUCCEEDED")
                .containsEntry("output", "before\uFFFDafter")
                .containsEntry("query", "q\uFFFD");
        assertThat(jdbc.queryForObject("SELECT response->'content'->0->>'text' FROM agent_model_calls m "
                + "JOIN agent_steps s ON s.id = m.step_id WHERE m.run_id = ? AND s.step_number = 1",
                String.class, result.runId())).isEqualTo("text\uFFFD");
    }

    @Test
    @DisplayName("an unknown tool name longer than 128 characters is still logged UNKNOWN_TOOL")
    void should_logUnknownTool_when_nameIsLong() {
        String longName = "x".repeat(300);
        model.then(model.response(LlmStopReason.TOOL_USE, NO_TOKENS,
                        new LlmContent.ToolUse("tu_" + "y".repeat(200), longName, TestTools.input("query", "q"))))
             .then(model.response(LlmStopReason.END_TURN, NO_TOKENS, new LlmContent.Text("ok")));

        AgentRunResult result = loop.run(new AgentRunRequest(ORGANIZER_LOGIN, SYSTEM, "Hello", List.of()));

        assertThat(result.status()).isEqualTo(AgentRunStatus.DONE);
        assertThat(jdbc.queryForMap("SELECT tool_name, status FROM agent_tool_calls WHERE run_id = ?", result.runId()))
                .containsEntry("tool_name", longName).containsEntry("status", "UNKNOWN_TOOL");
    }

    @Test
    @DisplayName("a run handed to the organizers sends no mail: agent-service has no mail client")
    void should_haveNoMailClient() {
        assertThat(context.getBeanNamesForType(EmailService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SesV2Client.class)).isEmpty();
    }

    private int countToolCalls(UUID runId) {
        return jdbc.queryForObject("SELECT count(*) FROM agent_tool_calls WHERE run_id = ?", Integer.class, runId);
    }

    private static LlmContent.ToolResult lastToolResult(LlmRequest request) {
        LlmMessage last = request.messages().get(request.messages().size() - 1);
        assertThat(last.role()).isEqualTo(LlmMessage.Role.USER);
        return (LlmContent.ToolResult) last.content().get(last.content().size() - 1);
    }
}
