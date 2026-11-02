package ch.batbern.events.mailcenter.inbound;

import ch.batbern.events.forwarding.MailClassifier;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides whether a person wrote an inbound mail (Story 16.2a AC7). Only {@link Kind#HUMAN} mail
 * can open a conversation; everything else is counted. Used on both inbound paths, so it checks
 * bounces too, which the alias forwarder never did.
 */
public final class InboundClassifier {

    /** What an inbound mail is. */
    public enum Kind { HUMAN, OUT_OF_OFFICE, BOUNCE, CALENDAR_REPLY, LOOP }

    /**
     * Subjects of automatic replies, DE + EN + FR + IT, matched at the start, case-insensitive.
     * A word inside the subject ("Re: my absence on Friday") is a person writing.
     */
    private static final Pattern AUTO_REPLY_SUBJECT = Pattern.compile(
            "^\\s*(out of office|automatic reply|auto-reply|autoreply|abwesenheitsnotiz|automatische antwort"
                    + "|abwesend|réponse automatique|absence|risposta automatica)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private InboundClassifier() {
    }

    public static Kind classify(InboundMail mail, byte[] raw, String ownDomain) {
        if (MailClassifier.isCalendarReply(new String(raw, StandardCharsets.ISO_8859_1))) {
            return Kind.CALENDAR_REPLY;
        }
        String from = mail.fromAddress();
        if (mail.forwarded() || (from != null && ownDomain != null
                && from.endsWith("@" + ownDomain.toLowerCase(Locale.ROOT)))) {
            return Kind.LOOP;
        }
        if (MailClassifier.isBounce(from, mail.contentType(), mail.returnPath())) {
            return Kind.BOUNCE;
        }
        if (MailClassifier.isAutoReply(mail.autoSubmitted(), mail.precedence(), mail.xAutoreply(),
                mail.xAutorespond()) || AUTO_REPLY_SUBJECT.matcher(mail.subject()).find()) {
            return Kind.OUT_OF_OFFICE;
        }
        return Kind.HUMAN;
    }
}
