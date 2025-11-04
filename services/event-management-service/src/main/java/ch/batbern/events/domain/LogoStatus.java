package ch.batbern.events.domain;

/**
 * Logo upload lifecycle states
 * Story 2.5.3a: Event Theme Image Upload
 *
 * State machine:
 * PENDING → CONFIRMED → ASSOCIATED
 */
public enum LogoStatus {
    /**
     * Upload initiated, presigned URL generated
     * Expires in 24 hours if not confirmed
     */
    PENDING,

    /**
     * File uploaded to S3 and confirmed
     * Expires in 7 days if not associated with an entity
     */
    CONFIRMED,

    /**
     * Logo associated with an entity (company, event, user, etc.)
     * Never expires
     */
    ASSOCIATED
}
