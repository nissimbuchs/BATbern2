package ch.batbern.agent.gate;

/** What the gate decided for a call; the values of {@code agent_verdicts.outcome}. */
public enum VerdictOutcome {
    /** The call runs now, without an organizer. */
    RUN,
    /** The call waits for an organizer's approval; it does not run (an {@code agent_approvals} row PENDING). */
    PENDING_APPROVAL,
    /** The call does not run and needs no approval: its mail could not be resolved, or a cap is reached. */
    REFUSED
}
