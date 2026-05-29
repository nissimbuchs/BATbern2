package ch.batbern.events.controller;

import ch.batbern.events.dto.venuecoordination.VenueCoordinationPreviewRequest;
import ch.batbern.events.dto.venuecoordination.VenueCoordinationPreviewResponse;
import ch.batbern.events.dto.venuecoordination.VenueCoordinationSendRequest;
import ch.batbern.events.dto.venuecoordination.VenueCoordinationSendResponse;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.VenueCoordinationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Event Detail → Venue tab: organizer-facing endpoints for previewing and sending
 * venue / catering coordination mails. Templates and contacts are defined elsewhere
 * (templates in DB, contacts in app_settings); this controller is just the wiring.
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/venue-coordination")
@RequiredArgsConstructor
public class VenueCoordinationController {

    private final VenueCoordinationService service;
    private final SecurityContextHelper securityContextHelper;

    @PostMapping("/preview")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<VenueCoordinationPreviewResponse> preview(
            @PathVariable String eventCode,
            @Valid @RequestBody VenueCoordinationPreviewRequest request) {
        return ResponseEntity.ok(service.preview(
                eventCode,
                request.getTemplateKey(),
                request.getRecipientRole(),
                request.getLocale(),
                request.getNotes()
        ));
    }

    @PostMapping("/send")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<VenueCoordinationSendResponse> send(
            @PathVariable String eventCode,
            @Valid @RequestBody VenueCoordinationSendRequest request) {
        String sentByUsername = securityContextHelper.getCurrentUsername();
        return ResponseEntity.ok(service.send(
                eventCode,
                request.getTemplateKey(),
                request.getRecipients(),
                request.getLocale(),
                request.getNotes(),
                sentByUsername
        ));
    }
}
