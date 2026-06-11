package ch.batbern.events.dto;

import java.util.List;

/**
 * The attendee dashboard (Story 7.6) — every event the attendee participated in, split into
 * upcoming (event date in the future, soonest first) and past (most recent first).
 */
public record AttendeeDashboardResponse(
        String attendeeName,
        List<AttendeeEventCardResponse> upcomingEvents,
        List<AttendeeEventCardResponse> pastEvents) {
}
