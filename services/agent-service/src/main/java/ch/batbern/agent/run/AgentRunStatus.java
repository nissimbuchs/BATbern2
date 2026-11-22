package ch.batbern.agent.run;

/** Where a run stands; the values of the {@code agent_runs.status} check constraint. */
public enum AgentRunStatus {
    RUNNING,
    DONE,
    FAILED,
    HANDED_TO_ORGANIZERS
}
