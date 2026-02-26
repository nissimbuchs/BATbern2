package ch.batbern.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * In-memory email capture for local development.
 * Active only on the 'local' Spring profile.
 *
 * Stores up to MAX_EMAILS most-recent captures in a ring buffer.
 * Thread-safe for concurrent @Async email calls.
 *
 * Browse captured emails at http://localhost:{EMS_PORT}/dev/emails
 */
@Component
@Profile("local")
@Slf4j
public class LocalEmailCapture {

    private static final int MAX_EMAILS = 200;

    private final LinkedList<CapturedEmail> inbox = new LinkedList<>();

    public synchronized void capture(
            String to,
            String subject,
            String htmlBody,
            String fromEmail,
            String fromName,
            List<CapturedEmail.AttachmentInfo> attachments) {
        CapturedEmail email = new CapturedEmail(
            UUID.randomUUID(),
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
            inbox.removeLast();
        }
        log.info("📧 [LOCAL] Email captured to={} subject='{}' attachments={}",
                to, subject, email.attachments().size());
    }

    public synchronized List<CapturedEmail> getAll() {
        return Collections.unmodifiableList(new LinkedList<>(inbox));
    }

    public synchronized void clear() {
        inbox.clear();
        log.info("📧 [LOCAL] Email inbox cleared");
    }
}
