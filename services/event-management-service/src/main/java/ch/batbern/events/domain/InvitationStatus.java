package ch.batbern.events.domain;

/**
 * Invitation workflow status - Story 6.1.
 *
 * Tracks the lifecycle of a speaker invitation:
 * PENDING → SENT → OPENED → RESPONDED/EXPIRED
 */
public enum InvitationStatus {
    PENDING,    // Invitation created but not yet sent
    SENT,       // Invitation email sent
    OPENED,     // Email opened (tracked via SES)
    RESPONDED,  // Speaker has responded (accepted/declined/tentative)
    EXPIRED     // Invitation expired without response
}
