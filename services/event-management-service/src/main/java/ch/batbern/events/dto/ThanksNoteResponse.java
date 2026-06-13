package ch.batbern.events.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A single organizer-visible thank-you note (Story 7.4, AC6; enriched in Story 7.7).
 *
 * <p>Returned ONLY in the organizer-authenticated GET response — never to anonymous/public
 * callers. {@code thankedByUsername} is {@code null} for anonymous claps. Story 7.7 adds the row
 * {@code id} + {@code featured} flag (for the ★ feature toggle) and the resolved author display
 * name ({@code thankedByFirstName}/{@code thankedByLastName}/{@code thankedByCompanyName}) so the
 * Appreciation panel can show a friendly name rather than the raw username.
 */
public record ThanksNoteResponse(
        UUID id,
        String note,
        String thankedByUsername,
        String thankedByFirstName,
        String thankedByLastName,
        String thankedByCompanyName,
        boolean featured,
        Instant createdAt) {
}
