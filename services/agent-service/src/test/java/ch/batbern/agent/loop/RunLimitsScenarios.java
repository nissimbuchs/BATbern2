package ch.batbern.agent.loop;

import ch.batbern.agent.MiddayClock;
import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.gate.GateFixtureTool;
import ch.batbern.agent.identity.CumsAtTheWire;
import ch.batbern.agent.loop.TestTools.RecordingTool;
import ch.batbern.agent.run.AgentRunStatus;
import ch.batbern.agent.run.RunEndReason;
import ch.batbern.agent.run.RunLimit;
import ch.batbern.agent.run.RunTrigger;
import ch.batbern.agent.tools.AgentTool;
import ch.batbern.agent.tools.TestToolRegistry;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.batbern.agent.identity.CumsAtTheWire.source;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 8 (R9): each of the four limits of {@code agent.limits} stops a scripted run through the real
 * loop and gate, on Testcontainers PostgreSQL with CUMS at the wire. The run ends
 * HANDED_TO_ORGANIZERS with reason CAP_REACHED and the limit as end detail, and the application log
 * names the limit without personal data. Two subclasses run the same scripts under different
 * configuration and state where each run stops, so a moved value moves the stop point. The day is
 * fixed ({@link MiddayClock}), so the daily budget cannot see midnight pass during a run.
 */
@ExtendWith(OutputCaptureExtension.class)
@Import(MiddayClock.class)
abstract class RunLimitsScenarios extends AbstractIntegrationTest {

    private static final String SYSTEM = "You coordinate BATbern speakers.";
    /** 15 tokens per model call, spread over all four counted kinds. */
    static final LlmUsage FIFTEEN = new LlmUsage(10, 3, 1, 1);
    private static final LlmUsage ONE = new LlmUsage(1, 0, 0, 0);
    /** Tokens other runs spent today before the daily-budget run starts. */
    static final long SPENT_TODAY = 80;
    private static final String ANNA = "anna@example.com";

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

    @Autowired
    private AgentProperties properties;

    @Autowired
    private Clock clock;

    private CumsAtTheWire cums;
    private int toolUses;

    /** Model calls a run makes before the step cap stops it. */
    abstract int stepsStopAfter();

    /** Model calls of {@link #FIFTEEN} tokens a run makes before the run token cap stops it. */
    abstract int runTokensStopAfter();

    /**
     * Model calls of {@link #FIFTEEN} tokens a run makes, after {@link #SPENT_TODAY}, before the daily
     * budget stops it.
     */
    abstract int dailyTokensStopAfter();

    @BeforeEach
    @AfterEach
    void clean() {
        model.reset();
        toolRegistry.reset();
        jdbc.execute("TRUNCATE agent_runs CASCADE");
    }

    @BeforeEach
    void bindCums() {
        cums = CumsAtTheWire.bindTo(restTemplate).withClock(clock).grants().mints();
    }

    @AfterEach
    void unbindCums() {
        cums.close();
    }

    @Test
    @DisplayName("the clock is the midday clock of the fixed day")
    void should_runOnTheFixedDay() {
        MiddayClock.assertAnchored(clock);
    }

    @Test
    @DisplayName("the step cap: a model still calling tools after run-steps model calls stops the run, STEPS")
    void should_stop_when_stepCapReached(CapturedOutput output) {
        RecordingTool lookup = TestTools.answering("lookupEvent", in -> "again");
        keepCallingTools(lookup, ONE, 20);

        AgentRunResult result = run(List.of(lookup));

        assertStopped(result, RunLimit.STEPS, output);
        assertThat(output.getAll()).contains("limit STEPS reached (used " + stepsStopAfter() + ", configured "
                + properties.limits().runSteps() + ")");
        assertThat(model.requests()).hasSize(stepsStopAfter());
        assertThat(result.steps()).isEqualTo(stepsStopAfter());
        assertThat(lookup.inputs()).hasSize(stepsStopAfter());
    }

