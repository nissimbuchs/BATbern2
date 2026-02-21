package ch.batbern.events.dto;

/**
 * Request DTO for saving content draft via speaker portal.
 * Story 6.3 AC4: Draft auto-save
 *
 * @param token Magic link token for authentication
 * @param title Draft presentation title (max 200 chars)
 * @param contentAbstract Draft presentation abstract (max 1000 chars)
 */
public record ContentDraftRequest(
        String token,
        String title,
        String contentAbstract
) {
}
