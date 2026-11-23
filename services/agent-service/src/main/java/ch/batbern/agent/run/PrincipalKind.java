package ch.batbern.agent.run;

/**
 * Whom a run acts for, as the CUMS grant says ({@code AgentGrant.principalKind} of
 * {@code docs/api/internal/cums-internal.openapi.yml}, AD-15). Stored on the run
 * ({@code agent_runs.principal_kind}); precedents match on it (story 6).
 */
public enum PrincipalKind {
    /** The person who wrote in (an EMS handoff). */
    REQUESTER,
    /** An organizer, live (chat, console, approval). */
    ORGANIZER,
    /** The configured organizer delegate (a linked run, a scheduler job). */
    ORGANIZER_DELEGATE
}
