package ch.batbern.agent.tools;

import java.util.Collection;
import java.util.List;

/**
 * The tools the agent loop offers the model (AD-6, R4). Built once at startup; refuses to start
 * when any tool fails {@link ToolClassCheck} against its {@link OperationClassTable}, so no tool can
 * understate its class or call an operation nobody classified.
 */
public class ToolRegistry {

    private final List<AgentTool> tools;
    private final OperationClassTable table;

    /**
     * @throws IllegalStateException naming every violation when a tool fails the check
     */
    public ToolRegistry(Collection<? extends AgentTool> tools, OperationClassTable table) {
        List<String> violations = ToolClassCheck.violations(tools, table);
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "The tool registry fails its class check: " + String.join("; ", violations));
        }
        this.tools = List.copyOf(tools);
        this.table = table;
    }

    /** The tools offered to the model, in a stable order. */
    public List<AgentTool> tools() {
        return tools;
    }

    /** The table the tools were checked against. */
    public OperationClassTable table() {
        return table;
    }
}
