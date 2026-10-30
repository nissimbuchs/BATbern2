package ch.batbern.events.forwarding;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * Rewrites the headers of an inbound mail so it can be re-sent from our own domain
 * (port of the Lambda's {@code email-rewriter.ts}).
 *
 * <ul>
 *   <li>From: {@code "{name} via BATbern" <noreply@{domain}>} (SES only sends from verified identities)</li>
 *   <li>Reply-To: the original sender, so replies go to the person and not to the alias
 *       (or an explicit override, e.g. the moderator for speaker broadcasts)</li>
 *   <li>Return-Path, Sender and DKIM-Signature removed (SES sets/signs its own)</li>
 *   <li>Optional To/Cc override for the visible speaker broadcast</li>
 *   <li>{@value MailClassifier#FORWARDED_MARKER_HEADER} added so a forwarded copy that comes back is dropped</li>
 * </ul>
 * Subject, Message-ID, body and attachments are passed through byte for byte.
 */
@Component
public class ForwardedMessageRewriter {

    private static final String VIA = " via BATbern";

    /**
     * @param raw         the inbound message as stored by SES
     * @param senderName  display name of the original sender (falls back to the address local part)
     * @param senderEmail the original sender, becomes Reply-To
     * @param sesSender   our verified From address
     * @param toOverride  visible To list, or null to keep the original To header
     * @param ccOverride  visible Cc list (empty removes Cc), or null to keep the original Cc
     */
    public byte[] rewrite(byte[] raw, String senderName, String senderEmail, String sesSender,
                          List<String> toOverride, List<String> ccOverride) {
        return rewrite(raw, senderName, senderEmail, sesSender, null, toOverride, ccOverride);
    }

    /**
     * @param replyToOverride Reply-To instead of the original sender, or null to keep the sender
     *                        (Story 16.0: {@code batbern{N}-speaker@} broadcasts answer the moderator)
     */
    public byte[] rewrite(byte[] raw, String senderName, String senderEmail, String sesSender,
                          String replyToOverride, List<String> toOverride, List<String> ccOverride) {
        try {
            MimeMessage message = new PreservingMimeMessage(raw);
            message.removeHeader("From");
            message.setFrom(new InternetAddress(sesSender, displayName(senderName, senderEmail) + VIA, "UTF-8"));
            String replyTo = replyToOverride != null ? replyToOverride : senderEmail;
            message.setReplyTo(new InternetAddress[] {new InternetAddress(replyTo)});
            message.removeHeader("Return-Path");
            message.removeHeader("Sender");
            message.removeHeader("DKIM-Signature");
            if (toOverride != null && !toOverride.isEmpty()) {
                message.setRecipients(Message.RecipientType.TO, addresses(toOverride));
            }
            if (ccOverride != null) {
                if (ccOverride.isEmpty()) {
                    message.removeHeader("Cc");
                } else {
                    message.setRecipients(Message.RecipientType.CC, addresses(ccOverride));
                }
            }
            message.setHeader(MailClassifier.FORWARDED_MARKER_HEADER, "1");

            ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length + 512);
            message.writeTo(out);
            return out.toByteArray();
        } catch (MessagingException | IOException e) {
            throw new IllegalStateException("Could not rewrite inbound mail for forwarding", e);
        }
    }

    private static String displayName(String senderName, String senderEmail) {
        if (senderName != null && !senderName.isBlank()) {
            return senderName.trim();
        }
        return senderEmail.contains("@") ? senderEmail.substring(0, senderEmail.indexOf('@')) : senderEmail;
    }

    private static InternetAddress[] addresses(List<String> emails) throws MessagingException {
        InternetAddress[] result = new InternetAddress[emails.size()];
        for (int i = 0; i < emails.size(); i++) {
            result[i] = new InternetAddress(emails.get(i));
        }
        return result;
    }

    /**
     * A parsed message that keeps its original Message-ID and content bytes: a message read
     * from a stream counts as saved, so {@code writeTo} does not regenerate either, and
     * {@code updateMessageID} is a no-op in case anything calls {@code saveChanges()}.
     */
    private static final class PreservingMimeMessage extends MimeMessage {

        PreservingMimeMessage(byte[] raw) throws MessagingException {
            super(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
        }

        @Override
        protected void updateMessageID() {
            // keep the sender's Message-ID: threading in the recipients' clients depends on it
        }
    }
}
