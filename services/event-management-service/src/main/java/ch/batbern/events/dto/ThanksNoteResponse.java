package ch.batbern.events.dto;

import java.time.Instant;

/**
 * A single organizer-visible thank-you note (Story 7.4, AC6).
 *
 * <p>Returned ONLY in the organizer-authenticated GET response — never to anonymous/public
 * callers. {@code thankedByUsername} is {@code null} for anonymous claps.
 */
public record ThanksNoteResponse(
        String note,
        String thankedByUsername,
        Instant createdAt) {
}
