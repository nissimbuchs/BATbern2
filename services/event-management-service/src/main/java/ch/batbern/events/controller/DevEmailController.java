package ch.batbern.events.controller;

import ch.batbern.events.service.InboundEmailRouter;
import ch.batbern.shared.service.CapturedEmail;
import ch.batbern.shared.service.LocalEmailCapture;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Development-only REST controller for inspecting captured emails and simulating inbound replies.
 * Active only on the 'local' Spring profile — never registered in staging/production.
 *
 * Endpoints:
 *   GET    /dev/emails               — list all captured emails (JSON, newest first)
 *   GET    /dev/emails/{id}          — get single email metadata (JSON)
 *   GET    /dev/emails/{id}/preview  — render full HTML body in browser
 *   POST   /dev/emails/{id}/reply    — simulate an inbound reply (routes via InboundEmailRouter)
 *   DELETE /dev/emails               — clear all captured emails
 *
 * Browse from the frontend at http://localhost:8100/dev/emails
 */
@RestController
@RequestMapping("/dev/emails")
@Profile("local")
@RequiredArgsConstructor
@CrossOrigin
public class DevEmailController {

    private final LocalEmailCapture localEmailCapture;
    private final InboundEmailRouter inboundEmailRouter;

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

    /**
     * Simulate an inbound email reply for local dev testing (Story 10.17 — AC11).
     * Constructs a ParsedEmail from the captured email and routes it through InboundEmailRouter.
     * The resulting confirmation email (if any) will appear in the inbox.
     */
    @PostMapping("/{id}/reply")
    public ResponseEntity<String> simulateReply(@PathVariable UUID id, @RequestBody ReplyRequest request) {
        return localEmailCapture.getAll().stream()
            .filter(e -> e.id().equals(id))
            .findFirst()
            .map(e -> {
                InboundEmailRouter.ParsedEmail parsed = new InboundEmailRouter.ParsedEmail(
                    e.to(), "Re: " + e.subject(), request.replyBody()
                );
                inboundEmailRouter.route(parsed);
                return ResponseEntity.ok("Reply routed: " + request.replyBody());
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping
    public ResponseEntity<Void> clearInbox() {
        localEmailCapture.clear();
        return ResponseEntity.noContent().build();
    }

    /** Request body for reply simulation. */
    record ReplyRequest(String replyBody) {}
}
