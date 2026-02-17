package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Story 9.4: Per-speaker outcome record in a MigrationReport.
 * Describes what happened to a single speaker during Epic 9 migration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerMigrationResult {

    private UUID speakerPoolId;
    private String email;
    private String speakerName;
    private MigrationOutcome outcome;
    private String detail;

    /**
     * Outcome of migrating a single speaker to Epic 9 JWT authentication.
     */
    public enum MigrationOutcome {
        /** New Cognito account created and invitation email sent. */
        PROVISIONED_NEW,
        /** Existing Cognito account extended with SPEAKER role; no email resent. */
        EXTENDED,
        /** Invitation email sent successfully (after PROVISIONED_NEW). */
        EMAIL_SENT,
        /** Invitation email dispatch failed; migration continues for other speakers. */
        EMAIL_FAILED,
        /** Speaker skipped — no matching event found in database. */
        SKIPPED,
        /** Unexpected error during provisioning; migration continues for other speakers. */
        ERROR
    }
}
