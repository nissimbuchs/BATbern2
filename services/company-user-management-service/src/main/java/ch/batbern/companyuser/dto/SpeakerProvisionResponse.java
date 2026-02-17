package ch.batbern.companyuser.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Story 9.2: Response DTO for speaker account provisioning.
 * Contains result of Cognito account creation/role extension.
 * temporaryPassword is only populated for NEW accounts (null for EXTENDED).
 */
@Data
@Builder
public class SpeakerProvisionResponse {

    /** Local DB username of the provisioned user */
    private String username;

    /** Cognito user sub/username */
    private String cognitoUserId;

    /** Account action — NEW (account created) or EXTENDED (SPEAKER role added) */
    private AccountAction action;

    /**
     * Temporary password for NEW accounts only.
     * Null for EXTENDED accounts (user already has a password).
     * Sent in the post-acceptance email by event-management-service.
     */
    private String temporaryPassword;

    public enum AccountAction {
        NEW,
        EXTENDED
    }
}
