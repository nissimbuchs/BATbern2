package ch.batbern.shared.mail;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes credentials from a mail before it is recorded (Story 16.1a). Recorded mail is kept for
 * twelve months and shown to every organizer, so it must not carry what lets someone act as the
 * recipient: one-click links ({@code ?token=}: confirmation, deregistration, unsubscribe), the
 * registration code, a temporary password.
 */
public final class MailRedaction {

    public static final String MASK = "[redacted]";

    /** Shorter secrets are ignored: masking "a" would shred the text, and no credential is that short. */
    private static final int MIN_SECRET_LENGTH = 4;

    /** {@code token=} as a query parameter, also after an HTML-escaped {@code &amp;}. */
    private static final Pattern TOKEN_PARAMETER =
            Pattern.compile("(?i)([?&;]token=)[^&\\s\"'<>]+");

    private MailRedaction() {
    }

    public static String redact(String text, List<String> secrets) {
        if (text == null) {
            return null;
        }
        String result = TOKEN_PARAMETER.matcher(text).replaceAll("$1" + Matcher.quoteReplacement(MASK));
        for (String secret : secrets) {
            if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
                continue;
            }
            result = result.replace(escapeHtml(secret), MASK).replace(secret, MASK);
        }
        return result;
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
