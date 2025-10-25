package ch.batbern.companyuser.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Sync Status DTO
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * Response for sync status check
 * Shows comparison between Cognito and Database users
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncStatusDTO {

    /**
     * Total number of users in Cognito
     */
    private int cognitoUserCount;

    /**
     * Total number of active users in Database
     */
    private int databaseUserCount;

    /**
     * Number of users missing in Database
     * (Exist in Cognito but not in DB)
     */
    private int missingInDatabase;

    /**
     * Number of users orphaned in Database
     * (Exist in DB but deleted in Cognito)
     */
    private int orphanedInDatabase;

    /**
     * Cognito IDs of users missing in Database
     * (For admin visibility)
     */
    private List<String> missingCognitoIds;

    /**
     * Indicates if Cognito and Database are in sync
     */
    private boolean inSync;

    /**
     * Human-readable status message
     */
    private String message;
}
