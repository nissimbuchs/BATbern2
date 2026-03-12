package ch.batbern.partners.service;

import ch.batbern.partners.client.dto.EventSummaryDTO;
import ch.batbern.partners.domain.PartnerMeeting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating RFC 5545 iCalendar (.ics) files for partner meetings.
 *
 * Story 8.3: Partner Meeting Coordination (AC3)
 *
 * Produces a VCALENDAR with two VEVENTs:
 *   1. The partner lunch/meeting itself (with SEQUENCE, DTSTAMP, ORGANIZER)
 *   2. The main BATbern event (as reminder context)
 *
 * SEQUENCE is taken from PartnerMeeting.inviteSequence and must be incremented
 * before calling generate() so that email clients (Outlook, Gmail, macOS Mail)
 * recognise repeated sends as updates of the same calendar entry, not new events.
 *
 * No external library required — RFC 5545 is plain CRLF-delimited text.
 */
@Service
@Slf4j
public class IcsGeneratorService {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final DateTimeFormatter UTC_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    @Value("${app.email.reply-to:replies@batbern.ch}")
    private String organizerEmail;

    /**
     * Generate an ICS calendar file with two VEVENTs.
     *
     * @param meeting          the partner meeting record (inviteSequence must already be incremented)
     * @param batbernEvent     summary of the linked BATbern event
     * @param recipientEmails  list of recipient email addresses (used to add ATTENDEE lines per RFC 5545)
     * @return ICS content as UTF-8 byte array
     */
    public byte[] generate(PartnerMeeting meeting, EventSummaryDTO batbernEvent, List<String> recipientEmails) {
        log.debug("Generating ICS for meeting={}, event={}, sequence={}, recipients={}",
                meeting.getId(), meeting.getEventCode(), meeting.getInviteSequence(), recipientEmails.size());

        // Single DTSTAMP shared across both VEVENTs (moment of generation)
        String dtstamp = UTC_FMT.format(Instant.now().atZone(ZoneId.of("UTC")));

        // Build extraLines for the partner meeting VEVENT: DTSTAMP, SEQUENCE, ORGANIZER, then ATTENDEE per recipient
        List<String> meetingExtraLines = new ArrayList<>();
        meetingExtraLines.add("DTSTAMP:" + dtstamp);
        meetingExtraLines.add("SEQUENCE:" + meeting.getInviteSequence());
        meetingExtraLines.add("ORGANIZER:mailto:" + organizerEmail);
        for (String email : recipientEmails) {
            meetingExtraLines.add("ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:" + email);
        }

        String ics = "BEGIN:VCALENDAR\r\n"
                + "VERSION:2.0\r\n"
                + "PRODID:-//BATbern//Partner Meeting//EN\r\n"
                + "CALSCALE:GREGORIAN\r\n"
                + "METHOD:REQUEST\r\n"
                // VEVENT 1: the partner meeting — includes SEQUENCE + ORGANIZER + ATTENDEE for RSVP tracking
                + buildVEvent(
                        meeting.getId() + "@batbern.ch",
                        toUtc(meeting.getMeetingDate(), meeting.getStartTime()),
                        toUtc(meeting.getMeetingDate(), meeting.getEndTime()),
                        "BATbern Partner Meeting (" + meeting.getMeetingType() + ")",
                        meeting.getAgenda() != null ? meeting.getAgenda() : "",
                        meeting.getLocation() != null ? meeting.getLocation() : "",
                        meetingExtraLines)
                // VEVENT 2: the main BATbern event as context (no SEQUENCE/ORGANIZER — not our event)
                + buildVEvent(
                        batbernEvent.eventCode() + "-main@batbern.ch",
                        toUtc(batbernEvent.eventDate(), batbernEvent.startTime()),
                        toUtc(batbernEvent.eventDate(), batbernEvent.endTime()),
                        batbernEvent.title(),
                        "BATbern Event — you are registered as a partner",
                        batbernEvent.venue() != null ? batbernEvent.venue() : "",
                        List.of("DTSTAMP:" + dtstamp))
                + "END:VCALENDAR\r\n";

        return ics.getBytes(StandardCharsets.UTF_8);
    }

    private String buildVEvent(
            String uid,
            String dtStart,
            String dtEnd,
            String summary,
            String description,
            String location,
            List<String> extraLines
    ) {
        StringBuilder sb = new StringBuilder()
                .append("BEGIN:VEVENT\r\n")
                .append("UID:").append(uid).append("\r\n")
                .append("DTSTART:").append(dtStart).append("\r\n")
                .append("DTEND:").append(dtEnd).append("\r\n")
                .append("SUMMARY:").append(foldLine(escapeText(summary))).append("\r\n")
                .append("DESCRIPTION:").append(foldLine(escapeText(description))).append("\r\n")
                .append("LOCATION:").append(foldLine(escapeText(location))).append("\r\n");
        for (String line : extraLines) {
            sb.append(line).append("\r\n");
        }
        sb.append("END:VEVENT\r\n");
        return sb.toString();
    }

    /**
     * Convert a local date + time in Europe/Zurich to UTC formatted string.
     */
    private String toUtc(LocalDate date, LocalTime time) {
        ZonedDateTime zurichDt = ZonedDateTime.of(date, time, ZURICH);
        ZonedDateTime utcDt = zurichDt.withZoneSameInstant(ZoneId.of("UTC"));
        return UTC_FMT.format(utcDt);
    }

    /**
     * Escape RFC 5545 special characters in text properties.
     * Commas, semicolons, and backslashes must be escaped.
     * Newlines become \n (literal backslash-n per RFC 5545).
     */
    private String escapeText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")
                .replace("\r\n", "\\n")
                .replace("\r", "\\n");
    }

    /**
     * Fold long lines per RFC 5545 §3.1 (max 75 octets, continuation with CRLF + SPACE).
     * Simple implementation: fold at 75 chars for ASCII-compatible strings.
     */
    private String foldLine(String text) {
        if (text.length() <= 75) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        while (pos < text.length()) {
            if (pos > 0) {
                sb.append("\r\n ");
            }
            int end = Math.min(pos + 75, text.length());
            sb.append(text, pos, end);
            pos = end;
        }
        return sb.toString();
    }
}
