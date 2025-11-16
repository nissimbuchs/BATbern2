package ch.batbern.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
