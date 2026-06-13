package ch.batbern.events.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Organizer request to feature/un-feature a thank-you note (Story 7.7).
 *
 * <p>Body of {@code PATCH /api/v1/events/{eventCode}/thanks/{id}}. {@code true} promotes the note
 * to the public marquee; {@code false} removes it.
 */
public record ThanksFeaturePatchRequest(
        @NotNull(message = "featured is required") Boolean featured) {
}
