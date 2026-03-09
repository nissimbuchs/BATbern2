package ch.batbern.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory email capture for local development.
 * Active only on the 'local' Spring profile.
 *
 * Stores up to MAX_EMAILS most-recent captures in a ring buffer.
 * Attachment bytes are stored separately and served via DevEmailController.
 * Thread-safe for concurrent @Async email calls.
 *
 * Browse captured emails at http://localhost:{SERVICE_PORT}/dev/emails
 */
@Component
@Profile("local")
@Slf4j
public class LocalEmailCapture {

    private static final int MAX_EMAILS = 200;

    private final LinkedList<CapturedEmail> inbox = new LinkedList<>();
    /** emailId → filename → raw bytes (dev-only, not sent to frontend in JSON list) */
    private final Map<UUID, Map<String, byte[]>> attachmentBytes = new HashMap<>();

    /**
     * Capture an email. Returns the generated UUID for the captured email so
     * callers can subsequently store attachment bytes via {@link #storeAttachmentBytes}.
     */
    public synchronized UUID capture(
            String to,
            String subject,
            String htmlBody,
            String fromEmail,
            String fromName,
            List<CapturedEmail.AttachmentInfo> attachments) {
        UUID id = UUID.randomUUID();
        CapturedEmail email = new CapturedEmail(
            id,
            to,
            subject,
            htmlBody,
            fromEmail,
            fromName,
            Instant.now(),
            attachments == null ? List.of() : attachments
        );
        inbox.addFirst(email);
        while (inbox.size() > MAX_EMAILS) {
            CapturedEmail removed = inbox.removeLast();
            attachmentBytes.remove(removed.id());
        }
        log.info("📧 [LOCAL] Email captured to={} subject='{}' attachments={}",
                to, subject, email.attachments().size());
        return id;
    }

    /** Store raw bytes for a single attachment belonging to a previously captured email. */
    public synchronized void storeAttachmentBytes(UUID emailId, String filename, byte[] bytes) {
        attachmentBytes.computeIfAbsent(emailId, k -> new HashMap<>()).put(filename, bytes);
    }

    /** Retrieve raw bytes for a specific attachment (for the download endpoint). */
    public synchronized Optional<byte[]> getAttachmentBytes(UUID emailId, String filename) {
        Map<String, byte[]> files = attachmentBytes.get(emailId);
        return files != null ? Optional.ofNullable(files.get(filename)) : Optional.empty();
    }

    public synchronized List<CapturedEmail> getAll() {
        return Collections.unmodifiableList(new LinkedList<>(inbox));
    }

    public synchronized void clear() {
        inbox.clear();
        attachmentBytes.clear();
        log.info("📧 [LOCAL] Email inbox cleared");
    }
}
