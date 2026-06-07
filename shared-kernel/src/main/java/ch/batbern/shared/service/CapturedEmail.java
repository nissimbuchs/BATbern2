package ch.batbern.shared.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Represents an email intercepted by LocalEmailCapture for local development inspection.
 * Browse captured emails at http://localhost:{EMS_PORT}/dev/emails
 *
 * <p>Story 10.32 — {@code cc} is the list of additional recipient addresses copied
 * into the captured email's CC line (mirrors what SES {@code Destination.ccAddresses}
 * would have done in staging/prod). Empty for emails sent via the no-CC overloads.
 */
public record CapturedEmail(
    UUID id,
    String to,
    List<String> cc,
    String subject,
    String htmlBody,
    String fromEmail,
    String fromName,
    Instant capturedAt,
    List<AttachmentInfo> attachments
) {
    /**
     * Compact constructor — defensively coerce a {@code null} cc list to an empty
     * one so consumers (DevEmailController JSON, frontend UI) can always iterate
     * without a null check.
     */
    public CapturedEmail {
        cc = (cc == null) ? List.of() : cc;
    }

    public record AttachmentInfo(String filename, String mimeType, int sizeBytes) {}
}
