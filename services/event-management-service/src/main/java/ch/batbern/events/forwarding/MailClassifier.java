package ch.batbern.events.forwarding;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Header-level checks on a raw inbound mail that decide whether it is forwarded at all.
 */
final class MailClassifier {

    /** Marker header set on every mail we forward; seeing it inbound means a loop. */
    static final String FORWARDED_MARKER_HEADER = "X-BATbern-Forwarded";

    private static final Pattern FOLD = Pattern.compile("\\r?\\n[ \\t]+");
    private static final Pattern CONTENT_TYPE_LINE = Pattern.compile("^content-type\\s*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern CALENDAR = Pattern.compile("text/calendar", Pattern.CASE_INSENSITIVE);
    private static final Pattern METHOD_REPLY = Pattern.compile("method\\s*=\\s*\"?reply\"?", Pattern.CASE_INSENSITIVE);

    private MailClassifier() {
    }

    /**
     * iMIP acceptance/decline replies (RFC 5546): mail clients send {@code METHOD:REPLY} to the
     * ORGANIZER on every Accept/Decline click. When the organizer is an alias that would fan out
     * to every organizer, so they are dropped. Checks every Content-Type line, top level and
     * inside every MIME part, after unfolding (port of the Lambda's {@code isCalendarReply}).
     */
    static boolean isCalendarReply(String raw) {
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
    static boolean isAutoReply(String autoSubmitted, String precedence, String xAutoreply, String xAutorespond) {
        if (autoSubmitted != null && !autoSubmitted.trim().toLowerCase(Locale.ROOT).startsWith("no")) {
            return true;
        }
        if (precedence != null && precedence.trim().equalsIgnoreCase("auto_reply")) {
            return true;
        }
        return xAutoreply != null || xAutorespond != null;
    }
}
