package ch.batbern.shared.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IcsCalendarService Tests")
class IcsCalendarServiceTest {

    private final IcsCalendarService service = new IcsCalendarService();

    @Test
    @DisplayName("generateMultiEventIcsFile: produces valid VCALENDAR with multiple VEVENTs")
    void multiEvent_producesMultipleVevents() {
        ZonedDateTime now = ZonedDateTime.of(2026, 6, 15, 16, 0, 0, 0, ZoneId.of("Europe/Zurich"));

        List<IcsCalendarService.IcsEventData> events = List.of(
                new IcsCalendarService.IcsEventData(
                        "BATbern #58", "AI in Software", "Welle 7, Bern",
                        now, now.plusHours(4)),
                new IcsCalendarService.IcsEventData(
                        "BATbern #59", "Cloud Native", "PostFinance Arena",
                        now.plusMonths(3), now.plusMonths(3).plusHours(4))
        );

        byte[] icsBytes = service.generateMultiEventIcsFile(events, "noreply@batbern.ch", "BATbern");
        String ics = new String(icsBytes, StandardCharsets.UTF_8);

        assertThat(ics).startsWith("BEGIN:VCALENDAR");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("METHOD:PUBLISH");
        assertThat(ics).contains("PRODID:-//BATbern//Newsletter Events//EN");

        // Two VEVENTs
        assertThat(countOccurrences(ics, "BEGIN:VEVENT")).isEqualTo(2);
        assertThat(countOccurrences(ics, "END:VEVENT")).isEqualTo(2);

        // Event content
        assertThat(ics).contains("SUMMARY:BATbern #58");
        assertThat(ics).contains("SUMMARY:BATbern #59");
        assertThat(ics).contains("LOCATION:Welle 7\\, Bern");
        assertThat(ics).contains("DESCRIPTION:AI in Software");
        assertThat(ics).contains("ORGANIZER;CN=BATbern:mailto:noreply@batbern.ch");

        // Each VEVENT has a unique UID
        int uidCount = countOccurrences(ics, "UID:");
        assertThat(uidCount).isEqualTo(2);

        // Each VEVENT has a VALARM
        assertThat(countOccurrences(ics, "TRIGGER:-PT1H")).isEqualTo(2);
    }

    @Test
    @DisplayName("generateMultiEventIcsFile: single event produces valid output")
    void singleEvent_producesOneVevent() {
        ZonedDateTime start = ZonedDateTime.of(2026, 3, 6, 16, 0, 0, 0, ZoneId.of("Europe/Zurich"));

        byte[] icsBytes = service.generateMultiEventIcsFile(
                List.of(new IcsCalendarService.IcsEventData(
                        "BATbern #58", "", "Welle 7", start, start.plusHours(3))),
                "noreply@batbern.ch", "BATbern");
        String ics = new String(icsBytes, StandardCharsets.UTF_8);

        assertThat(countOccurrences(ics, "BEGIN:VEVENT")).isEqualTo(1);
        assertThat(ics).contains("SUMMARY:BATbern #58");
        // Empty event description should not produce a DESCRIPTION line for the event
        // (VALARM has its own DESCRIPTION:Event reminder which is expected)
        assertThat(countOccurrences(ics, "DESCRIPTION:")).isEqualTo(1); // only the VALARM one
    }

    @Test
    @DisplayName("generateMultiEventIcsFile: escapes RFC 5545 special characters")
    void specialChars_areEscaped() {
        ZonedDateTime start = ZonedDateTime.of(2026, 3, 6, 16, 0, 0, 0, ZoneId.of("Europe/Zurich"));

        byte[] icsBytes = service.generateMultiEventIcsFile(
                List.of(new IcsCalendarService.IcsEventData(
                        "Event; with, special\\chars", "Line1\nLine2", "Bern, Switzerland",
                        start, start.plusHours(3))),
                "noreply@batbern.ch", "BATbern");
        String ics = new String(icsBytes, StandardCharsets.UTF_8);

        assertThat(ics).contains("SUMMARY:Event\\; with\\, special\\\\chars");
        assertThat(ics).contains("DESCRIPTION:Line1\\nLine2");
        assertThat(ics).contains("LOCATION:Bern\\, Switzerland");
    }

    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
