package ch.batbern.agent.run;

/**
 * Why a run ended; the values of the {@code agent_runs.end_reason} check constraint. Each reason
 * fixes the run's end status, so status and reason cannot disagree.
 */
public enum RunEndReason {
    /** The model ended its turn: the run is {@link AgentRunStatus#DONE}. */
    COMPLETED(AgentRunStatus.DONE),
    /** The model could not be reached; no retry, the organizers take over. */
    MODEL_UNAVAILABLE(AgentRunStatus.HANDED_TO_ORGANIZERS),
    /** The model's output was cut off at the output token limit. */
    MODEL_MAX_TOKENS(AgentRunStatus.HANDED_TO_ORGANIZERS),
    /** The model refused or stopped for a reason the loop does not continue from. */
    MODEL_STOPPED(AgentRunStatus.HANDED_TO_ORGANIZERS),
    /** The model kept calling tools past {@code agent.loop.max-steps}. */
    STEP_LIMIT(AgentRunStatus.HANDED_TO_ORGANIZERS),
    /** A fault in agent-service itself. */
    INTERNAL_ERROR(AgentRunStatus.FAILED);

    private final AgentRunStatus status;

    RunEndReason(AgentRunStatus status) {
        this.status = status;
    }

    /** The end status this reason gives the run. */
    public AgentRunStatus status() {
        return status;
    }
}
