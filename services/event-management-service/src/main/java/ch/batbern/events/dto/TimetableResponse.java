package ch.batbern.events.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Response DTO for the timetable endpoint.
 *
 * Contains a chronologically-ordered list of all slots (both structural and speaker slots)
 * and the set of speaker sessions that have not yet been assigned to a time slot.
 */
@Value
@Builder
public class TimetableResponse {

    /**
     * All slots in chronological order — includes MODERATION, BREAK, LUNCH, and SPEAKER_SLOT
     * entries interleaved as they appear throughout the event day.
     */
    List<TimetableSlot> slots;

    /**
     * Speaker sessions (non-structural) that have no startTime assigned yet.
     * Used by the slot-assignment UI to populate the unassigned sessions sidebar.
     */
    List<SessionResponse> unassignedSessions;
}
