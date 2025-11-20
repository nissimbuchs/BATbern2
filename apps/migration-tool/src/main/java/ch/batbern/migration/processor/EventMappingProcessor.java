package ch.batbern.migration.processor;

import ch.batbern.migration.model.legacy.LegacyEvent;
import ch.batbern.migration.model.target.EventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps legacy event data to target EventDto with German date parsing
 * Implements AC8: Parse 3 German date formats
 */
@Slf4j
@Component
public class EventMappingProcessor implements ItemProcessor<LegacyEvent, EventDto> {

    @Value("${migration.event.default-organizer-id:00000000-0000-0000-0000-000000000000}")
    private String defaultOrganizerId;

    private static final ZoneId ZURICH_ZONE = ZoneId.of("Europe/Zurich");

    // Date format parsers for German dates
    // Format 1: "24. Juni 05, 16:00h - 18:30h"
    // Use [\\p{L}]+ to match letters including umlauts (ä, ö, ü, ß)
    private static final Pattern FORMAT1_PATTERN = Pattern.compile(
        "(\\d{1,2})\\. ([\\p{L}]+) (\\d{2}), (\\d{2}):(\\d{2})h.*"
    );

    // Format 2: "Freitag, 15. Juni 2018, 16.00 bis 20.15 Uhr"
    private static final Pattern FORMAT2_PATTERN = Pattern.compile(
        "[\\p{L}]+, (\\d{1,2})\\. ([\\p{L}]+) (\\d{4}), (\\d{2})\\.(\\d{2}).*"
    );

    // German month names mapping
    private static final String[] GERMAN_MONTHS = {
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    };

    @Override
    public EventDto process(LegacyEvent legacy) throws Exception {
        log.debug("Processing event: BAT {}", legacy.getBat());

        EventDto event = new EventDto();

        // Event code: "BATbern" + bat number
        event.setEventCode("BATbern" + legacy.getBat());
        event.setEventNumber(legacy.getBat());

        // Title: trim whitespace from topic
        event.setTitle(legacy.getTopic() != null ? legacy.getTopic().trim() : "");

        // Parse German date to ZonedDateTime
        ZonedDateTime eventDate = parseGermanDate(legacy.getDatum());
        event.setDate(eventDate);

        // Event type
        event.setEventType(legacy.getEventType());

        // Status: ARCHIVED for all historical events (AC7)
        event.setStatus("ARCHIVED");

        // Default venue for historical events (AC8)
        event.setVenueName("Kornhausforum");
        event.setVenueAddress("Kornhausplatz 18, 3011 Bern");
        event.setVenueCapacity(200);

        // Organizer: system user for historical events
        event.setOrganizerId(defaultOrganizerId);

        log.info("Mapped event: {} → eventCode={}, date={}",
            legacy.getBat(), event.getEventCode(), event.getDate());

        return event;
    }

    /**
     * Parse German date strings in 3 formats (AC8)
     * - Format 1: "24. Juni 05, 16:00h - 18:30h"
     * - Format 2: "Freitag, 15. Juni 2018, 16.00 bis 20.15 Uhr"
     * - Format 3: ISO 8601 "2021-11-19T16:00:00Z"
     */
    private ZonedDateTime parseGermanDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Date string cannot be null or empty");
        }

        dateStr = dateStr.trim();

        // Try ISO 8601 format first (Format 3)
        if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}T.*")) {
            try {
                return ZonedDateTime.parse(dateStr);
            } catch (Exception e) {
                log.warn("Failed to parse ISO date: {}", dateStr, e);
            }
        }

        // Try Format 1: "24. Juni 05, 16:00h - 18:30h"
        Matcher format1Matcher = FORMAT1_PATTERN.matcher(dateStr);
        if (format1Matcher.matches()) {
            int day = Integer.parseInt(format1Matcher.group(1));
            String monthName = format1Matcher.group(2);
            int year = Integer.parseInt(format1Matcher.group(3));
            int hour = Integer.parseInt(format1Matcher.group(4));
            int minute = Integer.parseInt(format1Matcher.group(5));

            // Convert 2-digit year to 4-digit (05 → 2005)
            year = year < 50 ? 2000 + year : 1900 + year;

            int month = getMonthFromGermanName(monthName);

            LocalDateTime localDateTime = LocalDateTime.of(year, month, day, hour, minute);
            return ZonedDateTime.of(localDateTime, ZURICH_ZONE);
        }

        // Try Format 2: "Freitag, 15. Juni 2018, 16.00 bis 20.15 Uhr"
        Matcher format2Matcher = FORMAT2_PATTERN.matcher(dateStr);
        if (format2Matcher.matches()) {
            int day = Integer.parseInt(format2Matcher.group(1));
            String monthName = format2Matcher.group(2);
            int year = Integer.parseInt(format2Matcher.group(3));
            int hour = Integer.parseInt(format2Matcher.group(4));
            int minute = Integer.parseInt(format2Matcher.group(5));

            int month = getMonthFromGermanName(monthName);

            LocalDateTime localDateTime = LocalDateTime.of(year, month, day, hour, minute);
            return ZonedDateTime.of(localDateTime, ZURICH_ZONE);
        }

        log.error("Unable to parse date (no matching format): {}", dateStr);
        throw new IllegalArgumentException("Unable to parse date: " + dateStr);
    }

    /**
     * Map German month name to month number
     */
    private int getMonthFromGermanName(String monthName) {
        for (int i = 0; i < GERMAN_MONTHS.length; i++) {
            if (GERMAN_MONTHS[i].equalsIgnoreCase(monthName)) {
                return i + 1; // Month numbers are 1-based
            }
        }
        throw new IllegalArgumentException("Unknown German month: " + monthName);
    }
}
