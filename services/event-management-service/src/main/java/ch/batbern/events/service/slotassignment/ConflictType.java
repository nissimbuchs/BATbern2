package ch.batbern.events.service.slotassignment;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Types of scheduling conflicts that can occur
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
public enum ConflictType {
    ROOM_OVERLAP("room_overlap"),           // Same room, overlapping times
    SPEAKER_DOUBLE_BOOKED("speaker_double_booked"),  // Same speaker in overlapping sessions
    SPEAKER_UNAVAILABLE("speaker_unavailable"),    // Session during speaker's unavailable time
    PREFERENCE_MISMATCH("preference_mismatch");     // Session doesn't match speaker preferences (warning only)

    private final String value;

    ConflictType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
