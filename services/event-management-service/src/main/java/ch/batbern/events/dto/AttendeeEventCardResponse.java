package ch.batbern.events.dto;

import java.time.Instant;

/**
 * A single event on the attendee dashboard (Story 7.6).
 *
 * <p>Card data for one event the attendee participated in (one per non-cancelled registration).
 * {@code workflowState} is the event's UPPER_CASE state; {@code registrationStatus} is the
 * attendee's registration status as stored ({@code registered|confirmed|waitlist|attended}).
 * The client links the card to {@code /events/{eventCode}} (upcoming) or {@code /archive/{eventCode}}
 * (past) — both render the public event detail page.
 */
public record AttendeeEventCardResponse(
        String eventCode,
        String eventTitle,
        Instant eventDate,
        String eventLocation,
        String workflowState,
        String registrationStatus) {
}
