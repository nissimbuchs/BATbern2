package ch.batbern.events.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Immutable value object representing a single slot in the event timetable.
 *
 * SPEAKER_SLOT entries represent droppable speaker slots (the implicit gaps between
 * structural sessions). All other types map to persisted structural sessions.
 */
@Value
@Builder
public class TimetableSlot {

    public enum Type {
        MODERATION,
        BREAK,
        LUNCH,
        SPEAKER_SLOT
    }

    /** Slot type — determines rendering and droppability. */
    Type type;

    /** Slot start time (UTC). */
    Instant startTime;

    /** Slot end time (UTC). */
    Instant endTime;

    /**
     * Human-readable label for structural slots (null for SPEAKER_SLOT).
     * E.g., "Moderation Start", "Kaffee-Pause", "Mittagessen", "Moderation End".
     */
    String title;

    /**
     * 1-based global index for SPEAKER_SLOT entries across the whole day
     * (not reset when switching AM → PM). Null for structural slots.
     */
    Integer slotIndex;

    /**
     * Session slug of the structural DB session that occupies this slot.
     * Null if no structural sessions have been generated yet (or for SPEAKER_SLOT).
     */
    String sessionSlug;

    /**
     * Session slug of the speaker session assigned to this SPEAKER_SLOT.
     * Null if the slot is free (or for structural slots).
     */
    String assignedSessionSlug;
}
