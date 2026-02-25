package ch.batbern.shared.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Represents an email intercepted by LocalEmailCapture for local development inspection.
 * Browse captured emails at http://localhost:{EMS_PORT}/dev/emails
 */
public record CapturedEmail(
    UUID id,
    String to,
    String subject,
    String htmlBody,
    String fromEmail,
    String fromName,
    Instant capturedAt,
    List<AttachmentInfo> attachments
) {
    public record AttachmentInfo(String filename, String mimeType, int sizeBytes) {}
}
