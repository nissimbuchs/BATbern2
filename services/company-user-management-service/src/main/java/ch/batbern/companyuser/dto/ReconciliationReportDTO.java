package ch.batbern.companyuser.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Reconciliation Report DTO
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * Response for manual reconciliation trigger
 * Shows sync statistics and errors from Cognito → Database sync
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReconciliationReportDTO {

    /**
     * Number of orphaned database users deactivated
     * (Users deleted in Cognito but still active in DB)
     */
    private int orphanedUsersDeactivated;

    /**
     * Number of missing database users created
     * (Users exist in Cognito but missing in DB)
     */
    private int missingUsersCreated;

    /**
     * Total duration of reconciliation in milliseconds
     */
    private long durationMs;

    /**
     * Errors encountered during reconciliation
     */
    private List<String> errors;

    /**
     * Success indicator
     */
    private boolean success;

    /**
     * Human-readable summary message
     */
    private String message;
}
