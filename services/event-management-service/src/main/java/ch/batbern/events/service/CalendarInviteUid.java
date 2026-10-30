package ch.batbern.events.service;

import ch.batbern.events.domain.Registration;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The iCalendar UID of a registration invite: {@code reg-{calendarUid}@batbern.ch} (Story 16.0,
 * #1161). Written by {@link RegistrationEmailService}, read back from calendar "Declined" replies by
 * {@link CalendarDeclineService}. Both sides use this class so the format cannot drift.
 */
public final class CalendarInviteUid {

    private static final String PREFIX = "reg-";
    private static final Pattern REPLY_UID = Pattern.compile(
            "^reg-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})@batbern\\.ch$");

    private CalendarInviteUid() {
    }

    /** UID local part for the invite; the ICS generator appends {@code @batbern.ch}. */
    public static String forRegistration(Registration registration) {
        return PREFIX + registration.getCalendarUid();
    }

    /** The registration's calendar UID from a reply's UID, or empty for any other invite. */
    public static Optional<UUID> parse(String uid) {
        if (uid == null) {
            return Optional.empty();
        }
        Matcher m = REPLY_UID.matcher(uid.trim().toLowerCase(Locale.ROOT));
        return m.matches() ? Optional.of(UUID.fromString(m.group(1))) : Optional.empty();
    }
}
