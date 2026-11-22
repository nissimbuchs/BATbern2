package ch.batbern.agent.run;

/**
 * What started a run; the values of the {@code agent_runs.trigger} check constraint. They follow the
 * verified triggers CUMS issues run grants for (AD-3): an EMS mail handoff, an organizer's live
 * login, a scheduler job, and a run linked from a requester run (AD-15).
 */
public enum RunTrigger {
    INBOUND_MAIL,
    ORGANIZER,
    SCHEDULED,
    LINKED_RUN
}
