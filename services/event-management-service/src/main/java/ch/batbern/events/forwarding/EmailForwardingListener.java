package ch.batbern.events.forwarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Consumes the S3 {@code forwarding/} notifications that SES produces for every mail to a
 * {@code @batbern.ch} alias, and hands each raw mail to {@link EmailForwardingService}.
 *
 * <p>Unlike the replies listener, failures are rethrown: the service has released its claim, so
 * SQS redelivers (and moves the message to the DLQ after three attempts).
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "aws.inbound-email.forwarding.enabled", havingValue = "true")
public class EmailForwardingListener {

    private static final String PREFIX = "forwarding/";

    private final S3Client s3Client;
    private final EmailForwardingService forwardingService;
    private final ObjectMapper objectMapper;
    private final String bucketName;

    public EmailForwardingListener(S3Client s3Client,
                                   EmailForwardingService forwardingService,
                                   ObjectMapper objectMapper,
                                   @Value("${aws.inbound-email.bucket-name:}") String bucketName) {
        this.s3Client = s3Client;
        this.forwardingService = forwardingService;
        this.objectMapper = objectMapper;
        this.bucketName = bucketName;
    }

    @SqsListener("${aws.inbound-email.forwarding.queue-url}")
    public void handleS3Notification(String messageBody) throws IOException {
        JsonNode records = objectMapper.readTree(messageBody).path("Records");
        if (!records.isArray() || records.isEmpty()) {
            // s3:TestEvent on subscription, or anything else without records
            log.info("Forwarding queue: message without Records, ignoring");
            return;
        }
        for (JsonNode record : records) {
            String bucket = record.path("s3").path("bucket").path("name").asText("");
            String key = decodeKey(record.path("s3").path("object").path("key").asText(""));
            if (!bucket.equals(bucketName) || !key.startsWith(PREFIX)) {
                log.warn("Forwarding queue: ignoring object outside {}/{}", bucketName, PREFIX);
                continue;
            }
            byte[] raw = s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .asByteArray();
            forwardingService.forward(key, raw);
        }
    }

    /** S3 event keys are URL-encoded with '+' for spaces. */
    static String decodeKey(String key) {
        return URLDecoder.decode(key, StandardCharsets.UTF_8);
    }
}
