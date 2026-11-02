package ch.batbern.events.service;

import ch.batbern.events.forwarding.EmailForwardingService;
import ch.batbern.events.forwarding.MailClassifier;
import ch.batbern.events.mailcenter.ConversationNotices;
import ch.batbern.events.mailcenter.MailCenterSwitch;
import ch.batbern.events.mailcenter.inbound.InboundMailCenter;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimePart;
import jakarta.mail.internet.MimeMultipart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes one mail to {@code replies@} (Story 10.17, extracted from {@link InboundEmailListenerService}
 * in Story 16.2a so the local dev simulator runs the same code as production).
 *
 * <ol>
 *   <li>Story 16.7: SES spam or virus verdict FAIL → nothing below runs; dropped and recorded</li>
 *   <li>MIME parsed via jakarta.mail: From, Subject, first non-quoted plain-text line</li>
 *   <li>An iCal REPLY goes to {@link InboundEmailRouter#routeIcsReply}; nothing else runs</li>
 *   <li>A reply to an organizer's answer ({@code replies+{token}@} of a known conversation) skips the
 *       router (Story 16.6 AC4)</li>
 *   <li>Otherwise routed to {@link InboundEmailRouter}</li>
 *   <li>Mail the router cannot act on → {@link EmailForwardingService#forwardUnrecognizedReply}
 *       (Story 16.0, #1160)</li>
 *   <li>Then the Mail Center records or counts it (Story 16.2a). That step never throws.</li>
 *   <li>Story 16.6: with group {@code replies} switched on, the Mail Center stores the mail first and
 *       the organizers get a notification instead of the copy.</li>
 * </ol>
 *
 * <p>Not conditional: it has no AWS dependency of its own.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InboundReplyProcessor {

    private final InboundEmailRouter router;
    private final EmailForwardingService forwardingService;
    private final InboundMailCenter mailCenter;
    private final MailCenterSwitch mailSwitch;
    private final ConversationNotices notices;

    /**
     * @param s3Key where SES stored the mail (or a synthetic key on local dev)
     * @param raw   the raw MIME
     */
    public void process(String s3Key, byte[] raw) {
        if (MailClassifier.sesVerdict(raw) != MailClassifier.SesVerdict.CLEAN) {
            // Story 16.7 AC3–AC5: no keyword action, no calendar RSVP, not forwarded (both record the
            // drop); the Mail Center keeps spam in its Spam list and only counts a virus.
            forwardingService.forwardUnrecognizedReply(s3Key, raw);
            mailCenter.ingestReply(s3Key, raw, null);
            return;
        }
        InboundEmailRouter.RouteResult result;
        try {
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));

            String from = extractFrom(message);

            if (from == null || from.isBlank()) {
                log.warn("Inbound email: could not extract From address — discarding");
                return;
            }

            // Story 10.27: detect iCal REPLY before falling through to plain-text routing.
            // findCalendarReplyText returns the raw iCal body if a text/calendar; method=REPLY part exists.
            // If a calendar part exists (even if unparseable), skip plain-text routing entirely.
            String calendarText = findCalendarReplyText(message);
            if (calendarText != null) {
                InboundEmailRouter.IcsReply icsReply = parseIcsReply(calendarText);
                if (icsReply != null) {
                    router.routeIcsReply(icsReply);
                }
                mailCenter.ingestReply(s3Key, raw, null);
                return; // Always skip plain-text routing when a calendar part is present
            }

            if (mailCenter.isConversationReply(raw, "replies")) {
                // Story 16.6 AC4: a reply to an organizer's answer never triggers a keyword action
                result = InboundEmailRouter.RouteResult.UNRECOGNIZED;
            } else {
                String subject = message.getSubject();
                String bodyFirstLine = extractFirstPlainTextLine(message);
                result = router.route(
                        new InboundEmailRouter.ParsedEmail(from, subject != null ? subject : "", bodyFirstLine));
                if (result != InboundEmailRouter.RouteResult.UNRECOGNIZED) {
                    if (result.acted()) {
                        mailCenter.ingestReply(s3Key, raw, result);
                    }
                    return;
                }
            }

        } catch (Exception e) {
            // Catch-all: log and discard — do NOT rethrow to prevent infinite SQS redelivery
            log.error("Failed to process inbound email message: {}", e.getMessage(), e);
            return;
        }

        // Story 16.0 AC3: outside the catch-all on purpose. The router ran no action, so a
        // redelivery is harmless, and a failure before the first send (e.g. CUMS down) must reach
        // SQS so the reply is retried and lands in the DLQ instead of being lost.
        if (mailSwitch.isOn(MailCenterSwitch.Group.REPLIES)) {
            // Story 16.6 AC9: stored first (a failure is rethrown, SQS retries); the organizers get a
            // notification instead of the copy; mail that was not stored is forwarded as today
            Optional<InboundMailCenter.Stored> stored = mailCenter.ingestReplyStrict(s3Key, raw, result);
            if (stored.isPresent()) {
                forwardingService.forwardUnrecognizedReply(s3Key, raw, notices.forStored(stored.get()));
            } else {
                forwardingService.forwardUnrecognizedReply(s3Key, raw);
            }
            return;
        }
        forwardingService.forwardUnrecognizedReply(s3Key, raw);
        mailCenter.ingestReply(s3Key, raw, result);
    }

    /**
     * Walks the MIME tree and returns the raw iCal text body if a {@code text/calendar}
     * part with {@code METHOD:REPLY} is found. Returns null if no such part exists.
     *
     * <p>Separating detection from parsing ensures that when a calendar part exists but
     * is unparseable, we still skip plain-text routing (Story 10.27 AC3).
     */
    private String findCalendarReplyText(MimePart part) {
        try {
            String contentType = part.getContentType().toLowerCase();

            if (contentType.startsWith("text/calendar")) {
                // Use getInputStream() rather than getContent() — text/calendar may not be
                // decoded as String by all JavaMail implementations.
                String icsText;
                try (InputStream is = part.getInputStream()) {
                    icsText = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                // Unfold RFC 5545 line continuations before checking METHOD
                String unfolded = icsText.replaceAll("\r\n[ \t]", "").replaceAll("\n[ \t]", "");
                // RFC 5545 §3.2: property names and values are case-insensitive
                if (unfolded.toUpperCase().contains("METHOD:REPLY")) {
                    return unfolded;
                }
                return null;
            }

            if (contentType.startsWith("multipart/")) {
                MimeMultipart multipart = (MimeMultipart) part.getContent();
                for (int i = 0; i < multipart.getCount(); i++) {
                    String text = findCalendarReplyText((MimePart) multipart.getBodyPart(i));
                    if (text != null) {
                        return text;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to walk MIME tree for calendar part: {} — treating as no calendar", e.getMessage());
        }
        return null;
    }

    /**
     * Parses an already-unfolded iCal text for the fields required to route an RSVP.
     * Returns null (with WARN log) if required fields (UID, ATTENDEE, PARTSTAT) are missing.
     *
     * Story 10.27 (AC3).
     */
    private InboundEmailRouter.IcsReply parseIcsReply(String unfolded) {
        // Extract UID
        String uid = extractIcsProperty(unfolded, "UID:");
        if (uid == null || uid.isBlank()) {
            log.warn("iCal REPLY: missing UID — discarding");
            return null;
        }

        // Extract ATTENDEE email (mailto: value)
        Pattern attendeeEmailPattern = Pattern.compile("ATTENDEE[^:]*:mailto:([^\\r\\n]+)", Pattern.CASE_INSENSITIVE);
        Matcher emailMatcher = attendeeEmailPattern.matcher(unfolded);
        if (!emailMatcher.find()) {
            log.warn("iCal REPLY: missing ATTENDEE — discarding");
            return null;
        }
        String attendeeEmail = emailMatcher.group(1).trim();

        // Extract PARTSTAT from anywhere in the ATTENDEE line (parameter order varies per RFC 5545)
        Pattern partstatPattern = Pattern.compile("PARTSTAT=([A-Z\\-]+)", Pattern.CASE_INSENSITIVE);
        Matcher partstatMatcher = partstatPattern.matcher(unfolded);
        if (!partstatMatcher.find()) {
            log.warn("iCal REPLY: missing PARTSTAT — discarding");
            return null;
        }
        String partStat = partstatMatcher.group(1).toUpperCase();

        return new InboundEmailRouter.IcsReply(uid, attendeeEmail, partStat);
    }

    /**
     * Extracts a property value from an unfolded iCal text.
     * E.g. {@code extractIcsProperty(text, "UID:")} returns the value after {@code UID:} up to line end.
     */
    private String extractIcsProperty(String icsText, String propertyName) {
        int idx = icsText.indexOf(propertyName);
        if (idx < 0) {
            return null;
        }
        int start = idx + propertyName.length();
        int end = icsText.indexOf('\n', start);
        String value = end >= 0 ? icsText.substring(start, end) : icsText.substring(start);
        return value.replace("\r", "").trim();
    }

    /**
     * Extracts the sender email address from the From: header.
     */
    private String extractFrom(MimeMessage message) throws MessagingException {
        String[] fromHeaders = message.getHeader("From");
        if (fromHeaders == null || fromHeaders.length == 0) {
            return null;
        }
        InternetAddress[] parsed = InternetAddress.parse(fromHeaders[0]);
        return parsed.length > 0 ? parsed[0].getAddress() : null;
    }

    /**
     * Recursively walks multipart MIME tree to find the first non-quoted
     * plain-text line from the body.
     */
    private String extractFirstPlainTextLine(MimePart part) throws MessagingException, IOException {
        String contentType = part.getContentType().toLowerCase();

        if (contentType.startsWith("text/plain")) {
            String content = (String) part.getContent();
            if (content == null) {
                return "";
            }
            return content.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith(">"))
                    .findFirst()
                    .orElse("");
        }

        if (contentType.startsWith("multipart/")) {
            MimeMultipart multipart = (MimeMultipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String line = extractFirstPlainTextLine((MimePart) multipart.getBodyPart(i));
                if (!line.isBlank()) {
                    return line;
                }
            }
        }

        return "";
    }
}
