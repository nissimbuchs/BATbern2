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
import java.util.Properties;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    public InboundEmailListenerService(
            S3Client s3Client,
            InboundEmailRouter router,
            @Value("${AWS_INBOUND_EMAIL_BUCKET_NAME:}") String bucketName) {
        this.s3Client = s3Client;
        this.router = router;
        this.bucketName = bucketName;
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
                String subject = message.getSubject();
                String bodyFirstLine = extractFirstPlainTextLine(message);

                if (from == null || from.isBlank()) {
                    log.warn("Inbound email: could not extract From address — discarding");
                    return;
                }

                router.route(new InboundEmailRouter.ParsedEmail(from, subject != null ? subject : "", bodyFirstLine));
            }

        } catch (Exception e) {
            // Catch-all: log and discard — do NOT rethrow to prevent infinite SQS redelivery
            log.error("Failed to process inbound email message: {}", e.getMessage(), e);
        }
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
