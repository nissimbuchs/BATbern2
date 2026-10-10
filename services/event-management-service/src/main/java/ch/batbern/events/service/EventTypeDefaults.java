package ch.batbern.events.service;

import ch.batbern.events.core.dto.generated.EventType;

import java.time.LocalTime;

/**
 * Conventional start time per event type, used only when neither the per-event agenda config nor
 * the event-type template carries one. Single source for {@link TimetableService} (slot grid) and
 * {@link EventTimeResolver} (emails, public event page), which used to fall back to 09:00 and 16:00
 * respectively for every type (bug fix 2026-10-07). The frontend form mirrors these values in
 * {@code EventTypeConfigurationForm.DEFAULT_START_TIME}.
 */
public final class EventTypeDefaults {

    /** Standard BATbern start, used when an event has no type at all. */
    static final LocalTime STANDARD_START = LocalTime.of(16, 0);

    private EventTypeDefaults() {
    }

    public static LocalTime startTime(EventType eventType) {
        if (eventType == null) {
            return STANDARD_START;
        }
        return switch (eventType) {
            case EVENING -> LocalTime.of(16, 0);
            case AFTERNOON -> LocalTime.of(13, 0);
            case FULL_DAY -> LocalTime.of(9, 0);
        };
    }
}
