package ch.batbern.partners.controller;

import ch.batbern.partners.domain.PartnerMeetingRsvp;
import ch.batbern.partners.domain.RsvpStatus;
import ch.batbern.partners.dto.MeetingRsvpListResponse;
import ch.batbern.partners.dto.RecordRsvpRequest;
import ch.batbern.partners.dto.RsvpDTO;
import ch.batbern.partners.service.PartnerMeetingRsvpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller for iCal RSVP tracking — Story 10.27 (AC6, AC7).
 *
 * POST /internal/partner-meetings/rsvps — permitAll, VPC-internal only (no JWT)
 * GET /api/v1/partner-meetings/{id}/rsvps — ORGANIZER role required
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class PartnerMeetingRsvpController {

    private final PartnerMeetingRsvpService rsvpService;

    /**
     * Internal endpoint: record or update an RSVP from an inbound iCal REPLY email (AC6).
     * Not JWT-protected — secured by VPC/Service Connect private DNS namespace only.
     */
    @PostMapping("/internal/partner-meetings/rsvps")
    public ResponseEntity<RsvpDTO> recordRsvp(@Valid @RequestBody RecordRsvpRequest request) {
        // Validate partStat against known enum values
        RsvpStatus status;
        try {
            status = RsvpStatus.valueOf(request.getPartStat().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown partStat value: {}", request.getPartStat());
            return ResponseEntity.badRequest().build();
        }

        PartnerMeetingRsvp rsvp = rsvpService.upsertRsvp(
                request.getMeetingId(), request.getAttendeeEmail(), status);

        return ResponseEntity.ok(RsvpDTO.builder()
                .attendeeEmail(rsvp.getAttendeeEmail())
                .status(rsvp.getStatus().name())
                .respondedAt(rsvp.getRespondedAt())
                .build());
    }

    /**
     * Get RSVP list for a partner meeting — organizer only (AC7).
     */
    @GetMapping("/api/v1/partner-meetings/{id}/rsvps")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MeetingRsvpListResponse> getRsvps(@PathVariable UUID id) {
        return ResponseEntity.ok(rsvpService.getMeetingRsvpResponse(id));
    }
}
