package ch.batbern.agent.tools;

import ch.batbern.agent.gateway.Operation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The build check of the tool registry (story 5, R4, AD-6), a plain unit test in the normal suite:
 * the shipped registry passes against the shipped table; tools that exist only here prove that the
 * check fails for a tool declared below (or above) its highest operation, for an operation or undo
 * operation missing from the table, and for a reversible tool without its undo operation.
 *
 * <p>The fixtures need write operations in the table, which the shipped table does not hold (the
 * epic ships read tools only), so they are checked against the shipped table plus {@link #WRITES}.
 */
@DisplayName("The tool class check")
class ToolClassCheckTest {

    /** Real operations of docs/api, classed for the fixtures only. */
    static final OperationClassTable WRITES = OperationClassTable.standard().with(Map.of(
            Operation.of("PATCH /events/{eventCode}/sessions/{sessionSlug}"), OperationClass.REVERSIBLE,
            Operation.of("DELETE /events/{eventCode}/sessions/{sessionSlug}"), OperationClass.BINDING,
            Operation.of("POST /events/{eventCode}/sessions"), OperationClass.REVERSIBLE));

    @Test
    @DisplayName("the shipped registry passes the shipped table, and its tools are read tools")
    void should_pass_when_shippedRegistry() {
        List<AgentTool> shipped = StandardTools.create(Clock.systemUTC());

        assertThat(ToolClassCheck.violations(shipped, OperationClassTable.standard())).isEmpty();
        assertThat(new ToolRegistry(shipped, OperationClassTable.standard()).tools())
                .extracting(AgentTool::name)
                .containsExactly("findPastEvents", "listEventSessions", "listSessionSpeakers");
        assertThat(shipped).allSatisfy(tool -> {
            assertThat(tool.operationClass()).isEqualTo(OperationClass.READ);
            assertThat(tool.operations()).isNotEmpty();
            assertThat(tool.undoOperation()).isEmpty();
        });
    }

    @Test
    @DisplayName("a correct fixture of every kind passes: read, reversible with its undo, binding")
    void should_pass_when_fixturesDeclareTheirDerivedClass() {
        List<AgentTool> fixtures = List.of(
                FixtureTool.of("readSessions", OperationClass.READ, "GET /events/{eventCode}/sessions"),
                FixtureTool.of("addSession", OperationClass.REVERSIBLE, "POST /events/{eventCode}/sessions",
                        "GET /events/{eventCode}/sessions")
                        .undoneBy("DELETE /events/{eventCode}/sessions/{sessionSlug}"),
                FixtureTool.of("dropSession", OperationClass.BINDING,
                        "DELETE /events/{eventCode}/sessions/{sessionSlug}",
                        "PATCH /events/{eventCode}/sessions/{sessionSlug}"),
                FixtureTool.of("thinkAloud", OperationClass.READ));

        assertThat(ToolClassCheck.violations(fixtures, WRITES)).isEmpty();
    }

    @Test
    @DisplayName("a planted understated tool fails: declared read, calls a binding operation")
    void should_fail_when_toolDeclaredBelowItsHighestOperation() {
        AgentTool understated = FixtureTool.of("tidySessions", OperationClass.READ,
                "GET /events/{eventCode}/sessions", "DELETE /events/{eventCode}/sessions/{sessionSlug}");

        assertThat(ToolClassCheck.violations(List.of(understated), WRITES)).containsExactly(
                "Tool tidySessions is declared read but calls DELETE /events/{eventCode}/sessions/{sessionSlug},"
                        + " which is binding");
        assertThatThrownBy(() -> new ToolRegistry(List.of(understated), WRITES))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tidySessions");
    }

    @Test
    @DisplayName("an understated reversible tool fails: declared reversible, calls a binding operation")
    void should_fail_when_reversibleToolCallsBindingOperation() {
        AgentTool understated = FixtureTool.of("moveSession", OperationClass.REVERSIBLE,
                "PATCH /events/{eventCode}/sessions/{sessionSlug}", "DELETE /events/{eventCode}/sessions/{sessionSlug}")
                .undoneBy("PATCH /events/{eventCode}/sessions/{sessionSlug}");

        assertThat(ToolClassCheck.violations(List.of(understated), WRITES)).singleElement().asString()
                .contains("moveSession is declared reversible", "which is binding");
    }

    @Test
    @DisplayName("an overstated tool fails too: the class is derived, not chosen")
    void should_fail_when_toolDeclaredAboveItsHighestOperation() {
        AgentTool overstated = FixtureTool.of("readCarefully", OperationClass.BINDING, "GET /events");

        assertThat(ToolClassCheck.violations(List.of(overstated), WRITES)).singleElement().asString()
                .contains("readCarefully is declared binding", "at most read");
    }

    @Test
    @DisplayName("a planted tool calling an operation missing from the table fails, with the shipped table too")
    void should_fail_when_toolCallsUnlistedOperation() {
        AgentTool unlisted = FixtureTool.of("readCompanies", OperationClass.READ, "GET /companies");

        String expected = "Tool readCompanies calls GET /companies, which the operation class table does not list";
        assertThat(ToolClassCheck.violations(List.of(unlisted), WRITES)).containsExactly(expected);
        assertThat(ToolClassCheck.violations(List.of(unlisted), OperationClassTable.standard()))
                .containsExactly(expected);
        assertThatThrownBy(() -> new ToolRegistry(List.of(unlisted), OperationClassTable.standard()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(expected);
    }

    @Test
    @DisplayName("a reversible tool must name its undo operation, from the table; other tools name none")
    void should_fail_when_undoOperationMissingMisplacedOrUnlisted() {
        AgentTool withoutUndo = FixtureTool.of("addSession", OperationClass.REVERSIBLE,
                "POST /events/{eventCode}/sessions");
        AgentTool unlistedUndo = FixtureTool.of("addTalk", OperationClass.REVERSIBLE,
                "POST /events/{eventCode}/sessions").undoneBy("DELETE /events/{eventCode}");
        AgentTool readWithUndo = FixtureTool.of("readSessions", OperationClass.READ,
                "GET /events/{eventCode}/sessions").undoneBy("DELETE /events/{eventCode}/sessions/{sessionSlug}");

        assertThat(ToolClassCheck.violations(List.of(withoutUndo, unlistedUndo, readWithUndo), WRITES)).containsExactly(
                "Tool addSession is reversible but names no undo operation",
                "Tool addTalk names undo operation DELETE /events/{eventCode}, which the operation class table"
                        + " does not list",
                "Tool readSessions names an undo operation but is read, not reversible");
    }

    @Test
    @DisplayName("names are camelCase verb plus object and unique")
    void should_fail_when_nameIsNotCamelCaseOrTaken() {
        List<AgentTool> tools = List.of(
                FixtureTool.of("list_events", OperationClass.READ, "GET /events"),
                FixtureTool.of("events", OperationClass.READ, "GET /events"),
                FixtureTool.of("findPastEvents", OperationClass.READ, "GET /events"),
                FixtureTool.of("findPastEvents", OperationClass.READ, "GET /events"));

        assertThat(ToolClassCheck.violations(tools, WRITES)).containsExactly(
                "Tool list_events: the name is not camelCase verb plus object",
                "Tool events: the name is not camelCase verb plus object",
                "Tool findPastEvents: another tool has the same name");
    }
}
