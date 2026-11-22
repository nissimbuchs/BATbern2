package ch.batbern.agent.tools;

import ch.batbern.agent.gateway.Operation;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.Set;

/**
 * An intent tool, the agent's only capability (AD-6): one intent, composed of the public operations
 * it declares. Tools are deterministic code and never call the model (AD-14). Its class is derived:
 * the highest class among its operations in the {@link OperationClassTable}, which
 * {@link ToolClassCheck} enforces. At run time the tool's {@link ToolContext#api()} lets it call
 * only the operations it declares.
 */
public interface AgentTool {

    /** The name the model calls the tool by: camelCase verb plus object ({@code findPastEvents}). */
    String name();

    /** What the tool does, for the model. */
    String description();

    /** The JSON Schema of the tool's input, {@code "type": "object"}; ids are ADR-003 ids ({@code eventCode}). */
    JsonNode inputSchema();

    /** The tool's class: the highest class of {@link #operations()} in the table. */
    OperationClass operationClass();

    /** Every public operation the tool may call; the only ones its API lets through. */
    Set<Operation> operations();

    /** The operation that undoes the tool: present exactly when the tool is {@code reversible} (parent R9). */
    default Optional<Operation> undoOperation() {
        return Optional.empty();
    }

    /**
     * The past events a call of this tool with {@code input} should be judged against (story 6,
     * AD-8): the precedent retrieval reads them through {@code findPastEvents} with the run's token
     * and cites each one. Empty (the default) when past events say nothing about the call.
     * Deterministic, no call of any kind; input it cannot use gives empty.
     */
    default Optional<PastEventQuery> pastEventQuery(JsonNode input) {
        return Optional.empty();
    }

    /**
     * Runs the tool. A tool that throws is logged as FAILED and the model gets an error result.
     * A tool reaches the services only through {@link ToolContext#api()}, with the run's minted
     * token; when CUMS refuses a mint the run ends after this tool, whatever the tool returns.
     *
     * @param input   the input the model chose
     * @param context the run and its public API, limited to {@link #operations()}
     * @return what the model gets back
     */
    ToolOutcome execute(JsonNode input, ToolContext context);
}
