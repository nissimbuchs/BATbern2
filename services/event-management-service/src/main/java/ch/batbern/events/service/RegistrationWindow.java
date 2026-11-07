package ch.batbern.events.service;

import ch.batbern.events.domain.Event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The dates that open and close registration actions for an event (#1238), in Bern time.
 *
 * <ul>
 *   <li>Registration and waitlist close at the registration deadline, or at the event start when
 *       no deadline is set ({@link #isDeadlinePassed}).</li>
 *   <li>Unregistering is possible until the end of the day before the event (E2): from 00:00 Bern
 *       on the event day it is closed ({@link #isEventDayReached}), for every path.</li>
 *   <li>A cancellation after the deadline does not move anyone up from the waitlist (E2): the name
 *       badges are printed by then.</li>
 * </ul>
 * The public page mirrors these rules in {@code homePagePhase.ts} ({@code getRegistrationView}).
 */
public final class RegistrationWindow {

    static final ZoneId BERN = ZoneId.of("Europe/Zurich");

    private RegistrationWindow() {
    }

    /** The registration deadline, or the event start when none is set; null when neither is. */
    public static Instant deadline(Event event) {
        return event.getRegistrationDeadline() != null ? event.getRegistrationDeadline() : event.getDate();
    }

    /** True once {@code now} is after the deadline (or the event start without one). */
    public static boolean isDeadlinePassed(Event event, Instant now) {
        Instant deadline = deadline(event);
        return deadline != null && now.isAfter(deadline);
    }

    /** True from 00:00 Bern on the event's calendar day: no unregistering from then on. */
    public static boolean isEventDayReached(Event event, Instant now) {
        if (event.getDate() == null) {
            return false;
        }
        LocalDate eventDay = LocalDate.ofInstant(event.getDate(), BERN);
        return !LocalDate.ofInstant(now, BERN).isBefore(eventDay);
    }
}
