package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.newsletter.api.generated.SlidesOnlineApi;
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
 * Story 7.3 — dedicated, ORGANIZER-only endpoint to send the "The Slides Are Online" mail to an
 * event's active registrants.
 *
 * <p>Kept separate from {@code NewsletterController} so the slides-online send (registrant-targeted,
 * one-shot) is never confused with the subscriber-newsletter send. See {@link SlidesOnlineEmailService}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class SlidesOnlineController implements SlidesOnlineApi {

    private final SlidesOnlineEmailService slidesOnlineEmailService;
    private final EventRepository eventRepository;
    private final SecurityContextHelper securityContextHelper;

    /**
     * Send the slides-online mail for an event (ORGANIZER only).
     *
     * @return 200 with the send id + PENDING status (the send runs asynchronously)
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SlidesOnlineSendResponse> sendSlidesOnline(String eventCode) {
        Event event = findEventOrThrow(eventCode);
        String sentByUsername = securityContextHelper.getCurrentUsername();
        SlidesOnlineSendResponse response = slidesOnlineEmailService.sendSlidesOnline(event, sentByUsername);
        return ResponseEntity.ok(response);
    }

    private Event findEventOrThrow(String eventCode) {
        return eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + eventCode));
    }
}
