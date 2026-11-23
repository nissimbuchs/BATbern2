package ch.batbern.agent.precedent;

/** What an organizer did with an earlier call of the agent, as the decision log records it (AD-8). */
public enum PrecedentKind {
    /** An approval in state APPROVED ({@code agent_approvals}). */
    APPROVAL,
    /** An approval in state DENIED ({@code agent_approvals}). */
    DENIAL,
    /** An undo of the call, whatever its outcome ({@code agent_undos}). */
    UNDO
}
