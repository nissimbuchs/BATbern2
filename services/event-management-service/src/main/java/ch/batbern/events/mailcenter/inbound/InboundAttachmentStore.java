package ch.batbern.events.mailcenter.inbound;

import ch.batbern.events.domain.MailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps inbound attachments (Story 16.6 PR 2 prerequisite, owner decisions 2026-11-01 and
 * 2026-11-02): each file up to {@link #DEFAULT_MAX_BYTES} goes to the private inbound bucket under
 * {@code mail-attachments/{groupId}/{index}}; a larger one keeps only its name. The raw mail itself
 * still expires after 7 days. The retention job deletes the files with their conversation; a bucket
 * lifecycle rule on the prefix is the backstop.
 *
 * <p>Without a configured bucket (local dev, tests) only names are kept.
 */
@Component
@Slf4j
public class InboundAttachmentStore {

    public static final String PREFIX = "mail-attachments/";
    static final long DEFAULT_MAX_BYTES = 25L * 1024 * 1024;

    private final S3Client s3;
    private final String bucket;
    private final long maxBytes;

    @Autowired
    public InboundAttachmentStore(S3Client s3, @Value("${aws.inbound-email.bucket-name:}") String bucket) {
        this(s3, bucket, DEFAULT_MAX_BYTES);
    }

    InboundAttachmentStore(S3Client s3, String bucket, long maxBytes) {
        this.s3 = s3;
        this.bucket = bucket;
        this.maxBytes = maxBytes;
    }

    /** Whether files are kept at all (a bucket is configured). */
    public boolean enabled() {
        return bucket != null && !bucket.isBlank();
    }

    public String bucket() {
        return bucket;
    }

    /**
     * Uploads each attachment within the cap.
     *
     * @param contents the bytes, in the order of {@code attachments}
     * @return the attachments, with {@code storageKey} for every file kept
     * @throws RuntimeException when an upload fails
     */
    public List<MailMessage.Attachment> store(String groupId, List<MailMessage.Attachment> attachments,
            List<byte[]> contents) {
        if (!enabled() || attachments.isEmpty()) {
            return attachments;
        }
        List<MailMessage.Attachment> result = new ArrayList<>(attachments.size());
        for (int i = 0; i < attachments.size(); i++) {
            MailMessage.Attachment attachment = attachments.get(i);
            byte[] content = i < contents.size() ? contents.get(i) : null;
            if (content == null || content.length > maxBytes) {
                result.add(attachment);
                continue;
            }
            String key = PREFIX + groupId + "/" + i;
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(attachment.contentType())
                            .build(),
                    RequestBody.fromBytes(content));
            result.add(attachment.withStorageKey(key));
        }
        return result;
    }

    /** Best effort: deletes the given keys; a failure is logged, the lifecycle rule catches it. */
    public void delete(List<String> keys) {
        if (!enabled() || keys.isEmpty()) {
            return;
        }
        for (int from = 0; from < keys.size(); from += 1000) {
            List<ObjectIdentifier> batch = keys.subList(from, Math.min(keys.size(), from + 1000)).stream()
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();
            try {
                s3.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(batch).quiet(true).build())
                        .build());
            } catch (RuntimeException e) {
                log.warn("Mail attachments: {} file(s) not deleted, lifecycle rule will: {}", batch.size(),
                        e.toString());
            }
        }
    }
}
