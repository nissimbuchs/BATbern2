package ch.batbern.agent.loop;

import ch.batbern.agent.identity.CumsAtTheWire;
import ch.batbern.agent.log.InboundChannel;
import ch.batbern.agent.log.InboundMessage;
import ch.batbern.agent.run.AgentRunStatus;
import ch.batbern.agent.run.RunAlarms;
import ch.batbern.agent.run.RunEndReason;
import ch.batbern.agent.run.RunLimit;
import ch.batbern.agent.run.RunTrigger;
import ch.batbern.agent.tools.TestToolRegistry;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.fixtures.ProductionShapedPeople;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static ch.batbern.agent.identity.CumsAtTheWire.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 10 (R10, #1202): a run about a speaker writes the alarm marker of its end and none of the
 * speaker's personal data. The speaker's mail (name, e-mail address, postal address) is the run's
 * inbound message and prompt; the shipped {@code listSessionSpeakers} tool reads the speaker through
 * the public API, answered at the wire against the sessions spec; the model repeats the data in its
 * text and tool input. Each run then ends by one alarmed path (daily budget, step cap, fault, Error,
 * refused mint) or
 * by the unalarmed model outage, and the captured log is checked for the marker (positive control)
 * and for the absence of every piece of the speaker's data.
 */
@DisplayName("Alarm markers carry no personal data")
@TestPropertySource(properties = {
    "agent.enabled=true", "agent.limits.run-steps=" + RunAlarmLogIntegrationTest.RUN_STEPS,
    "agent.limits.daily-tokens=" + RunAlarmLogIntegrationTest.DAILY_TOKENS
})
@ExtendWith(OutputCaptureExtension.class)
class RunAlarmLogIntegrationTest extends AbstractIntegrationTest {

    static final long DAILY_TOKENS = 30;
    static final int RUN_STEPS = 6;

    private static final String SESSIONS = "docs/api/event-sessions-api.openapi.yml";
    private static final String SPEAKERS_PATH = "/api/v1/events/BATbern56/sessions/blockchain-security/speakers";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ProductionShapedPeople.Person SPEAKER = ProductionShapedPeople.VIETNAMESE;
    private static final String POSTAL_ADDRESS = "Kramgasse 49, 3011 Bern";
    private static final String WHO = SPEAKER.fullName() + " <" + SPEAKER.email() + ">, " + POSTAL_ADDRESS;
    /** 15 tokens per call: two calls reach the daily budget of 30. */
    private static final LlmUsage FIFTEEN = new LlmUsage(10, 5, 0, 0);
    private static final LlmUsage ONE = new LlmUsage(1, 0, 0, 0);
    private static final List<String> ALL_MARKERS = List.of(RunAlarms.RUN_FAILED, RunAlarms.RUN_CAP_REACHED,
            RunAlarms.DAILY_BUDGET_BREACHED, RunAlarms.CREDENTIALS_REFUSED);

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

    @BeforeEach
    @AfterEach
    void clean() {
        model.reset();
        toolRegistry.reset();
        jdbc.execute("TRUNCATE agent_runs CASCADE");
    }

    @BeforeEach
    void bindCums() {
        cums = CumsAtTheWire.bindTo(restTemplate, SESSIONS);
    }

    @AfterEach
    void unbindCums() {
        cums.close();
    }

    @Test
    @DisplayName("the daily budget: AGENT_RUN_CAP_REACHED and AGENT_DAILY_BUDGET_BREACHED, no speaker data")
    void should_logBudgetMarkers_when_dailyBudgetStopsARunAboutASpeaker(CapturedOutput output) {
        cums.grants().mints();
        serveSpeaker(2);
        model.then(readSpeakers(FIFTEEN)).then(readSpeakers(FIFTEEN));

        AgentRunResult result = runAboutTheSpeaker();

        assertThat(result.reason()).isEqualTo(RunEndReason.CAP_REACHED);
        assertThat(result.detail()).isEqualTo(RunLimit.DAILY_TOKENS.name());
        assertSpeakerWasRead(result.runId());
        assertMarkers(output, result.runId(), RunAlarms.RUN_CAP_REACHED, RunAlarms.DAILY_BUDGET_BREACHED);
        assertNoSpeakerData(output);
    }

    @Test
    @DisplayName("a fault: AGENT_RUN_FAILED, no speaker data even in the exception")
    void should_logFailureMarker_when_runAboutASpeakerFails(CapturedOutput output) {
        cums.grants().mints();
        serveSpeaker(1);
        model.then(readSpeakers(ONE)).thenThrow(new IllegalStateException("fault while answering " + WHO));

        AgentRunResult result = runAboutTheSpeaker();

        assertThat(result.status()).isEqualTo(AgentRunStatus.FAILED);
        assertSpeakerWasRead(result.runId());
        assertMarkers(output, result.runId(), RunAlarms.RUN_FAILED);
        assertNoSpeakerData(output);
    }

    @Test
    @DisplayName("an Error from a tool: AGENT_RUN_FAILED once, the Error goes on, no speaker data")
    void should_logFailureMarker_when_errorEndsARunAboutASpeaker(CapturedOutput output) {
        cums.grants().mints();
        AssertionError error = new AssertionError("invariant broken for " + WHO);
        TestTools.RecordingTool broken = new TestTools.RecordingTool("lookupEvent", in -> {
            throw error;
        });
        toolRegistry.install(List.of(broken));
        model.then(model.response(LlmStopReason.TOOL_USE, ONE, new LlmContent.Text("Looking up " + WHO),
                new LlmContent.ToolUse("tu_1", "lookupEvent", TestTools.input("query", WHO))));

        assertThatThrownBy(this::runAboutTheSpeaker).isSameAs(error);

        UUID runId = jdbc.queryForObject("SELECT id FROM agent_runs", UUID.class);
        assertThat(jdbc.queryForObject("SELECT status FROM agent_runs WHERE id = ?", String.class, runId))
                .isEqualTo("FAILED");
        assertMarkers(output, runId, RunAlarms.RUN_FAILED);
        assertNoSpeakerData(output);
    }

    @Test
    @DisplayName("a per-run cap: AGENT_RUN_CAP_REACHED without the budget marker, no speaker data")
    void should_logCapMarker_when_stepCapStopsARunAboutASpeaker(CapturedOutput output) {
        cums.grants().mints();
        serveSpeaker(RUN_STEPS);
        for (int i = 0; i < RUN_STEPS; i++) {
            model.then(readSpeakers(ONE));
        }

        AgentRunResult result = runAboutTheSpeaker();

        assertThat(result.reason()).isEqualTo(RunEndReason.CAP_REACHED);
        assertThat(result.detail()).isEqualTo(RunLimit.STEPS.name());
        assertSpeakerWasRead(result.runId());
        assertMarkers(output, result.runId(), RunAlarms.RUN_CAP_REACHED);
        assertNoSpeakerData(output);
    }

    @Test
    @DisplayName("a refused mint: AGENT_CREDENTIALS_REFUSED with the CUMS code, no speaker data")
    void should_logRefusalMarker_when_mintRefusedForARunAboutASpeaker(CapturedOutput output) {
        cums.grants().mintsThenRefuses(0, HttpStatus.FORBIDDEN, "GRANT_INVALID");

        AgentRunResult result = runAboutTheSpeaker();

        assertThat(result.reason()).isEqualTo(RunEndReason.TOKEN_REFUSED);
        // The speaker's mail is in the decision log (positive control for the data being in the run)
        assertThat(jdbc.queryForObject("SELECT content::text FROM agent_inbound_messages WHERE run_id = ?",
                String.class, result.runId())).contains(POSTAL_ADDRESS);
        assertMarkers(output, result.runId(), RunAlarms.CREDENTIALS_REFUSED);
        assertThat(output.getAll()).contains(RunAlarms.CREDENTIALS_REFUSED + " Agent run " + result.runId()
                + " ended TOKEN_REFUSED (GRANT_INVALID)");
        assertNoSpeakerData(output);
    }

    @Test
    @DisplayName("the model unreachable: handed to the organizers, no marker, no speaker data")
    void should_logNoMarker_when_modelUnreachableForARunAboutASpeaker(CapturedOutput output) {
        cums.grants().mints();
        serveSpeaker(1);
        model.then(readSpeakers(ONE)).thenThrow(new LlmUnavailableException("unreachable while answering " + WHO));

        AgentRunResult result = runAboutTheSpeaker();

        assertThat(result.reason()).isEqualTo(RunEndReason.MODEL_UNAVAILABLE);
        assertSpeakerWasRead(result.runId());
        assertThat(output.getAll()).contains("Agent run " + result.runId() + " ended HANDED_TO_ORGANIZERS");
        ALL_MARKERS.forEach(marker -> assertThat(output.getAll()).doesNotContain(marker));
        assertNoSpeakerData(output);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

    private AgentRunResult runAboutTheSpeaker() {
        ObjectNode mail = JSON.createObjectNode()
                .put("from", SPEAKER.fullName() + " <" + SPEAKER.email() + ">")
                .put("subject", "My talk")
                .put("body", "Kind regards, " + SPEAKER.fullName() + ", " + POSTAL_ADDRESS);
        InboundMessage inbound = new InboundMessage(InboundChannel.MAIL, "<speaker-mail@example.com>",
                Instant.parse("2026-11-22T09:00:00Z"), mail);
        // No tools installed: the run is offered the shipped registry (listSessionSpeakers among them)
        return loop.run(new AgentRunRequest(source(RunTrigger.INBOUND_MAIL), "You coordinate BATbern speakers.",
                "Answer " + WHO, inbound));
    }

    /** The speaker, as the sessions API sends them: real shape, the person's names and username. */
    private void serveSpeaker(int times) {
        ArrayNode body = JSON.createArrayNode();
        body.addObject()
                .put("username", SPEAKER.username())
                .put("firstName", SPEAKER.firstName())
                .put("lastName", SPEAKER.lastName())
                .put("company", "galenica")
                .put("companyDisplayName", "Galenica")
                .putNull("profilePictureUrl")
                .put("speakerRole", "PRIMARY_SPEAKER")
                .put("isConfirmed", true)
                .put("bio", "Reach me at " + SPEAKER.email() + ", " + POSTAL_ADDRESS)
                .putNull("companyLogoUrl")
                .put("presentationTitle", "Talk by " + SPEAKER.fullName());
        for (int i = 0; i < times; i++) {
            cums.server().expect(HttpMethod.GET, SPEAKERS_PATH).respondWith(HttpStatus.OK, body.toString());
        }
    }

    private LlmResponse readSpeakers(LlmUsage usage) {
        ObjectNode input = JSON.createObjectNode().put("eventCode", "BATbern56")
                .put("sessionSlug", "blockchain-security");
        return model.response(LlmStopReason.TOOL_USE, usage, new LlmContent.Text("Looking up " + WHO),
                new LlmContent.ToolUse("tu_" + UUID.randomUUID(), "listSessionSpeakers", input));
    }

    private void assertSpeakerWasRead(UUID runId) {
        // Positive control: the speaker's name reached the decision log through the real tool
        assertThat(jdbc.queryForList("SELECT output FROM agent_tool_calls WHERE run_id = ? AND status = 'SUCCEEDED'",
                String.class, runId)).isNotEmpty().allSatisfy(out -> assertThat(out).contains(SPEAKER.lastName()));
    }

    private static void assertMarkers(CapturedOutput output, UUID runId, String... expected) {
        String out = output.getAll();
        for (String marker : expected) {
            String line = marker + " Agent run " + runId + " ended";
            assertThat(out.split(Pattern.quote(line), -1)).as("one line of " + marker).hasSize(2);
        }
        ALL_MARKERS.stream().filter(m -> !List.of(expected).contains(m))
                .forEach(marker -> assertThat(out).doesNotContain(marker));
    }

    private static void assertNoSpeakerData(CapturedOutput output) {
        assertThat(output.getAll()).doesNotContain(SPEAKER.fullName())
                .doesNotContain(SPEAKER.firstName())
                .doesNotContain(SPEAKER.lastName())
                .doesNotContain(SPEAKER.username())
                .doesNotContain(SPEAKER.email())
                .doesNotContain(POSTAL_ADDRESS)
                .doesNotContain("Kramgasse");
    }
}
