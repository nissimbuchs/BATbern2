package ch.batbern.events.controller;

import ch.batbern.events.core.api.generated.VenueCoordinationApi;
import ch.batbern.events.core.dto.generated.VenueCoordinationPreviewRequest;
import ch.batbern.events.core.dto.generated.VenueCoordinationPreviewResponse;
import ch.batbern.events.core.dto.generated.VenueCoordinationSendRequest;
import ch.batbern.events.core.dto.generated.VenueCoordinationSendResponse;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.VenueCoordinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Event Detail → Venue tab: organizer-facing endpoints for previewing and sending
 * venue / catering coordination mails. Templates and contacts are defined elsewhere
 * (templates in DB, contacts in app_settings); this controller is just the wiring.
 *
 * <p>API-consolidation Phase 7: implements the generated {@link VenueCoordinationApi}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VenueCoordinationController implements VenueCoordinationApi {

    private final VenueCoordinationService service;
    private final SecurityContextHelper securityContextHelper;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<VenueCoordinationPreviewResponse> previewVenueCoordination(
            String eventCode,
            VenueCoordinationPreviewRequest request) {
        return ResponseEntity.ok(service.preview(
                eventCode,
                request.getTemplateKey(),
                request.getRecipients(),
                request.getLocale(),
                request.getNotes()
        ));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<VenueCoordinationSendResponse> sendVenueCoordination(
            String eventCode,
            VenueCoordinationSendRequest request) {
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
