package ch.batbern.partners.controller;

import ch.batbern.shared.service.CapturedEmail;
import ch.batbern.shared.service.LocalEmailCapture;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Development-only REST controller for inspecting captured emails from the partner service.
 * Active only on the 'local' Spring profile — never registered in staging/production.
 *
 * Endpoints:
 *   GET    /dev/emails                              — list all captured emails (JSON, newest first)
 *   GET    /dev/emails/{id}                         — get single email metadata (JSON)
 *   GET    /dev/emails/{id}/preview                 — render full HTML body in browser
 *   GET    /dev/emails/{id}/attachments/{filename}  — download raw attachment bytes
 *   DELETE /dev/emails                              — clear all captured emails
 *
 * Partner service direct URL: http://localhost:8004/dev/emails
 */
@RestController
@RequestMapping("/dev/emails")
@Profile("local")
@RequiredArgsConstructor
@CrossOrigin
public class DevEmailController {

    private final LocalEmailCapture localEmailCapture;

    @GetMapping
    public List<CapturedEmail> listEmails() {
        return localEmailCapture.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CapturedEmail> getEmail(@PathVariable UUID id) {
        return localEmailCapture.getAll().stream()
            .filter(e -> e.id().equals(id))
            .findFirst()
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{id}/preview", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> previewEmail(@PathVariable UUID id) {
        return localEmailCapture.getAll().stream()
            .filter(e -> e.id().equals(id))
            .findFirst()
            .map(e -> ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(e.htmlBody()))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/attachments/{filename}")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable UUID id,
            @PathVariable String filename) {
        return localEmailCapture.getAll().stream()
            .filter(e -> e.id().equals(id))
            .findFirst()
            .flatMap(email -> {
                String mimeType = email.attachments().stream()
                    .filter(a -> a.filename().equals(filename))
                    .findFirst()
                    .map(CapturedEmail.AttachmentInfo::mimeType)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
                return localEmailCapture.getAttachmentBytes(id, filename)
                    .map(bytes -> {
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.parseMediaType(mimeType));
                        headers.setContentDisposition(
                            ContentDisposition.attachment().filename(filename).build());
                        return ResponseEntity.ok().headers(headers).body(bytes);
                    });
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping
    public ResponseEntity<Void> clearInbox() {
        localEmailCapture.clear();
        return ResponseEntity.noContent().build();
    }
}
