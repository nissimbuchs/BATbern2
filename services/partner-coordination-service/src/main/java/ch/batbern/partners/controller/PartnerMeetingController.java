package ch.batbern.partners.controller;

import ch.batbern.partners.dto.CreateMeetingRequest;
import ch.batbern.partners.dto.PartnerMeetingDTO;
import ch.batbern.partners.dto.SendInviteResponse;
import ch.batbern.partners.dto.UpdateMeetingRequest;
import ch.batbern.partners.service.PartnerMeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for partner meeting coordination — Story 8.3.
 *
 * All endpoints require ORGANIZER role (AC6).
 * POST /send-invite returns 202 Accepted (async email dispatch) — AC8.
 */
@RestController
@RequestMapping("/api/v1/partner-meetings")
@RequiredArgsConstructor
@Slf4j
public class PartnerMeetingController {

    private final PartnerMeetingService meetingService;

    /**
     * GET /api/v1/partner-meetings
     * List all partner meetings sorted by date descending (AC5).
     */
    @GetMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<PartnerMeetingDTO>> listMeetings() {
        return ResponseEntity.ok(meetingService.getMeetings());
    }

    /**
     * POST /api/v1/partner-meetings
     * Create a new partner meeting linked to a BATbern event (AC1).
     * Date is auto-filled from event-management-service.
     */
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PartnerMeetingDTO> createMeeting(
            @Valid @RequestBody CreateMeetingRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String organizerUsername = extractUsername(jwt);
        PartnerMeetingDTO created = meetingService.createMeeting(request, organizerUsername);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/v1/partner-meetings/{meetingId}
     * Get a single meeting.
     */
    @GetMapping("/{meetingId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PartnerMeetingDTO> getMeeting(@PathVariable UUID meetingId) {
        return ResponseEntity.ok(meetingService.getMeeting(meetingId));
    }

    /**
     * PATCH /api/v1/partner-meetings/{meetingId}
     * Update agenda or notes (AC2, AC4). All fields optional.
     */
    @PatchMapping("/{meetingId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PartnerMeetingDTO> updateMeeting(
            @PathVariable UUID meetingId,
            @RequestBody UpdateMeetingRequest request
    ) {
        return ResponseEntity.ok(meetingService.updateMeeting(meetingId, request));
    }

    /**
     * POST /api/v1/partner-meetings/{meetingId}/send-invite
     * Generate ICS and send to all partner contacts via SES (AC3).
     * Returns 202 Accepted immediately — email dispatched asynchronously (AC8).
     */
    @PostMapping("/{meetingId}/send-invite")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SendInviteResponse> sendInvite(@PathVariable UUID meetingId) {
        SendInviteResponse response = meetingService.sendInvite(meetingId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * DELETE /api/v1/partner-meetings/{meetingId}
     * Delete a partner meeting. If a calendar invite was already sent, a METHOD:CANCEL ICS
     * is dispatched asynchronously to all partners and organizers.
     * RSVPs are cascade-deleted automatically (V9 migration ON DELETE CASCADE).
     */
    @DeleteMapping("/{meetingId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteMeeting(@PathVariable UUID meetingId) {
        meetingService.deleteMeeting(meetingId);
        return ResponseEntity.noContent().build();
    }

    private String extractUsername(Jwt jwt) {
        if (jwt == null) {
            return "system";
        }
        // Try custom:username claim first, fall back to sub
        String username = jwt.getClaimAsString("custom:username");
        if (username == null || username.isBlank()) {
            username = jwt.getSubject();
        }
        return username != null ? username : "system";
    }
}
