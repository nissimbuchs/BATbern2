package ch.batbern.shared.types;

/**
 * Speaker Workflow State Enum — ADR-009 (Unified Speaker Workflow).
 *
 * Represents the workflow state of a speaker on an event's speaker pool. Eight states
 * cover the full lifecycle from initial brainstorming through quality-reviewed content,
 * plus a single terminal "not happening" state reachable from any non-terminal state.
 *
 * Workflow Phases (ADR-009 §0.1):
 * 1. Brainstorming         — IDENTIFIED, CONTACTED        (no User exists; speaker_pool.username is NULL)
 * 2. Provisioning gate     — READY                        (User lookup-or-create + SPEAKER role grant;
 *                                                          Cognito user provisioning lands in Phase E)
 * 3. Invitation & response — INVITED, ACCEPTED, DECLINED  (slot-capacity gate enforced on READY → INVITED)
 * 4. Content lifecycle     — CONTENT_SUBMITTED, QUALITY_REVIEWED
 * 5. Terminal              — DECLINED                     (reachable from every non-terminal state, including
 *                                                          post-QUALITY_REVIEWED drop-outs; reason recorded
 *                                                          in speaker_status_history)
 *
 * Derived flags (NOT stored on speaker_pool — computed at read time per ADR-009 §0.1):
 * - is_slot_assigned  := session.start_time IS NOT NULL
 * - is_publishable    := workflow_state = QUALITY_REVIEWED AND is_slot_assigned
 *
 * Enum Value Flow (per _bmad-output/project-context.md §"Enum Value Flow"):
 * - Java/JSON/API: UPPER_CASE (e.g., "CONTACTED")
 * - Database: lowercase_snake_case via SpeakerWorkflowStateConverter (e.g., 'contacted')
 *
 * Stored in speaker_pool.status (event-management-service database).
 *
 * @see ch.batbern.speakers.converter.SpeakerWorkflowStateConverter
 * @see <a href="../../../../../../../../docs/architecture/ADR-009-unified-speaker-workflow.md">ADR-009 §0.1</a>
 */
public enum SpeakerWorkflowState {

    /**
     * Initial state. Name on the brainstorm list. May be a candidate, a lead, or a contact
     * the organizer plans to ask. No User exists. {@code speaker_pool.username} is NULL.
     * No Cognito user.
     *
     * @see <a href="../../../../../../../../docs/architecture/ADR-009-unified-speaker-workflow.md">ADR-009 §0.1</a>
     */
    IDENTIFIED,

    /**
     * <strong>Still brainstorming.</strong> Organizer is reaching out — to the candidate,
     * to partners, to network contacts — to figure out who will actually speak. All
     * conversations logged via {@code OutreachHistory}. No User exists.
     * {@code speaker_pool.username} is NULL. No Cognito user.
     *
     * @see <a href="../../../../../../../../docs/architecture/ADR-009-unified-speaker-workflow.md">ADR-009 §0.1</a>
     */
    CONTACTED,

    /**
     * <strong>Provisioning gate.</strong> The real speaker has been identified. Organizer
     * has a name + email and has committed to inviting this specific person. The transition
     * INTO this state performs User lookup-or-create + SPEAKER role grant + persists
     * {@code username} on {@code speaker_pool}. <strong>Cognito user provisioning (with
     * {@code FORCE_CHANGE_PASSWORD}) is added in Phase E (Story 11.E.2)</strong> — it is
     * NOT yet wired through this transition. Reached only via
     * {@code POST /api/v1/events/{code}/speakers/{speakerId}/promote}
     * (Phase D — Story 11.D.1), which publishes {@code SpeakerPromotedToReadyEvent}.
     *
     * @see <a href="../../../../../../../../docs/architecture/ADR-009-unified-speaker-workflow.md">ADR-009 §0.1, §0.2</a>
     */
    READY,

    /**
     * Formal invitation sent (email contains login link + temporary password). Speaker
     * can authenticate via Cognito. {@code READY → INVITED} is blocked when
     * {@code count(ACCEPTED) + count(INVITED) >= max_slots} for the event (slot-capacity
     * gate replaces removed {@code OVERFLOW} — see ADR-009 §0.7).
     *
     * @see <a href="../../../../../../../../docs/architecture/ADR-009-unified-speaker-workflow.md">ADR-009 §0.1, §0.7</a>
     */
    INVITED,

    /**
     * Speaker committed via the portal.
     *
     * @see <a href="../../../../../../../../docs/architecture/ADR-009-unified-speaker-workflow.md">ADR-009 §0.1</a>
     */
    ACCEPTED,

    /**
     * Title + abstract submitted to {@code content_submissions}. Either
     * organizer-on-behalf or speaker-self submission — both flows traverse the same
     * {@code ContentSubmissionService} per ADR-009 §0.4.
     *
     * @see <a href="../../../../../../../../docs/architecture/ADR-009-unified-speaker-workflow.md">ADR-009 §0.1, §0.4</a>
     */
    CONTENT_SUBMITTED,

    /**
     * Moderator approved content. <strong>Happy end-state of the content lifecycle</strong> —
     * the speaker is publishable once a slot is assigned. NOT terminal: a confirmed
     * speaker who later drops out still transitions to {@code DECLINED} (reason recorded in
     * {@code speaker_status_history}). {@code DECLINED} is the only terminal state.
     * {@code is_publishable} is derived as {@code QUALITY_REVIEWED AND slot_assigned}.
     * {@code is_slot_assigned} is derived from {@code session.start_time IS NOT NULL}.
     * Neither is persisted.
     *
     * @see <a href="../../../../../../../../docs/architecture/ADR-009-unified-speaker-workflow.md">ADR-009 §0.1, §0.5</a>
     */
    QUALITY_REVIEWED,

    /**
     * The single terminal "not happening" state. Reachable from ANY non-terminal state —
     * covers a lead that didn't pan out (from {@code IDENTIFIED}/{@code CONTACTED}), a
     * refusal to an invitation (from {@code INVITED}), and a speaker who accepted then
     * dropped out (from {@code ACCEPTED}/{@code CONTENT_SUBMITTED}/{@code QUALITY_REVIEWED}).
     * The status-history row records the previous state and reason. Replaces the removed
     * {@code WITHDREW} state — see ADR-009 §0.7.
     *
     * @see <a href="../../../../../../../../docs/architecture/ADR-009-unified-speaker-workflow.md">ADR-009 §0.1, §0.7</a>
     */
    DECLINED
}
