package ch.batbern.companyuser.domain;

/**
 * Logo upload lifecycle states for generic file upload service
 * Story 1.16.3: Generic File Upload Service
 * ADR-002: Generic File Upload Service Architecture
 *
 * State Machine:
 * PENDING → CONFIRMED → ASSOCIATED
 */
public enum LogoStatus {
    /**
     * Upload initiated, presigned URL generated
     * File may not yet be in S3
     * Expires after 24 hours if not confirmed
     */
    PENDING,

    /**
     * File successfully uploaded to S3 and verified
     * File is in temp location (logos/temp/{uploadId}/)
     * Expires after 7 days if not associated with an entity
     */
    CONFIRMED,

    /**
     * File associated with an entity (company, user, event, etc.)
     * File moved to final location (logos/{year}/{type}/{name}/)
     * Kept indefinitely (no expiration)
     */
    ASSOCIATED
}
