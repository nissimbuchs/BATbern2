package ch.batbern.events.service.slotassignment;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Severity levels for scheduling conflicts
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
public enum ConflictSeverity {
    ERROR("error"),    // Blocks assignment (room overlap, speaker double-booking)
    WARNING("warning");   // Allows assignment but shows warning (preference mismatch)

    private final String value;

    ConflictSeverity(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
