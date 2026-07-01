package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.newsletter.api.generated.RegistrantNoticeApi;
import ch.batbern.events.newsletter.dto.generated.RegistrantNoticePreviewRequest;
import ch.batbern.events.newsletter.dto.generated.RegistrantNoticePreviewResponse;
import ch.batbern.events.newsletter.dto.generated.RegistrantNoticeSendRequest;
import ch.batbern.events.newsletter.dto.generated.SlidesOnlineSendResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.SlidesOnlineEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
public class RegistrantNoticeController implements RegistrantNoticeApi {

    private final SlidesOnlineEmailService registrantNoticeEmailService;
    private final EventRepository eventRepository;
    private final SecurityContextHelper securityContextHelper;

    /**
     * Preview a registrant-notice template in a chosen language and report the recipient count.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<RegistrantNoticePreviewResponse> previewRegistrantNotice(
            String eventCode,
            RegistrantNoticePreviewRequest request) {
        Event event = findEventOrThrow(eventCode);
        String locale = request.getLocale() != null ? request.getLocale().getValue() : null;
        RegistrantNoticePreviewResponse preview = registrantNoticeEmailService
                .previewRegistrantNotice(event, request.getTemplateKey(), locale);
        return ResponseEntity.ok(preview);
    }

    /**
     * Send a registrant-notice mail to the event's active registrants (ORGANIZER only).
     *
     * @return 200 with the send id + PENDING status (the send runs asynchronously)
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SlidesOnlineSendResponse> sendRegistrantNotice(
            String eventCode,
            RegistrantNoticeSendRequest request) {
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
