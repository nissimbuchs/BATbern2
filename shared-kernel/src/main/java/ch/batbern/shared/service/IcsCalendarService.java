package ch.batbern.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Service for generating iCalendar (.ics) files for event invitations.
 *
 * Follows RFC 5545 (Internet Calendaring and Scheduling Core Object Specification)
 * https://tools.ietf.org/html/rfc5545
 *
 * Story 2.2a Task B12: Calendar attachments for registration confirmations
 */
@Service
@Slf4j
public class IcsCalendarService {

    private static final DateTimeFormatter ICS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    /**
     * Generate an .ics calendar file for an event.
     *
     * @param eventTitle Event title
     * @param eventDescription Event description
     * @param eventLocation Event venue address
     * @param startDateTime Event start date/time (UTC)
     * @param endDateTime Event end date/time (UTC)
     * @param organizerEmail Organizer email address
     * @param organizerName Organizer name
     * @return .ics file content as byte array
     */
    public byte[] generateIcsFile(
            String eventTitle,
            String eventDescription,
            String eventLocation,
            ZonedDateTime startDateTime,
            ZonedDateTime endDateTime,
            String organizerEmail,
            String organizerName
    ) {
        log.debug("Generating .ics file for event: {}", eventTitle);

        // Convert to UTC for iCalendar format
        String startIcs = startDateTime.withZoneSameInstant(java.time.ZoneOffset.UTC)
                .format(ICS_DATE_FORMAT);
        String endIcs = endDateTime.withZoneSameInstant(java.time.ZoneOffset.UTC)
                .format(ICS_DATE_FORMAT);
        String now = ZonedDateTime.now(java.time.ZoneOffset.UTC).format(ICS_DATE_FORMAT);

        // Generate unique UID for the event
        String uid = UUID.randomUUID().toString() + "@batbern.ch";

        // Build iCalendar content (RFC 5545)
        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//BATbern//Event Registration//EN\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:REQUEST\r\n");
        ics.append("BEGIN:VEVENT\r\n");
        ics.append("UID:").append(uid).append("\r\n");
        ics.append("DTSTAMP:").append(now).append("\r\n");
        ics.append("DTSTART:").append(startIcs).append("\r\n");
        ics.append("DTEND:").append(endIcs).append("\r\n");
        ics.append("SUMMARY:").append(escapeIcsText(eventTitle)).append("\r\n");

        if (eventDescription != null && !eventDescription.isEmpty()) {
            ics.append("DESCRIPTION:").append(escapeIcsText(eventDescription)).append("\r\n");
        }

        if (eventLocation != null && !eventLocation.isEmpty()) {
            ics.append("LOCATION:").append(escapeIcsText(eventLocation)).append("\r\n");
        }

        ics.append("ORGANIZER;CN=").append(escapeIcsText(organizerName))
           .append(":mailto:").append(organizerEmail).append("\r\n");
        ics.append("STATUS:CONFIRMED\r\n");
        ics.append("SEQUENCE:0\r\n");
        ics.append("BEGIN:VALARM\r\n");
        ics.append("TRIGGER:-PT1H\r\n"); // Reminder 1 hour before
        ics.append("ACTION:DISPLAY\r\n");
        ics.append("DESCRIPTION:Event reminder\r\n");
        ics.append("END:VALARM\r\n");
        ics.append("END:VEVENT\r\n");
        ics.append("END:VCALENDAR\r\n");

        log.debug("Generated .ics file with UID: {}", uid);
        return ics.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Data record for a single event to include in an ICS file.
     *
     * <p>{@code uid} is the stable local-part of the iCalendar UID (the
     * {@code @batbern.ch} suffix is appended by the generators). Passing a stable,
     * event-specific value (e.g. the event code) is what lets calendar clients
     * recognise a re-sent invite as an UPDATE of the same entry instead of a
     * duplicate. The legacy constructor defaults it to a random UUID, preserving
     * the old "new entry every time" behaviour for callers that have not migrated.
     */
    public record IcsEventData(
            String uid,
            String title,
            String description,
            String location,
            ZonedDateTime start,
            ZonedDateTime end
    ) {
        /** Backward-compatible constructor — {@code uid} defaults to a random UUID. */
        public IcsEventData(
                String title,
                String description,
                String location,
                ZonedDateTime start,
                ZonedDateTime end
        ) {
            this(UUID.randomUUID().toString(), title, description, location, start, end);
        }
    }

    /**
     * Generate a single .ics file containing multiple VEVENTs (e.g., current event + upcoming events).
     * Uses METHOD:PUBLISH since these are informational, not meeting invitations.
     */
    public byte[] generateMultiEventIcsFile(
            List<IcsEventData> events,
            String organizerEmail,
            String organizerName
    ) {
        log.debug("Generating multi-event .ics file with {} events", events.size());

        String now = ZonedDateTime.now(java.time.ZoneOffset.UTC).format(ICS_DATE_FORMAT);

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//BATbern//Newsletter Events//EN\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:PUBLISH\r\n");

        for (IcsEventData event : events) {
            // Use the event's stable UID (random only if the caller used the legacy constructor)
            // so re-sent newsletters update the same entry rather than piling up duplicates.
            String uid = event.uid() + "@batbern.ch";
            String startIcs = event.start().withZoneSameInstant(java.time.ZoneOffset.UTC)
                    .format(ICS_DATE_FORMAT);
            String endIcs = event.end().withZoneSameInstant(java.time.ZoneOffset.UTC)
                    .format(ICS_DATE_FORMAT);

            ics.append("BEGIN:VEVENT\r\n");
            ics.append("UID:").append(uid).append("\r\n");
            ics.append("DTSTAMP:").append(now).append("\r\n");
            ics.append("DTSTART:").append(startIcs).append("\r\n");
            ics.append("DTEND:").append(endIcs).append("\r\n");
            ics.append("SUMMARY:").append(escapeIcsText(event.title())).append("\r\n");

            if (event.description() != null && !event.description().isEmpty()) {
                ics.append("DESCRIPTION:").append(escapeIcsText(event.description())).append("\r\n");
            }
            if (event.location() != null && !event.location().isEmpty()) {
                ics.append("LOCATION:").append(escapeIcsText(event.location())).append("\r\n");
            }

            ics.append("ORGANIZER;CN=").append(escapeIcsText(organizerName))
               .append(":mailto:").append(organizerEmail).append("\r\n");
            ics.append("STATUS:CONFIRMED\r\n");
            ics.append("SEQUENCE:0\r\n");
            ics.append("BEGIN:VALARM\r\n");
            ics.append("TRIGGER:-PT1H\r\n");
            ics.append("ACTION:DISPLAY\r\n");
            ics.append("DESCRIPTION:Event reminder\r\n");
            ics.append("END:VALARM\r\n");
            ics.append("END:VEVENT\r\n");
        }

        ics.append("END:VCALENDAR\r\n");

        log.debug("Generated multi-event .ics file with {} VEVENTs", events.size());
        return ics.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Generate a single-VEVENT {@code METHOD:REQUEST} .ics for ONE event.
     *
     * <p>This is the calendar-invite form (as opposed to {@link #generateMultiEventIcsFile}'s
     * informational {@code METHOD:PUBLISH}). Combined with a <em>stable</em> {@code UID} and a
     * monotonically-increasing {@code SEQUENCE}, calendar clients (Outlook, Apple Mail, Gmail)
     * recognise a re-opened or re-sent file as an <strong>update of the same entry</strong>
     * rather than creating a duplicate.
     *
     * <p>Deliberately a single VEVENT: Outlook only processes the first VEVENT in a
     * multi-event {@code REQUEST}, so bundling several events here would silently drop all
     * but the first. Bundle informational events via {@link #generateMultiEventIcsFile} instead.
     *
     * <p>No {@code ATTENDEE} line is emitted — the file is sent as an email attachment, not an
     * addressed iMIP invitation, so there is no recipient to track and no RSVP response is
     * solicited. {@code ORGANIZER} is still required for Outlook to treat the file as a
     * meeting it can match by UID.
     *
     * @param event          the event (its {@code uid()} should be stable, e.g. the event code)
     * @param sequence       revision counter; pass a value that only ever increases for the
     *                       same UID (e.g. seconds since a fixed epoch derived from the event's
     *                       last-modified time). Clamped to {@code >= 0}.
     * @param organizerEmail organizer mailbox (e.g. {@code noreply@batbern.ch})
     * @param organizerName  organizer display name
     * @return .ics file content as a UTF-8 byte array
     */
    public byte[] generateRequestIcsFile(
            IcsEventData event,
            int sequence,
            String organizerEmail,
            String organizerName
    ) {
        String uid = event.uid() + "@batbern.ch";
        log.debug("Generating METHOD:REQUEST .ics for event UID: {}, sequence: {}", uid, sequence);

        String startIcs = event.start().withZoneSameInstant(java.time.ZoneOffset.UTC)
                .format(ICS_DATE_FORMAT);
        String endIcs = event.end().withZoneSameInstant(java.time.ZoneOffset.UTC)
                .format(ICS_DATE_FORMAT);
        String now = ZonedDateTime.now(java.time.ZoneOffset.UTC).format(ICS_DATE_FORMAT);

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//BATbern//Newsletter Event//EN\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:REQUEST\r\n");
        ics.append("BEGIN:VEVENT\r\n");
        ics.append("UID:").append(uid).append("\r\n");
        ics.append("DTSTAMP:").append(now).append("\r\n");
        ics.append("DTSTART:").append(startIcs).append("\r\n");
        ics.append("DTEND:").append(endIcs).append("\r\n");
        ics.append("SUMMARY:").append(escapeIcsText(event.title())).append("\r\n");

        if (event.description() != null && !event.description().isEmpty()) {
            ics.append("DESCRIPTION:").append(escapeIcsText(event.description())).append("\r\n");
        }
        if (event.location() != null && !event.location().isEmpty()) {
            ics.append("LOCATION:").append(escapeIcsText(event.location())).append("\r\n");
        }

        ics.append("ORGANIZER;CN=").append(escapeIcsText(organizerName))
           .append(":mailto:").append(organizerEmail).append("\r\n");
        ics.append("STATUS:CONFIRMED\r\n");
        ics.append("SEQUENCE:").append(Math.max(0, sequence)).append("\r\n");
        ics.append("END:VEVENT\r\n");
        ics.append("END:VCALENDAR\r\n");

        return ics.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Escape special characters in iCalendar text fields.
     * RFC 5545: Comma, semicolon, and backslash must be escaped.
     *
     * @param text Text to escape
     * @return Escaped text
     */
    private String escapeIcsText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")  // Backslash first
                .replace(",", "\\,")
                .replace(";", "\\;")
                .replace("\n", "\\n");  // Newlines become literal \n
    }
}
