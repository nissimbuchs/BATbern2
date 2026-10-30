package ch.batbern.events.forwarding;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 16.0 AC6/AC7: what a calendar "Declined" reply carries, as mail clients really send it. */
@DisplayName("CalendarReply")
class CalendarReplyTest {

    @Test
    @DisplayName("should_readUidPartstatAndStart_when_outlookStyleMultipartReply")
    void should_readUidPartstatAndStart_when_outlookStyleMultipartReply() throws Exception {
        CalendarReply reply = CalendarReply.parse(message(multipart(
                "BEGIN:VCALENDAR\r\nMETHOD:REPLY\r\nBEGIN:VEVENT\r\n"
                + "ATTENDEE;PARTSTAT=DECLINED;CN=Test Person:mailto:success+p@simulator.amazonses.com\r\n"
                + "UID:reg-0b6e3f8a-1d2c-4b5a-9e8f-7a6b5c4d3e2f@batbern.ch\r\n"
                + "SUMMARY:BATbern #60\r\nDTSTART:20261203T170000Z\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n")))
                .orElseThrow();

        assertThat(reply.uid()).isEqualTo("reg-0b6e3f8a-1d2c-4b5a-9e8f-7a6b5c4d3e2f@batbern.ch");
        assertThat(reply.partStat()).isEqualTo("DECLINED");
        assertThat(reply.declined()).isTrue();
        assertThat(reply.summary()).isEqualTo("BATbern #60");
        assertThat(reply.start()).isEqualTo(Instant.parse("2026-12-03T17:00:00Z"));
    }

    @Test
    @DisplayName("should_unfoldLinesAndReadTzidStart_when_appleStyleReply")
    void should_unfoldLinesAndReadTzidStart_when_appleStyleReply() throws Exception {
        CalendarReply reply = CalendarReply.parse(message(multipart(
                "BEGIN:VCALENDAR\r\nMETHOD:REPLY\r\nBEGIN:VEVENT\r\n"
                + "UID:7c9e6679-7425-40de-944b-e07fc1f9\r\n 0ae7@batbern.ch\r\n"
                + "ATTENDEE;CN=\"Test Person\";PARTSTAT=TENTATIVE\r\n :mailto:success+p@simulator.amazonses.com\r\n"
                + "DTSTART;TZID=Europe/Zurich:20261203T180000\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n")))
                .orElseThrow();

        assertThat(reply.uid()).isEqualTo("7c9e6679-7425-40de-944b-e07fc1f90ae7@batbern.ch");
        assertThat(reply.partStat()).isEqualTo("TENTATIVE");
        assertThat(reply.declined()).isFalse();
        assertThat(reply.start()).isEqualTo(Instant.parse("2026-12-03T17:00:00Z"));
    }

    @Test
    @DisplayName("should_beEmpty_when_noCalendarPart")
    void should_beEmpty_when_noCalendarPart() throws Exception {
        assertThat(CalendarReply.parse(message("From: a@simulator.amazonses.com\r\nTo: calendar@batbern.ch\r\n"
                + "Subject: x\r\nContent-Type: text/plain\r\n\r\nhello\r\n"))).isEmpty();
    }

    private static String multipart(String ics) {
        return "From: \"Test Person\" <success+p@simulator.amazonses.com>\r\n"
                + "To: calendar@batbern.ch\r\nSubject: Declined: BATbern #60\r\nMIME-Version: 1.0\r\n"
                + "Content-Type: multipart/alternative; boundary=\"b1\"\r\n\r\n"
                + "--b1\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nDeclined\r\n"
                + "--b1\r\nContent-Type: text/calendar; charset=UTF-8; method=REPLY\r\n\r\n"
                + ics + "--b1--\r\n";
    }

    private static MimeMessage message(String raw) throws Exception {
        return new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
    }
}
