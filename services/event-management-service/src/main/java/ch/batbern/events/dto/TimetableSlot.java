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
@Builder(toBuilder = true)
public class TimetableSlot {

    public enum Type {
        MODERATION,
        BREAK,
        LUNCH,
        APERITIF,
        SPEAKER_SLOT
    }

    /** Slot type — determines rendering and droppability. */
    Type type;

    /**
     * Deterministic, computed slot identity (Story 15.3): {@code "{Type}-{ordinal}"} where
     * ordinal is 1-based among slots of that type, in computed order — e.g. "MODERATION-1",
     * "APERITIF-1", "SPEAKER_SLOT-3", "BREAK-2". Stable across timing/config edits; the UI and
     * the slot-assign endpoint address slots by this key rather than by wall-clock time.
     * Transient — never persisted on a session.
     */
    String slotKey;

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
