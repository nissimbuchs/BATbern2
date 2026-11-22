package ch.batbern.agent.loop;

import ch.batbern.agent.identity.CumsAtTheWire;
import ch.batbern.agent.loop.TestTools.RecordingTool;
import ch.batbern.agent.run.AgentRunStatus;
import ch.batbern.agent.run.RunTrigger;
import ch.batbern.agent.tools.TestToolRegistry;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.fixtures.ProductionShapedPeople;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static ch.batbern.agent.identity.CumsAtTheWire.source;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * #1202 for the loop: the application log carries ids, counts, tool names and outcomes, never the
 * prompt, a tool's input or output, or an exception message. A run whose prompt, tool input, tool
 * output, tool exception and model fault all carry a person's name and address leaves neither in the
 * captured output; the decision log keeps them (it is the full record).
 */
@DisplayName("AgentLoop logs no personal data")
@TestPropertySource(properties = {"agent.enabled=true", "agent.loop.max-steps=4"})
@ExtendWith(OutputCaptureExtension.class)
class AgentLoopLogIntegrationTest extends AbstractIntegrationTest {

    private static final ProductionShapedPeople.Person PERSON = ProductionShapedPeople.VIETNAMESE;
    private static final String POSTAL_ADDRESS = "Kramgasse 49, 3011 Bern";

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

    /** CUMS grants and mints at the wire, spec-validated; every run obtains a grant and a token first. */
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
        cums = CumsAtTheWire.bindTo(restTemplate).grants().mints();
    }

    @AfterEach
    void unbindCums() {
        cums.close();
    }

    @Test
    @DisplayName("should_logIdsAndOutcomesOnly_when_promptAndToolsCarryPersonalData")
    void should_logIdsAndOutcomesOnly_when_promptAndToolsCarryPersonalData(CapturedOutput output) {
        String who = PERSON.fullName() + " <" + PERSON.email() + ">, " + POSTAL_ADDRESS;
        RecordingTool find = TestTools.answering("findPerson", in -> "found " + who);
        RecordingTool broken = TestTools.throwing("updatePerson",
                new IllegalArgumentException("cannot update " + who));
        model.then(model.response(LlmStopReason.TOOL_USE, new LlmUsage(10, 5, 0, 0),
                        new LlmContent.Text("Looking up " + who),
                        new LlmContent.ToolUse("tu_1", "findPerson", TestTools.input("query", who)),
                        new LlmContent.ToolUse("tu_2", "updatePerson", TestTools.input("query", who)),
                        new LlmContent.ToolUse("tu_3", "unknown " + PERSON.fullName(), TestTools.input("query", who))))
             .thenThrow(new IllegalStateException("model fault while answering about " + who));

        AgentRunResult result = loop.run(toolRegistry.request(source(RunTrigger.INBOUND_MAIL),
                "Help " + who, "Please update " + who, List.of(find, broken)));

        assertThat(result.status()).isEqualTo(AgentRunStatus.FAILED);
        // The decision log has the data (the full record) ...
        assertThat(jdbc.queryForObject("SELECT output FROM agent_tool_calls WHERE run_id = ? AND tool_use_id = 'tu_1'",
                String.class, result.runId())).contains(PERSON.fullName());
        // ... the application log names the run, the tools and the outcomes (positive control) ...
        String out = output.getAll();
        assertThat(out).contains("Agent run " + result.runId() + " started")
                .contains("tool findPerson succeeded")
                .contains("tool updatePerson failed (java.lang.IllegalArgumentException)")
                .contains("Agent run " + result.runId() + " failed in step 2: java.lang.IllegalStateException")
                .contains("Agent run " + result.runId() + " ended FAILED");
        // ... and nothing about the person
        assertThat(out).doesNotContain(PERSON.fullName())
                .doesNotContain(PERSON.firstName())
                .doesNotContain(PERSON.username())
                .doesNotContain(PERSON.email())
                .doesNotContain(POSTAL_ADDRESS)
                .doesNotContain("Kramgasse");
        // ... nor the run's credentials (story 4)
        assertThat(cums.tokens()).isNotEmpty();
        assertThat(out).doesNotContain(CumsAtTheWire.CALLER_SECRET);
        cums.tokens().forEach(token -> assertThat(out).doesNotContain(token));
    }
}
