package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Story 9.2: Response DTO from company-user-management-service speaker provisioning.
 * Contains result of Cognito account creation/role extension.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerProvisionResponse {

    private String username;
    private String cognitoUserId;
    private AccountAction action;

    /**
     * Temporary password for NEW accounts only. Null for EXTENDED accounts.
     * Must be included in the post-acceptance email.
     */
    private String temporaryPassword;

    public enum AccountAction {
        @JsonProperty("NEW")
        NEW,
        @JsonProperty("EXTENDED")
        EXTENDED
    }
}
