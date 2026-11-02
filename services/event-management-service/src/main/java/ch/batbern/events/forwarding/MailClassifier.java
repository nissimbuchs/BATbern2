package ch.batbern.events.forwarding;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Header-level checks on a raw inbound mail that decide whether it is forwarded at all. Public since
 * Story 16.2a: the Mail Center classifies with the same rules ({@code InboundClassifier}).
 */
public final class MailClassifier {

    /** Marker header set on every mail we forward; seeing it inbound means a loop. */
    public static final String FORWARDED_MARKER_HEADER = "X-BATbern-Forwarded";

    private static final Pattern FOLD = Pattern.compile("\\r?\\n[ \\t]+");
    private static final Pattern CONTENT_TYPE_LINE = Pattern.compile("^content-type\\s*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern CALENDAR = Pattern.compile("text/calendar", Pattern.CASE_INSENSITIVE);
    private static final Pattern METHOD_REPLY = Pattern.compile("method\\s*=\\s*\"?reply\"?", Pattern.CASE_INSENSITIVE);

    /** Story 16.7: a verdict header SES adds when the receipt rule scans (RouteReplies, RouteForwarding*). */
    private static final Pattern SES_VERDICT = Pattern.compile(
            "^x-ses-(spam|virus)-verdict\\s*:\\s*fail\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern DELIVERY_REPORT = Pattern.compile("^\\s*multipart/report", Pattern.CASE_INSENSITIVE);

    private MailClassifier() {
    }

    /**
     * iMIP acceptance/decline replies (RFC 5546): mail clients send {@code METHOD:REPLY} to the
     * ORGANIZER on every Accept/Decline click. When the organizer is an alias that would fan out
     * to every organizer, so they are dropped. Checks every Content-Type line, top level and
     * inside every MIME part, after unfolding (port of the Lambda's {@code isCalendarReply}).
     */
    public static boolean isCalendarReply(String raw) {
        String unfolded = FOLD.matcher(raw).replaceAll(" ");
        for (String line : unfolded.split("\\r?\\n")) {
            if (CONTENT_TYPE_LINE.matcher(line).find()
                    && CALENDAR.matcher(line).find()
                    && METHOD_REPLY.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Out-of-office and other automatic replies (RFC 3834 {@code Auto-Submitted}, plus the
     * common non-standard markers). Forwarding them to a list only multiplies noise.
     * {@code Precedence: bulk/list} is deliberately NOT a signal: it also marks newsletters an
     * organizer may have subscribed {@code info@} to, which the Lambda forwarded too.
     */
    public static boolean isAutoReply(String autoSubmitted, String precedence, String xAutoreply, String xAutorespond) {
        if (autoSubmitted != null && !autoSubmitted.trim().toLowerCase(Locale.ROOT).startsWith("no")) {
            return true;
        }
        if (precedence != null && precedence.trim().equalsIgnoreCase("auto_reply")) {
            return true;
        }
        return xAutoreply != null || xAutorespond != null;
    }

    /**
     * Delivery status notifications (bounces): sent by {@code MAILER-DAEMON@}/{@code postmaster@},
     * as {@code multipart/report} (RFC 6522), or with the null reverse path {@code Return-Path: <>}
     * that RFC 5321 requires for them. Never a person, never worth forwarding.
     */
    public static boolean isBounce(String fromAddress, String contentType, String returnPath) {
        if (fromAddress != null) {
            String lower = fromAddress.trim().toLowerCase(Locale.ROOT);
            if (lower.startsWith("mailer-daemon@") || lower.startsWith("postmaster@")) {
                return true;
            }
        }
        if (contentType != null && DELIVERY_REPORT.matcher(contentType).find()) {
            return true;
        }
        return returnPath != null && returnPath.trim().equals("<>");
    }

    /** Story 16.7: what SES found when it scanned the mail. */
    public enum SesVerdict { CLEAN, SPAM, VIRUS }

    /**
     * Story 16.7 AC6: the SES scan verdicts in the header block. Only {@code FAIL} counts; {@code PASS},
     * {@code GRAY}, {@code PROCESSING_FAILED} and a missing header are clean. A virus wins over spam. Any
     * FAIL header counts, also a forged one: it can only ever hold the sender's own mail back.
     */
    public static SesVerdict sesVerdict(String raw) {
        boolean spam = false;
        String unfolded = FOLD.matcher(raw).replaceAll(" ");
        for (String line : unfolded.split("\\r?\\n")) {
            if (line.isEmpty()) {
                break; // end of the header block
            }
            Matcher m = SES_VERDICT.matcher(line);
            if (m.find()) {
                if (m.group(1).equalsIgnoreCase("virus")) {
                    return SesVerdict.VIRUS;
                }
                spam = true;
            }
        }
        return spam ? SesVerdict.SPAM : SesVerdict.CLEAN;
    }

    /** {@link #sesVerdict(String)} on the raw bytes (headers are ASCII). */
    public static SesVerdict sesVerdict(byte[] raw) {
        return sesVerdict(new String(raw, java.nio.charset.StandardCharsets.ISO_8859_1));
    }
}
