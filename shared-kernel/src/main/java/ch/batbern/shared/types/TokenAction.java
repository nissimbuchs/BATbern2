package ch.batbern.shared.types;

/**
 * Token Action Enum - Story 6.1a
 *
 * Represents the action type for magic link tokens in the speaker portal.
 * Token action determines single-use vs reusable behavior and access scope.
 *
 * Token Behavior by Action:
 * - RESPOND: Single-use, consumed after speaker accepts/declines invitation
 * - SUBMIT: Reusable, allows multiple content submission iterations
 * - VIEW: Reusable, allows read-only dashboard access
 *
 * Enum Value Flow (per coding-standards.md):
 * - Java/JSON/API: RESPOND (UPPER_CASE)
 * - Database: 'RESPOND' (stored as-is via varchar)
 *
 * Note: Stored in speaker_invitation_tokens.action column (event-management-service database)
 *
 * @see ch.batbern.events.domain.SpeakerInvitationToken
 */
public enum TokenAction {

    /**
     * Single-use token for responding to speaker invitation.
     * After accept/decline, token is marked as used and cannot be reused.
     * Story 6.1a AC3: Single-use enforcement
     */
    RESPOND,

    /**
     * Reusable token for submitting content.
     * Allows speakers to iteratively submit/update presentation materials.
     * Story 6.3: Content Submission workflow
     */
    SUBMIT,

    /**
     * Reusable token for viewing the speaker dashboard.
     * Read-only access to event details and submission status.
     * Story 6.4: Speaker Dashboard workflow
     */
    VIEW
}
