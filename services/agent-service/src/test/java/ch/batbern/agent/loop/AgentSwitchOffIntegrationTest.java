package ch.batbern.agent.loop;

import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.run.RunTrigger;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The service ships dark: in the default context ({@code agent.enabled=false}) the loop refuses to
 * start, writes no run and never calls the model.
 */
@DisplayName("AgentLoop refuses while the agent is switched off")
class AgentSwitchOffIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AgentLoop loop;

    @Autowired
    private ScriptedLlm model;

    @Autowired
    private AgentProperties properties;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        model.reset();
        jdbc.execute("TRUNCATE agent_runs CASCADE");
    }

    @Test
    @DisplayName("should_refuseAndWriteNoRun_when_agentSwitchedOff")
    void should_refuseAndWriteNoRun_when_agentSwitchedOff() {
        assertThat(properties.enabled()).as("the default context runs dark").isFalse();
        model.then(model.response(LlmStopReason.END_TURN, new LlmUsage(1, 1, 0, 0), new LlmContent.Text("hi")));

        assertThatThrownBy(() -> loop.run(new AgentRunRequest(RunTrigger.ORGANIZER, "system", "Hello", List.of())))
                .isInstanceOf(AgentSwitchedOffException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_runs", Integer.class)).isZero();
        assertThat(model.requests()).isEmpty();
        assertThat(model.exhausted()).as("the scripted answer was never taken").isFalse();
    }
}
