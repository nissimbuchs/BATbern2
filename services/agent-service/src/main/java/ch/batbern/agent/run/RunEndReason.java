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
    /**
     * CUMS refused the run grant, or could not be asked (story 4, R2); the run's {@code end_detail}
     * holds the CUMS code. No model call was made.
     */
    GRANT_REFUSED(AgentRunStatus.HANDED_TO_ORGANIZERS),
    /**
     * CUMS refused to mint an agent token, before the first model call or before a write (story 4,
     * R2); the run's {@code end_detail} holds the CUMS code. A refused write was never sent.
     */
    TOKEN_REFUSED(AgentRunStatus.HANDED_TO_ORGANIZERS),
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
