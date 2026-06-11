package ch.batbern.events.dto;

import java.util.List;

/**
 * Response for both the POST submit and GET aggregate of "Thank the Organizers" (Story 7.4).
 *
 * <p>{@code count} (the public clap-style aggregate) is always present. {@code notes} is
 * populated ONLY for organizer-authenticated GET callers (AC6); it is {@code null} for the
 * public GET and for POST responses — there is no public note wall.
 */
public record ThanksCountResponse(
        long count,
        List<ThanksNoteResponse> notes) {

    /** Public / count-only response (no notes). */
    public static ThanksCountResponse ofCount(long count) {
        return new ThanksCountResponse(count, null);
    }
}
