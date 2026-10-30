package ch.batbern.events.forwarding;

import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMultipart;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fields of an iMIP {@code METHOD:REPLY} (RFC 5546) that Story 16.0 Part B (#1161) needs to
 * match a calendar "Declined" to a registration. Mail clients disagree on layout (multipart nesting,
 * parameter order, folded lines, UTC vs {@code TZID} start), so parsing is deliberately lenient.
 *
 * @param uid      the invite's UID, e.g. {@code reg-{uuid}@batbern.ch}
 * @param partStat PARTSTAT of the replying attendee (ACCEPTED, DECLINED, TENTATIVE, ...)
 * @param summary  SUMMARY, may be null
 * @param start    DTSTART, may be null when absent or unparseable
 */
public record CalendarReply(String uid, String partStat, String summary, Instant start) {

    private static final ZoneId SWISS = ZoneId.of("Europe/Zurich");
    private static final Pattern FOLD = Pattern.compile("\\r?\\n[ \\t]");
    private static final Pattern PARTSTAT = Pattern.compile("(?im)^ATTENDEE[^\\r\\n]*?;PARTSTAT=([A-Z-]+)");
    private static final Pattern DTSTART = Pattern.compile("(?im)^DTSTART((?:;[^:\\r\\n]*)?):([0-9TZ]+)\\s*$");
    private static final Pattern TZID = Pattern.compile("(?i);TZID=([^;:]+)");
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    public boolean declined() {
        return "DECLINED".equalsIgnoreCase(partStat);
    }

    /** The reply in a parsed mail, or empty when it has no readable {@code text/calendar} REPLY part. */
    public static Optional<CalendarReply> parse(Part message) {
        try {
            String ics = findCalendarText(message);
            if (ics == null) {
                return Optional.empty();
            }
            String unfolded = FOLD.matcher(ics).replaceAll("");
            if (!unfolded.toUpperCase(Locale.ROOT).contains("METHOD:REPLY")) {
                return Optional.empty();
            }
            String uid = property(unfolded, "UID");
            Matcher partStat = PARTSTAT.matcher(unfolded);
            if (uid == null || !partStat.find()) {
                return Optional.empty();
            }
            return Optional.of(new CalendarReply(uid, partStat.group(1).toUpperCase(Locale.ROOT),
                    property(unfolded, "SUMMARY"), start(unfolded)));
        } catch (MessagingException | IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String findCalendarText(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/calendar")) {
            try (InputStream in = part.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        if (part.isMimeType("multipart/*")) {
            MimeMultipart multipart = (MimeMultipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String text = findCalendarText(multipart.getBodyPart(i));
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String property(String ics, String name) {
        Matcher m = Pattern.compile("(?im)^" + name + "(?:;[^:\\r\\n]*)?:([^\\r\\n]*)").matcher(ics);
        if (!m.find()) {
            return null;
        }
        String value = m.group(1).trim();
        return value.isEmpty() ? null : value;
    }

    private static Instant start(String ics) {
        Matcher m = DTSTART.matcher(ics);
        if (!m.find()) {
            return null;
        }
        String params = m.group(1);
        String value = m.group(2).toUpperCase(Locale.ROOT);
        try {
            if (value.endsWith("Z")) {
                return LocalDateTime.parse(value.substring(0, value.length() - 1), LOCAL).toInstant(ZoneOffset.UTC);
            }
            Matcher tz = TZID.matcher(params);
            ZoneId zone = tz.find() ? zone(tz.group(1).replace("\"", "")) : SWISS;
            if (value.length() == 8) {
                return LocalDate.parse(value, DATE).atStartOfDay(zone).toInstant();
            }
            return LocalDateTime.parse(value, LOCAL).atZone(zone).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Clients send IANA ids, sometimes Windows names ("W. Europe Standard Time"); fall back to Swiss time. */
    private static ZoneId zone(String id) {
        try {
            return ZoneId.of(id.trim());
        } catch (RuntimeException e) {
            return SWISS;
        }
    }
}
