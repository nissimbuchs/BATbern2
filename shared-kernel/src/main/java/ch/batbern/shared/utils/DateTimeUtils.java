package ch.batbern.shared.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateTimeUtils {

    private static final ZoneId SWISS_ZONE = ZoneId.of("Europe/Zurich");
    private static final DateTimeFormatter ISO_8601_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final DateTimeFormatter SWISS_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter SWISS_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter RFC3339_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private DateTimeUtils() {
        // Utility class, prevent instantiation
    }

    // Timestamp Generation
    public static Instant now() {
        return Instant.now();
    }

    public static ZonedDateTime nowUTC() {
        return ZonedDateTime.now(ZoneId.of("UTC"));
    }

    public static ZonedDateTime nowSwiss() {
        return ZonedDateTime.now(SWISS_ZONE);
    }

    public static Instant nowWithMillisPrecision() {
        Instant now = Instant.now();
        long millis = now.toEpochMilli();
        return Instant.ofEpochMilli(millis);
    }

    // Date Formatting
    public static String formatISO8601(Instant instant) {
        return ISO_8601_FORMATTER.format(instant);
    }

    public static String formatSwissDate(LocalDate date) {
        return SWISS_DATE_FORMATTER.format(date);
    }

    public static String formatSwissDateTime(LocalDateTime dateTime) {
        return SWISS_DATETIME_FORMATTER.format(dateTime);
    }

    public static String formatForEventBridge(Instant instant) {
        return instant.toString(); // EventBridge accepts ISO-8601 format
    }

    public static String formatRFC3339(ZonedDateTime dateTime) {
        return RFC3339_FORMATTER.format(dateTime);
    }

    // Date Parsing
    public static Instant parseISO8601(String dateString) {
        return Instant.parse(dateString);
    }

    public static LocalDate parseSwissDate(String dateString) {
        return LocalDate.parse(dateString, SWISS_DATE_FORMATTER);
    }

    public static TemporalAccessor parseFlexible(String dateString) {
        // Try multiple formats
        try {
            return Instant.parse(dateString);
        } catch (DateTimeParseException e1) {
            try {
                return LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException e2) {
                try {
                    return LocalDate.parse(dateString, SWISS_DATE_FORMATTER);
                } catch (DateTimeParseException e3) {
                    try {
                        return LocalDate.parse(dateString, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    } catch (DateTimeParseException e4) {
                        return LocalDate.parse(dateString, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                    }
                }
            }
        }
    }

    // Date Calculations
    public static String getQuarter(LocalDate date) {
        int quarter = (date.getMonthValue() - 1) / 3 + 1;
        return String.format("Q%d %d", quarter, date.getYear());
    }

    public static long daysBetween(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end);
    }

    public static long businessDaysBetween(LocalDate start, LocalDate end) {
        long days = 0;
        LocalDate current = start;
        while (current.isBefore(end)) {
            if (isWorkday(current)) {
                days++;
            }
            current = current.plusDays(1);
        }
        return days;
    }

    public static LocalDate addBusinessDays(LocalDate date, int days) {
        LocalDate result = date;
        int addedDays = 0;
        while (addedDays < days) {
            result = result.plusDays(1);
            if (isWorkday(result)) {
                addedDays++;
            }
        }
        return result;
    }

    public static boolean isWorkday(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    // Time Zone Conversions
    public static ZonedDateTime toSwissTime(Instant instant) {
        return instant.atZone(SWISS_ZONE);
    }

    public static Instant toUTC(ZonedDateTime dateTime) {
        return dateTime.toInstant();
    }

    // Date Validation
    public static boolean isFuture(LocalDate date) {
        return date.isAfter(LocalDate.now());
    }

    public static boolean isPast(Instant instant) {
        return instant.isBefore(Instant.now());
    }

    public static boolean isInRange(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    public static boolean isSameQuarter(LocalDate date1, LocalDate date2) {
        return getQuarter(date1).equals(getQuarter(date2));
    }

    // Duration and Period Handling
    public static String formatDuration(long milliseconds) {
        long hours = milliseconds / 3600000;
        long minutes = (milliseconds % 3600000) / 60000;
        long seconds = (milliseconds % 60000) / 1000;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0) {
            sb.append(seconds).append("s");
        }
        return sb.toString().trim();
    }

    public static long parseHumanDuration(String duration) {
        Pattern pattern = Pattern.compile("(\\d+)\\s*(minute|minutes|hour|hours|day|days|week|weeks)");
        Matcher matcher = pattern.matcher(duration.toLowerCase());

        if (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);

            switch (unit) {
                case "minute":
                case "minutes":
                    return value * 60 * 1000L;
                case "hour":
                case "hours":
                    return value * 60 * 60 * 1000L;
                case "day":
                case "days":
                    return value * 24 * 60 * 60 * 1000L;
                case "week":
                case "weeks":
                    return value * 7 * 24 * 60 * 60 * 1000L;
                default:
                    throw new IllegalArgumentException("Unknown time unit: " + unit);
            }
        }
        throw new IllegalArgumentException("Invalid duration format: " + duration);
    }

    public static int calculateAge(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}