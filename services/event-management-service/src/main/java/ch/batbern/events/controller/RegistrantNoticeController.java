package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.RegistrantNoticePreviewRequest;
import ch.batbern.events.dto.RegistrantNoticePreviewResponse;
import ch.batbern.events.dto.RegistrantNoticeSendRequest;
import ch.batbern.events.dto.SlidesOnlineSendResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.SlidesOnlineEmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/**
 * Story 7.3 hardening — ORGANIZER-only endpoints to preview and send a registrant-notice mail
 * (any {@code REGISTRANT_NOTICE}-category template, e.g. {@code slides-online}) to an event's
 * <b>active registrants</b>.
 *
 * <p>This is the safe, dedicated send path that the "Registrant Notices" organizer tab uses.
 * It is intentionally separate from {@code NewsletterController} (subscriber blast); the
 * subscriber newsletter now hard-rejects {@code REGISTRANT_NOTICE} templates, and those
 * templates no longer appear in the subscriber-newsletter picker.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class RegistrantNoticeController {

    private final SlidesOnlineEmailService registrantNoticeEmailService;
    private final EventRepository eventRepository;
    private final SecurityContextHelper securityContextHelper;

    /**
     * Preview a registrant-notice template in a chosen language and report the recipient count.
     */
    @PostMapping("/events/{eventCode}/registrant-notices/preview")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<RegistrantNoticePreviewResponse> preview(
            @PathVariable String eventCode,
            @Valid @RequestBody RegistrantNoticePreviewRequest request) {
        Event event = findEventOrThrow(eventCode);
        RegistrantNoticePreviewResponse preview = registrantNoticeEmailService
                .previewRegistrantNotice(event, request.getTemplateKey(), request.getLocale());
        return ResponseEntity.ok(preview);
    }

    /**
     * Send a registrant-notice mail to the event's active registrants (ORGANIZER only).
     *
     * @return 200 with the send id + PENDING status (the send runs asynchronously)
     */
    @PostMapping("/events/{eventCode}/registrant-notices/send")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SlidesOnlineSendResponse> send(
            @PathVariable String eventCode,
            @Valid @RequestBody RegistrantNoticeSendRequest request) {
        Event event = findEventOrThrow(eventCode);
        String sentByUsername = securityContextHelper.getCurrentUsername();
        SlidesOnlineSendResponse response = registrantNoticeEmailService
                .sendRegistrantNotice(event, request.getTemplateKey(), sentByUsername);
        return ResponseEntity.ok(response);
    }

    private Event findEventOrThrow(String eventCode) {
        return eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + eventCode));
    }
}
