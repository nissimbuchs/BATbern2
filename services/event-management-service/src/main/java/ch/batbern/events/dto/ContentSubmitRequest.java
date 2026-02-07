package ch.batbern.events.dto;

/**
 * Request DTO for submitting content via speaker portal.
 * Story 6.3 AC5: Content submission
 *
 * @param token Magic link token for authentication
 * @param title Presentation title (required, max 200 chars)
 * @param contentAbstract Presentation abstract (required, max 1000 chars)
 */
public record ContentSubmitRequest(
        String token,
        String title,
        String contentAbstract
) {
}
