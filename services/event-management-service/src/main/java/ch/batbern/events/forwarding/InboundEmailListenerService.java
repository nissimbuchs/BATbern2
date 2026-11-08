package ch.batbern.events.forwarding;

import ch.batbern.events.mailcenter.inbound.InboundReplyProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;

/**
 * SQS listener for inbound email processing (Story 10.17 — AC1, AC3, AC4, AC5).
 *
 * <p>Data flow:
 * <ol>
 *   <li>S3 event notification JSON received from SQS</li>
 *   <li>Raw email bytes fetched from S3</li>
 *   <li>Processed by {@link InboundReplyProcessor} (routing, forwarding, Mail Center)</li>
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
    private final InboundReplyProcessor processor;
    private final String bucketName;
    private final ObjectMapper objectMapper;

    public InboundEmailListenerService(
            S3Client s3Client,
            InboundReplyProcessor processor,
            @Value("${aws.inbound-email.bucket-name:}") String bucketName,
            ObjectMapper objectMapper) {
        this.s3Client = s3Client;
        this.processor = processor;
        this.bucketName = bucketName;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles S3 event notification messages from SQS: fetches the mail and hands it to
     * {@link InboundReplyProcessor}.
     *
     * @param messageBody raw SQS message body (S3 event notification JSON)
     */
    @SqsListener("${aws.inbound-email.queue-url}")
    public void handleS3Notification(String messageBody) {
        String key;
        byte[] raw;
        try {
            // Parse S3 event notification JSON
            JsonNode root = objectMapper.readTree(messageBody);
            JsonNode records = root.path("Records");
            if (records.isMissingNode() || !records.isArray() || records.isEmpty()) {
                log.warn("Inbound email: no Records in S3 notification — discarding");
                return;
            }

            JsonNode s3Node = records.get(0).path("s3");
            key = s3Node.path("object").path("key").asText("");

            if (key.isBlank()) {
                log.warn("Inbound email: empty S3 object key — discarding");
                return;
            }
            if (MailClassifier.isSesSetupNotification(key)) {
                log.info("Inbound email: SES receipt-rule setup notification {}, ignoring", key);
                return;
            }

            // Fetch raw email from S3
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            try (InputStream rawEmailStream = s3Client.getObject(getRequest)) {
                raw = rawEmailStream.readAllBytes();
            }
        } catch (Exception e) {
            // Catch-all: log and discard — do NOT rethrow to prevent infinite SQS redelivery
            log.error("Failed to process inbound email message: {}", e.getMessage(), e);
            return;
        }

        processor.process(key, raw);
    }
}
