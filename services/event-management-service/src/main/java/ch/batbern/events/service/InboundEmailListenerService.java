package ch.batbern.events.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimePart;
import jakarta.mail.internet.MimeMultipart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQS listener for inbound email processing (Story 10.17 — AC1, AC3, AC4, AC5).
 *
 * <p>Data flow:
 * <ol>
 *   <li>S3 event notification JSON received from SQS</li>
 *   <li>Raw email bytes fetched from S3</li>
 *   <li>MIME parsed via jakarta.mail: From, Subject, first non-quoted plain-text line</li>
 *   <li>Routed to {@link InboundEmailRouter}</li>
 * </ol>
 *
 * <p>This bean is only loaded when {@code aws.inbound-email.enabled=true} (staging/production).
 * In local/test environments the property defaults to {@code false}, so no SQS connection
 * is attempted.
 *
 * <p><strong>Note:</strong> Do NOT use {@code @SqsListener("${....:#{null}}")} — Spring Cloud
 * AWS 3.x does not support null queue identifiers and will throw at startup.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "aws.inbound-email.enabled", havingValue = "true", matchIfMissing = false)
public class InboundEmailListenerService {

    private final S3Client s3Client;
    private final InboundEmailRouter router;
    private final String bucketName;
    private final ObjectMapper objectMapper;

    public InboundEmailListenerService(
            S3Client s3Client,
            InboundEmailRouter router,
            @Value("${aws.inbound-email.bucket-name:}") String bucketName,
            ObjectMapper objectMapper) {
        this.s3Client = s3Client;
        this.router = router;
        this.bucketName = bucketName;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles S3 event notification messages from SQS.
     *
     * @param messageBody raw SQS message body (S3 event notification JSON)
     */
    @SqsListener("${aws.inbound-email.queue-url}")
    public void handleS3Notification(String messageBody) {
        try {
            // Parse S3 event notification JSON
            JsonNode root = objectMapper.readTree(messageBody);
            JsonNode records = root.path("Records");
            if (records.isMissingNode() || !records.isArray() || records.isEmpty()) {
                log.warn("Inbound email: no Records in S3 notification — discarding");
                return;
            }

            JsonNode s3Node = records.get(0).path("s3");
            String key = s3Node.path("object").path("key").asText("");

            if (key.isBlank()) {
                log.warn("Inbound email: empty S3 object key — discarding");
                return;
            }

            // Fetch raw email from S3
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            try (InputStream rawEmailStream = s3Client.getObject(getRequest)) {
                // Parse MIME email
                Session session = Session.getInstance(new Properties());
                MimeMessage message = new MimeMessage(session, rawEmailStream);

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
                    return; // Always skip plain-text routing when a calendar part is present
                }

                String subject = message.getSubject();
                String bodyFirstLine = extractFirstPlainTextLine(message);
                router.route(new InboundEmailRouter.ParsedEmail(from, subject != null ? subject : "", bodyFirstLine));
            }

        } catch (Exception e) {
            // Catch-all: log and discard — do NOT rethrow to prevent infinite SQS redelivery
            log.error("Failed to process inbound email message: {}", e.getMessage(), e);
        }
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
                if (unfolded.contains("METHOD:REPLY")) {
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
