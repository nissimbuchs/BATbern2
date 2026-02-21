package ch.batbern.shared.types;

/**
 * Speaker Response Type Enum - Story 6.2a
 *
 * Represents the response type when a speaker responds to an event invitation.
 * Used in the speaker self-service response portal.
 *
 * Response Behavior:
 * - ACCEPT: Speaker accepts invitation, transitions to ACCEPTED state, token consumed
 * - DECLINE: Speaker declines invitation, transitions to DECLINED state (terminal), token consumed
 * - TENTATIVE: Speaker is undecided, stays in INVITED state with tentative flag, token NOT consumed
 *
 * Enum Value Flow (per coding-standards.md):
 * - Java/JSON/API: ACCEPT (UPPER_CASE)
 * - Database: Not stored directly (derived from workflow_state + flags)
 *
 * @see ch.batbern.events.service.SpeakerResponseService
 */
public enum SpeakerResponseType {

    /**
     * Speaker accepts the invitation.
     * - workflow_state transitions to ACCEPTED
     * - accepted_at timestamp is set
     * - Token is consumed (single-use enforcement)
     * - Optional preferences are stored
     * Story 6.2a AC3: Accept Response Flow
     */
    ACCEPT,

    /**
     * Speaker declines the invitation.
     * - workflow_state transitions to DECLINED (terminal state)
     * - declined_at timestamp is set
     * - decline_reason is required and stored
     * - Token is consumed (single-use enforcement)
     * Story 6.2a AC4: Decline Response Flow
     */
    DECLINE,

    /**
     * Speaker is tentatively interested but not committing.
     * - workflow_state stays INVITED
     * - is_tentative flag set to true
     * - tentative_reason is required and stored
     * - Token is NOT consumed (speaker can return and change response)
     * Story 6.2a AC5: Tentative Response Flow
     */
    TENTATIVE
}
