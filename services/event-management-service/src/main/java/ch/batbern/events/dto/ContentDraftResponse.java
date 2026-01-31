package ch.batbern.events.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO after saving content draft via speaker portal.
 * Story 6.3 AC4: Draft auto-save
 *
 * @param draftId The submission record ID
 * @param savedAt Timestamp when draft was saved
 */
public record ContentDraftResponse(
        UUID draftId,
        Instant savedAt
) {
}
