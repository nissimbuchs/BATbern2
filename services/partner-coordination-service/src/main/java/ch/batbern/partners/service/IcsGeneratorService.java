package ch.batbern.partners.service;

import ch.batbern.partners.client.dto.EventSummaryDTO;
import ch.batbern.partners.domain.PartnerMeeting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for generating RFC 5545 iCalendar (.ics) files for partner meetings.
 *
 * Story 8.3: Partner Meeting Coordination (AC3)
 *
 * Produces a VCALENDAR with two VEVENTs:
 *   1. The partner lunch/meeting itself
 *   2. The main BATbern event (as reminder context)
 *
 * No external library required — RFC 5545 is plain CRLF-delimited text.
 */
@Service
@Slf4j
public class IcsGeneratorService {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final DateTimeFormatter UTC_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    /**
     * Generate an ICS calendar file with two VEVENTs.
     *
     * @param meeting      the partner meeting record
     * @param batbernEvent summary of the linked BATbern event
     * @return ICS content as UTF-8 byte array
     */
    public byte[] generate(PartnerMeeting meeting, EventSummaryDTO batbernEvent) {
        log.debug("Generating ICS for meeting={}, event={}", meeting.getId(), meeting.getEventCode());

        String ics = "BEGIN:VCALENDAR\r\n"
                + "VERSION:2.0\r\n"
                + "PRODID:-//BATbern//Partner Meeting//EN\r\n"
                + "CALSCALE:GREGORIAN\r\n"
                + "METHOD:REQUEST\r\n"
                + buildVEvent(
                        meeting.getId() + "@batbern.ch",
                        toUtc(meeting.getMeetingDate(), meeting.getStartTime()),
                        toUtc(meeting.getMeetingDate(), meeting.getEndTime()),
                        "BATbern Partner Meeting (" + meeting.getMeetingType() + ")",
                        meeting.getAgenda() != null ? meeting.getAgenda() : "",
                        meeting.getLocation() != null ? meeting.getLocation() : "")
                + buildVEvent(
                        batbernEvent.eventCode() + "-main@batbern.ch",
                        toUtc(batbernEvent.eventDate(), batbernEvent.startTime()),
                        toUtc(batbernEvent.eventDate(), batbernEvent.endTime()),
                        batbernEvent.title(),
                        "BATbern Event — you are registered as a partner",
                        batbernEvent.venue() != null ? batbernEvent.venue() : "")
                + "END:VCALENDAR\r\n";

        return ics.getBytes(StandardCharsets.UTF_8);
    }

    private String buildVEvent(
            String uid,
            String dtStart,
            String dtEnd,
            String summary,
            String description,
            String location
    ) {
        return "BEGIN:VEVENT\r\n"
                + "UID:" + uid + "\r\n"
                + "DTSTART:" + dtStart + "\r\n"
                + "DTEND:" + dtEnd + "\r\n"
                + "SUMMARY:" + foldLine(escapeText(summary)) + "\r\n"
                + "DESCRIPTION:" + foldLine(escapeText(description)) + "\r\n"
                + "LOCATION:" + foldLine(escapeText(location)) + "\r\n"
                + "END:VEVENT\r\n";
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
