package ch.batbern.agent.tools;

import ch.batbern.agent.gateway.Operation;
import ch.batbern.agent.identity.GrantSource;
import ch.batbern.agent.log.InboundMessage;
import ch.batbern.agent.loop.AgentRunRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * The tool registry of every test context ({@code @Primary}, as {@code ScriptedLlm} is for the
 * model): it offers the shipped tools until a test {@link #install installs} its own. Installed
 * tools pass the same {@link ToolClassCheck} as the shipped ones, against {@link #TABLE}: the
 * shipped table plus the companies operations the loop and identity tests call. Tests call
 * {@link #reset()} before and after each test.
 */
@Primary
@Component
public class TestToolRegistry extends ToolRegistry {

    /**
     * The shipped table plus GET and POST {@code /companies} (companies spec) and, for the gate's
     * fixture tools (story 7), one real operation of docs/api per non-read class.
     */
    public static final OperationClassTable TABLE = OperationClassTable.standard().with(Map.of(
            Operation.of("GET /companies"), OperationClass.READ,
            Operation.of("POST /companies"), OperationClass.BINDING,
            Operation.of("PATCH /events/{eventCode}/sessions/{sessionSlug}"), OperationClass.REVERSIBLE,
            Operation.of("POST /conversations/{conversationKey}/replies"), OperationClass.REPLY,
            Operation.of("POST /events/{eventCode}/speakers/{speakerId}/send-invitation"), OperationClass.BINDING,
            Operation.of("POST /events/{eventCode}/mails"), OperationClass.AUDIENCE));

    private volatile List<AgentTool> installed;

    public TestToolRegistry(Clock clock) {
        super(StandardTools.create(clock), OperationClassTable.standard());
    }

    @Override
    public List<AgentTool> tools() {
        List<AgentTool> current = installed;
        return current == null ? super.tools() : current;
    }

    /** {@link #TABLE} while test tools are installed, the shipped table otherwise. */
    @Override
    public OperationClassTable table() {
        return installed == null ? super.table() : TABLE;
    }

    /** The next runs are offered exactly {@code tools} (none for an empty list), checked first. */
    public void install(List<? extends AgentTool> tools) {
        List<String> violations = ToolClassCheck.violations(tools, TABLE);
        if (!violations.isEmpty()) {
            throw new AssertionError("Test tools fail the class check: " + violations);
        }
        installed = List.copyOf(tools);
    }

    /** Back to the shipped tools. */
    public void reset() {
        installed = null;
    }

    /** Installs {@code tools} and returns the request of a run without an inbound message. */
    public AgentRunRequest request(GrantSource grant, String system, String userMessage,
                                   List<? extends AgentTool> tools) {
        return request(grant, system, userMessage, tools, null);
    }

    /** Installs {@code tools} and returns the request of a run. */
    public AgentRunRequest request(GrantSource grant, String system, String userMessage,
                                   List<? extends AgentTool> tools, InboundMessage inbound) {
        install(tools);
        return new AgentRunRequest(grant, system, userMessage, inbound);
    }
}
