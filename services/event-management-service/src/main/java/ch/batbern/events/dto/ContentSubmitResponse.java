package ch.batbern.events.dto;

import java.util.UUID;

/**
 * Response DTO after submitting content via speaker portal.
 * Story 6.3 AC5: Content submission
 *
 * @param submissionId The content submission record ID
 * @param version The submission version number
 * @param status The new content status (SUBMITTED)
 * @param sessionTitle The session title for confirmation display
 */
public record ContentSubmitResponse(
        UUID submissionId,
        int version,
        String status,
        String sessionTitle
) {
}
