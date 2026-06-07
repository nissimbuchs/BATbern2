package ch.batbern.shared.types;

/**
 * Speaker Response Type Enum — ADR-009 §0.6 (Unified Speaker Workflow).
 *
 * Represents the response a speaker gives to an event invitation. Under ADR-009 the
 * value space is binary: a speaker either accepts the invitation or declines it.
 *
 * Response model:
 * - ACCEPT  — workflow_state transitions to {@code ACCEPTED}.
 * - DECLINE — workflow_state transitions to {@code DECLINED} (terminal).
 *
 * Speakers who are unsure simply do not respond yet — reminder and escalation flows
 * handle response delays. A speaker who has already pressed ACCEPT but later changes
 * their mind transitions through {@code DECLINED}, with the reason recorded in
 * {@code speaker_status_history} (this path replaces the removed {@code WITHDREW}
 * state — see ADR-009 §0.7).
 *
 * Enum Value Flow (per _bmad-output/project-context.md §"Enum Value Flow"):
 * - Java/JSON/API: UPPER_CASE (e.g., "ACCEPT")
 * - Database: not stored directly (derived from workflow_state)
 *
 * @see ch.batbern.events.service.SpeakerResponseService
 * @see <a href="../../../../../../../../docs/architecture/ADR-009-unified-speaker-workflow.md">ADR-009 §0.6, §0.7</a>
 */
public enum SpeakerResponseType {

    /**
     * Speaker accepts the invitation.
     * <ul>
     *   <li>{@code workflow_state} transitions to {@code ACCEPTED}.</li>
     *   <li>{@code accepted_at} timestamp is set.</li>
     *   <li>Optional preferences are stored.</li>
     * </ul>
     */
    ACCEPT,

    /**
     * Speaker declines the invitation.
     * <ul>
     *   <li>{@code workflow_state} transitions to {@code DECLINED} (terminal state).</li>
     *   <li>{@code declined_at} timestamp is set.</li>
     *   <li>{@code decline_reason} is required and stored in
     *       {@code speaker_status_history}.</li>
     * </ul>
     */
    DECLINE
}
