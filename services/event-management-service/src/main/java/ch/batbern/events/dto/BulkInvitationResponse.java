package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for bulk speaker invitation operations - Story 6.1 AC7.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkInvitationResponse {

    /**
     * Total number of invitations requested.
     */
    private int totalRequested;

    /**
     * Number of invitations successfully sent.
     */
    private int successCount;

    /**
     * Number of invitations that failed.
     */
    private int failureCount;

    /**
     * List of successfully sent invitations.
     */
    private List<InvitationResponse> successful;

    /**
     * List of failures with reasons.
     */
    private List<BulkInvitationFailure> failures;

    /**
     * Details of a failed invitation attempt.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkInvitationFailure {
        private String username;
        private String reason;
    }
}
