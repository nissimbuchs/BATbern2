package ch.batbern.events.dto;

import java.util.List;

/**
 * Response DTO for batch speaker invitations.
 * Story 6.1b: Speaker Invitation System (AC5)
 *
 * @param totalRequested Total number of speakers requested
 * @param successCount Number of speakers successfully invited
 * @param failedCount Number of speakers that failed to be invited
 * @param results Individual results for each speaker invitation
 * @param errors Details of failed invitations
 */
public record BatchInviteResponse(
        int totalRequested,
        int successCount,
        int failedCount,
        List<InviteSpeakerResponse> results,
        List<BatchInviteError> errors
) {

    /**
     * Error details for a failed invitation.
     *
     * @param email Email address of the speaker that failed
     * @param errorCode Error code (e.g., "INVALID_EMAIL", "USER_SERVICE_ERROR")
     * @param errorMessage Human-readable error message
     */
    public record BatchInviteError(
            String email,
            String errorCode,
            String errorMessage
    ) {
    }

    /**
     * Factory method to create a successful batch response (no errors).
     */
    public static BatchInviteResponse success(List<InviteSpeakerResponse> results) {
        return new BatchInviteResponse(
                results.size(),
                results.size(),
                0,
                results,
                List.of()
        );
    }

    /**
     * Factory method to create a batch response with partial success.
     */
    public static BatchInviteResponse partial(
            int totalRequested,
            List<InviteSpeakerResponse> results,
            List<BatchInviteError> errors
    ) {
        return new BatchInviteResponse(
                totalRequested,
                results.size(),
                errors.size(),
                results,
                errors
        );
    }
}
