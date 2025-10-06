package ch.batbern.shared.unit.utils;

import ch.batbern.shared.utils.DateTimeUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class DateTimeUtilsTest {

    @Nested
    @DisplayName("Timestamp Generation")
    class TimestampGeneration {

        @Test
        void should_generateCurrentTimestamp_when_called() {
            Instant before = Instant.now();
            Instant timestamp = DateTimeUtils.now();
            Instant after = Instant.now();

            assertThat(timestamp)
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
        }

        @Test
        void should_generateUTCTimestamp_when_requested() {
            ZonedDateTime utcTime = DateTimeUtils.nowUTC();

            assertThat(utcTime.getZone()).isEqualTo(ZoneId.of("UTC"));
            assertThat(utcTime.toInstant()).isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
        }

        @Test
        void should_generateSwissTimestamp_when_requested() {
            ZonedDateTime swissTime = DateTimeUtils.nowSwiss();

            assertThat(swissTime.getZone()).isEqualTo(ZoneId.of("Europe/Zurich"));
        }

        @Test
        void should_generateMillisecondPrecision_when_timestampCreated() {
            Instant timestamp = DateTimeUtils.nowWithMillisPrecision();

            assertThat(timestamp.getNano() % 1000000).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Date Formatting")
    class DateFormatting {

        @Test
        void should_formatAsISO8601_when_instantProvided() {
            Instant instant = Instant.parse("2024-01-15T10:30:00Z");

            String formatted = DateTimeUtils.formatISO8601(instant);

            assertThat(formatted).isEqualTo("2024-01-15T10:30:00Z");
        }

        @Test
        void should_formatAsSwissDate_when_localDateProvided() {
            LocalDate date = LocalDate.of(2024, 1, 15);

            String formatted = DateTimeUtils.formatSwissDate(date);

            assertThat(formatted).isEqualTo("15.01.2024");
        }

        @Test
        void should_formatAsSwissDateTime_when_localDateTimeProvided() {
            LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 14, 30, 45);

            String formatted = DateTimeUtils.formatSwissDateTime(dateTime);

            assertThat(formatted).isEqualTo("15.01.2024 14:30:45");
        }

        @Test
        void should_formatAsEventBridgeTimestamp_when_instantProvided() {
            Instant instant = Instant.parse("2024-01-15T10:30:00Z");

            String formatted = DateTimeUtils.formatForEventBridge(instant);

            assertThat(formatted).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z");
        }

        @Test
        void should_formatAsRFC3339_when_zonedDateTimeProvided() {
            ZonedDateTime dateTime = ZonedDateTime.parse("2024-01-15T10:30:00+01:00[Europe/Zurich]");

            String formatted = DateTimeUtils.formatRFC3339(dateTime);

            assertThat(formatted).contains("2024-01-15");
            assertThat(formatted).contains("10:30:00");
            assertThat(formatted).contains("+01:00");
        }
    }

    @Nested
    @DisplayName("Date Parsing")
    class DateParsing {

        @Test
        void should_parseISO8601_when_validStringProvided() {
            String isoString = "2024-01-15T10:30:00Z";

            Instant parsed = DateTimeUtils.parseISO8601(isoString);

            assertThat(parsed).isEqualTo(Instant.parse(isoString));
        }

        @Test
        void should_throwException_when_invalidISO8601Provided() {
            String invalidString = "2024-01-15 10:30:00";

            assertThatThrownBy(() -> DateTimeUtils.parseISO8601(invalidString))
                .isInstanceOf(DateTimeParseException.class);
        }

        @Test
        void should_parseSwissDate_when_validFormatProvided() {
            String swissDate = "15.01.2024";

            LocalDate parsed = DateTimeUtils.parseSwissDate(swissDate);

            assertThat(parsed).isEqualTo(LocalDate.of(2024, 1, 15));
        }

        @Test
        void should_parseFlexibleFormat_when_variousFormatsProvided() {
            String[] formats = {
                "2024-01-15T10:30:00Z",
                "2024-01-15 10:30:00",
                "15.01.2024",
                "15/01/2024",
                "01/15/2024"
            };

            for (String format : formats) {
                var parsed = DateTimeUtils.parseFlexible(format);
                assertThat(parsed).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Date Calculations")
    class DateCalculations {

        @Test
        void should_calculateEventQuarter_when_dateProvided() {
            LocalDate q1Date = LocalDate.of(2024, 2, 15);
            LocalDate q2Date = LocalDate.of(2024, 5, 15);
            LocalDate q3Date = LocalDate.of(2024, 8, 15);
            LocalDate q4Date = LocalDate.of(2024, 11, 15);

            assertThat(DateTimeUtils.getQuarter(q1Date)).isEqualTo("Q1 2024");
            assertThat(DateTimeUtils.getQuarter(q2Date)).isEqualTo("Q2 2024");
            assertThat(DateTimeUtils.getQuarter(q3Date)).isEqualTo("Q3 2024");
            assertThat(DateTimeUtils.getQuarter(q4Date)).isEqualTo("Q4 2024");
        }

        @Test
        void should_calculateDaysBetween_when_twoDatesProvided() {
            LocalDate start = LocalDate.of(2024, 1, 1);
            LocalDate end = LocalDate.of(2024, 1, 15);

            long days = DateTimeUtils.daysBetween(start, end);

            assertThat(days).isEqualTo(14);
        }

        @Test
        void should_calculateBusinessDays_when_excludingWeekends() {
            LocalDate start = LocalDate.of(2024, 1, 1); // Monday
            LocalDate end = LocalDate.of(2024, 1, 15);

            long businessDays = DateTimeUtils.businessDaysBetween(start, end);

            assertThat(businessDays).isEqualTo(10); // 2 weeks = 10 business days
        }

        @Test
        void should_addBusinessDays_when_skipWeekends() {
            LocalDate friday = LocalDate.of(2024, 1, 5);

            LocalDate result = DateTimeUtils.addBusinessDays(friday, 1);

            assertThat(result).isEqualTo(LocalDate.of(2024, 1, 8)); // Monday
        }

        @Test
        void should_determineIfWorkday_when_dateProvided() {
            LocalDate monday = LocalDate.of(2024, 1, 1);
            LocalDate saturday = LocalDate.of(2024, 1, 6);
            LocalDate sunday = LocalDate.of(2024, 1, 7);

            assertThat(DateTimeUtils.isWorkday(monday)).isTrue();
            assertThat(DateTimeUtils.isWorkday(saturday)).isFalse();
            assertThat(DateTimeUtils.isWorkday(sunday)).isFalse();
        }
    }

    @Nested
    @DisplayName("Time Zone Conversions")
    class TimeZoneConversions {

        @Test
        void should_convertToSwissTime_when_utcProvided() {
            Instant utcTime = Instant.parse("2024-01-15T10:00:00Z");

            ZonedDateTime swissTime = DateTimeUtils.toSwissTime(utcTime);

            assertThat(swissTime.getZone()).isEqualTo(ZoneId.of("Europe/Zurich"));
            assertThat(swissTime.getHour()).isEqualTo(11); // UTC+1 in winter
        }

        @Test
        void should_convertFromSwissToUTC_when_swissTimeProvided() {
            ZonedDateTime swissTime = ZonedDateTime.of(
                2024, 1, 15, 11, 0, 0, 0,
                ZoneId.of("Europe/Zurich")
            );

            Instant utc = DateTimeUtils.toUTC(swissTime);

            assertThat(utc).isEqualTo(Instant.parse("2024-01-15T10:00:00Z"));
        }

        @Test
        void should_handleDaylightSaving_when_summerDateProvided() {
            Instant summerUtc = Instant.parse("2024-07-15T10:00:00Z");

            ZonedDateTime swissTime = DateTimeUtils.toSwissTime(summerUtc);

            assertThat(swissTime.getHour()).isEqualTo(12); // UTC+2 in summer
        }
    }

    @Nested
    @DisplayName("Date Validation")
    class DateValidation {

        @Test
        void should_validateFutureDate_when_checkingEventDate() {
            LocalDate futureDate = LocalDate.now().plusDays(30);
            LocalDate pastDate = LocalDate.now().minusDays(30);

            assertThat(DateTimeUtils.isFuture(futureDate)).isTrue();
            assertThat(DateTimeUtils.isFuture(pastDate)).isFalse();
        }

        @Test
        void should_validatePastDate_when_checkingHistoricalData() {
            Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
            Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

            assertThat(DateTimeUtils.isPast(past)).isTrue();
            assertThat(DateTimeUtils.isPast(future)).isFalse();
        }

        @Test
        void should_validateDateRange_when_checkingBounds() {
            LocalDate date = LocalDate.of(2024, 6, 15);
            LocalDate start = LocalDate.of(2024, 1, 1);
            LocalDate end = LocalDate.of(2024, 12, 31);

            boolean inRange = DateTimeUtils.isInRange(date, start, end);

            assertThat(inRange).isTrue();
        }

        @Test
        void should_validateSameQuarter_when_comparingDates() {
            LocalDate date1 = LocalDate.of(2024, 1, 15);
            LocalDate date2 = LocalDate.of(2024, 2, 28);
            LocalDate date3 = LocalDate.of(2024, 4, 1);

            assertThat(DateTimeUtils.isSameQuarter(date1, date2)).isTrue();
            assertThat(DateTimeUtils.isSameQuarter(date1, date3)).isFalse();
        }
    }

    @Nested
    @DisplayName("Duration and Period Handling")
    class DurationAndPeriodHandling {

        @Test
        void should_formatDuration_when_millisecondsProvided() {
            long duration = 3661000; // 1 hour, 1 minute, 1 second

            String formatted = DateTimeUtils.formatDuration(duration);

            assertThat(formatted).isEqualTo("1h 1m 1s");
        }

        @Test
        void should_parseHumanReadableDuration_when_stringProvided() {
            String[] durations = {
                "5 minutes",
                "2 hours",
                "3 days",
                "1 week"
            };

            for (String duration : durations) {
                long millis = DateTimeUtils.parseHumanDuration(duration);
                assertThat(millis).isGreaterThan(0);
            }
        }

        @Test
        void should_calculateAge_when_birthDateProvided() {
            LocalDate birthDate = LocalDate.now().minusYears(25).minusDays(10);

            int age = DateTimeUtils.calculateAge(birthDate);

            assertThat(age).isEqualTo(25);
        }
    }
}