package ch.batbern.agent.gate;

/** Why the gate decided as it did; stored as {@code inputs.reason} of the verdict (story 7, AD-7). */
public enum GateReason {
    /** {@code reversible} or {@code reply}: runs alone within the caps. */
    RUNS_ALONE,
    /** {@code binding} with at least two matching approvals and no matching denial. */
    BINDING_CLEARED,
    /** {@code binding} below the threshold: fewer than two matching approvals. */
    BINDING_BELOW_THRESHOLD,
    /** {@code binding} with a matching denial: never alone, whatever the approvals. */
    BINDING_DENIED,
    /** The tool is {@code audience}: always a pending approval. */
    AUDIENCE_CLASS,
    /**
     * The mail brings the distinct non-organizer recipients of the run or the day above
     * {@code agent.limits.non-organizer-recipients} (one by default): {@code audience}.
     */
    RECIPIENTS_ABOVE_ONE,
    /** A scheduled run's mail: never sent without approval. */
    SCHEDULED_MAIL,
    /** The tool could not say whom its mail would reach: the call does not run. */
    MAIL_UNRESOLVED,
    /** A limit of {@code agent.limits} is reached (story 8): the call does not run and the run stops. */
    CAP_REACHED
}
