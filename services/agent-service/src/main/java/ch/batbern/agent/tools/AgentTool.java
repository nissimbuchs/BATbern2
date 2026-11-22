package ch.batbern.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A tool the agent loop can run when the model chooses it. Tools are deterministic code and never
 * call the model (AD-14). Story 5 adds the operation classes and the registry; until then the loop
 * gets its tools per run.
 */
public interface AgentTool {

    /** The name the model calls the tool by; unique within a run. */
    String name();

    /** What the tool does, for the model. */
    String description();

    /** The JSON Schema of the tool's input, {@code "type": "object"}. */
    JsonNode inputSchema();

    /**
     * Runs the tool. A tool that throws is logged as FAILED and the model gets an error result.
     * A tool reaches the services only through {@link ToolContext#api()}, with the run's minted
     * token; when CUMS refuses a mint the run ends after this tool, whatever the tool returns.
     *
     * @param input   the input the model chose
     * @param context the run and its public API
     * @return what the model gets back
     */
    ToolOutcome execute(JsonNode input, ToolContext context);
}