    @Test
    @DisplayName("the run token cap: no model call once the run's recorded tokens reach run-tokens, RUN_TOKENS")
    void should_stop_when_runTokenCapReached(CapturedOutput output) {
        RecordingTool lookup = TestTools.answering("lookupEvent", in -> "again");
        keepCallingTools(lookup, FIFTEEN, 20);

        AgentRunResult result = run(List.of(lookup));

        assertStopped(result, RunLimit.RUN_TOKENS, output);
        assertThat(model.requests()).hasSize(runTokensStopAfter());
        long recorded = tokensOfRun(result.runId());
        assertThat(recorded).as("the crossing call completed and is counted, all four kinds")
                .isEqualTo(15L * runTokensStopAfter())
                .isGreaterThanOrEqualTo(properties.limits().runTokens());
        assertThat(recorded - 15).as("the call before it was still under the cap")
                .isLessThan(properties.limits().runTokens());
    }

    @Test
    @DisplayName("the reply cap: run-replies replies run, the next is refused and stops the run, REPLIES")
    void should_stop_when_replyCapReached(CapturedOutput output) {
        GateFixtureTool reply = GateFixtureTool.reply();
        RecordingTool lookup = TestTools.answering("lookupEvent", in -> "found");
        // First answer: one reply; every later answer: a reply, then a lookup
        model.then(model.response(LlmStopReason.TOOL_USE, ONE, use(reply.name(), to(ANNA))));
        for (int i = 0; i < 5; i++) {
            model.then(model.response(LlmStopReason.TOOL_USE, ONE, use(reply.name(), to(ANNA)),
                    use(lookup.name(), TestTools.input("query", "q"))));
        }

        AgentRunResult result = run(List.of(reply, lookup));

        int replies = properties.limits().runReplies();
        assertStopped(result, RunLimit.REPLIES, output);
        assertThat(reply.executed()).as("the replies the cap allows ran").hasSize(replies);
        assertThat(model.requests()).hasSize(replies + 1);
        assertThat(lookup.inputs()).as("the refused reply's answer ran no further tool").hasSize(replies - 1);
        List<Map<String, Object>> verdicts = jdbc.queryForList("SELECT v.outcome, v.inputs->>'reason' AS reason, "
                + "v.inputs->>'cap' AS cap, (v.inputs->>'requiredApprovals')::int AS approvals, "
                + "(v.inputs->>'maxNonOrganizerRecipients')::int AS recipients, tc.status FROM agent_verdicts v "
                + "JOIN agent_tool_calls tc ON tc.id = v.tool_call_id WHERE v.run_id = ? "
                + "ORDER BY v.decided_at, tc.started_at",
                result.runId());
        assertThat(verdicts).hasSize(replies + 1);
        assertThat(verdicts.subList(0, replies)).allSatisfy(row -> assertThat(row)
                .containsEntry("outcome", "RUN").containsEntry("status", "SUCCEEDED"));
        assertThat(verdicts.getLast()).containsEntry("outcome", "REFUSED").containsEntry("reason", "CAP_REACHED")
                .containsEntry("cap", "REPLIES").containsEntry("status", "REFUSED");
        // The gate's two values come from the same configuration and are recorded with each verdict
        assertThat(verdicts).allSatisfy(row -> assertThat(row)
                .containsEntry("approvals", properties.limits().bindingApprovals())
                .containsEntry("recipients", properties.limits().nonOrganizerRecipients()));
        assertThat(output.getAll()).contains("limit REPLIES reached (used " + replies + ", configured " + replies + ")")
                .doesNotContain(ANNA);
    }

    @Test
    @DisplayName("the daily budget: no model call once today's tokens of all runs reach daily-tokens; "
            + "yesterday's do not count, DAILY_TOKENS")
    void should_stop_when_dailyBudgetReached(CapturedOutput output) {
        seedModelCall(SPENT_TODAY, clock.instant());
        seedModelCall(1_000_000, startOfToday().minusSeconds(60));
        RecordingTool lookup = TestTools.answering("lookupEvent", in -> "again");
        keepCallingTools(lookup, FIFTEEN, 20);

        AgentRunResult result = run(List.of(lookup));

        assertStopped(result, RunLimit.DAILY_TOKENS, output);
        assertThat(model.requests()).hasSize(dailyTokensStopAfter());
        assertThat(tokensOfRun(result.runId())).as("the run's own cap was not what stopped it")
                .isLessThan(properties.limits().runTokens());
    }

