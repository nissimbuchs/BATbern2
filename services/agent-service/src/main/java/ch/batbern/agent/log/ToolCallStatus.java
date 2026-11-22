package ch.batbern.agent.log;

/** Where a tool call stands; the values of {@code agent_tool_calls.status}. */
public enum ToolCallStatus {
    /** Running: a run that crashed here still shows the input. */
    STARTED,
    /** The tool returned a result. */
    SUCCEEDED,
    /** The tool returned an error result of its own. */
    ERRORED,
    /** The tool threw. */
    FAILED,
    /** The model asked for a tool the run does not offer. */
    UNKNOWN_TOOL
}
