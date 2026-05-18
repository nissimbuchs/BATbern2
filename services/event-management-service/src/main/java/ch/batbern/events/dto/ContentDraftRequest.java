package ch.batbern.events.dto;

/**
 * Request DTO for saving content draft via speaker portal.
 * Story 6.3 AC4: Draft auto-save.
 *
 * <p>Story 11.E.3: {@code token} field removed; the speaker portal is now Cognito-secured
 * and the {@code eventCode} arrives as a path parameter.
 *
 * @param title Draft presentation title (max 200 chars)
 * @param contentAbstract Draft presentation abstract (max 1000 chars)
 */
public record ContentDraftRequest(
        String title,
        String contentAbstract
) {
}