    @Test
    @DisplayName("a budget already spent: the run makes no model call, DAILY_TOKENS")
    void should_stopBeforeAnyModelCall_when_budgetAlreadySpent(CapturedOutput output) {
        seedModelCall(properties.limits().dailyTokens(), clock.instant());
        RecordingTool lookup = TestTools.answering("lookupEvent", in -> "again");
        keepCallingTools(lookup, ONE, 3);

        AgentRunResult result = run(List.of(lookup));

        assertStopped(result, RunLimit.DAILY_TOKENS, output);
        assertThat(model.requests()).isEmpty();
        assertThat(result.steps()).isZero();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

    private AgentRunResult run(List<? extends AgentTool> tools) {
        return loop.run(toolRegistry.request(source(RunTrigger.ORGANIZER), SYSTEM,
                "Write to " + ANNA + " about the agenda.", tools));
    }

    private void keepCallingTools(AgentTool tool, LlmUsage usage, int answers) {
        for (int i = 0; i < answers; i++) {
            model.then(model.response(LlmStopReason.TOOL_USE, usage, use(tool.name(), TestTools.input("query",
                    "q" + i))));
        }
    }

    private void assertStopped(AgentRunResult result, RunLimit limit, CapturedOutput output) {
        assertThat(result.status()).isEqualTo(AgentRunStatus.HANDED_TO_ORGANIZERS);
        assertThat(result.reason()).isEqualTo(RunEndReason.CAP_REACHED);
        assertThat(result.detail()).isEqualTo(limit.name());
        assertThat(jdbc.queryForMap("SELECT status, end_reason, end_detail FROM agent_runs WHERE id = ?",
                result.runId()))
                .containsEntry("status", "HANDED_TO_ORGANIZERS")
                .containsEntry("end_reason", "CAP_REACHED")
                .containsEntry("end_detail", limit.name());
        // Positive control first: the log line is there, with the run id and the limit
        assertThat(output.getAll()).contains("Agent run " + result.runId() + " handed to the organizers: limit "
                + limit.name() + " reached");
        assertThat(output.getAll()).doesNotContain(ANNA).doesNotContain("about the agenda");
        assertThat(model.exhausted()).as("the script outlasted the run").isFalse();
    }

    private LlmContent.ToolUse use(String name, ObjectNode input) {
        toolUses++;
        return new LlmContent.ToolUse("tu_" + toolUses, name, input);
    }

    private static ObjectNode to(String address) {
        ObjectNode input = TestTools.JSON.createObjectNode();
        input.putArray("to").add(address);
        return input;
    }

    private long tokensOfRun(UUID runId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(input_tokens + output_tokens + cache_creation_input_tokens "
                + "+ cache_read_input_tokens), 0) FROM agent_model_calls WHERE run_id = ?", Long.class, runId);
    }

    private Instant startOfToday() {
        return LocalDate.ofInstant(clock.instant(), RunLimit.DAY_ZONE).atStartOfDay(RunLimit.DAY_ZONE).toInstant();
    }

    /** A model call of another, finished run that spent {@code tokens} at {@code at}. */
    private void seedModelCall(long tokens, Instant at) {
        UUID run = UUID.randomUUID();
        UUID step = UUID.randomUUID();
        Timestamp t = Timestamp.from(at);
        jdbc.update("INSERT INTO agent_runs (id, status, started_at, ended_at, trigger, end_reason) "
                + "VALUES (?, 'DONE', ?, ?, 'ORGANIZER', 'COMPLETED')", run, t, t);
        jdbc.update("INSERT INTO agent_steps (id, run_id, step_number, started_at, ended_at) VALUES (?, ?, 1, ?, ?)",
                step, run, t, t);
        jdbc.update("INSERT INTO agent_model_calls (id, run_id, step_id, model, prompt, status, input_tokens, "
                + "output_tokens, cache_creation_input_tokens, cache_read_input_tokens, started_at, ended_at) "
                + "VALUES (?, ?, ?, 'seeded', '{}'::jsonb, 'SUCCEEDED', ?, 0, 0, 0, ?, ?)", UUID.randomUUID(), run,
                step, tokens, t, t);
    }
}
