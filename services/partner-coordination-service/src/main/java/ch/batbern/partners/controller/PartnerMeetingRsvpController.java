package ch.batbern.partners.controller;

import ch.batbern.partners.domain.PartnerMeeting;
import ch.batbern.partners.domain.PartnerMeetingRsvp;
import ch.batbern.partners.domain.RsvpStatus;
import ch.batbern.partners.dto.MeetingRsvpListResponse;
import ch.batbern.partners.dto.RecordRsvpRequest;
import ch.batbern.partners.dto.RsvpDTO;
import ch.batbern.partners.dto.RsvpSummary;
import ch.batbern.partners.exception.PartnerNotFoundException;
import ch.batbern.partners.repository.PartnerMeetingRepository;
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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final PartnerMeetingRepository meetingRepository;

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

        return ResponseEntity.ok(toDTO(rsvp));
    }

    /**
     * Get RSVP list for a partner meeting — organizer only (AC7).
     */
    @GetMapping("/api/v1/partner-meetings/{id}/rsvps")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MeetingRsvpListResponse> getRsvps(@PathVariable UUID id) {
        PartnerMeeting meeting = meetingRepository.findById(id)
                .orElseThrow(() -> new PartnerNotFoundException("Partner meeting not found: " + id));

        List<PartnerMeetingRsvp> rsvps = rsvpService.getRsvps(id);

        List<RsvpDTO> rsvpDTOs = rsvps.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        RsvpSummary summary = buildSummary(rsvps);

        return ResponseEntity.ok(MeetingRsvpListResponse.builder()
                .meetingId(id)
                .inviteSentAt(meeting.getInviteSentAt())
                .rsvps(rsvpDTOs)
                .summary(summary)
                .build());
    }

    private RsvpDTO toDTO(PartnerMeetingRsvp rsvp) {
        return RsvpDTO.builder()
                .attendeeEmail(rsvp.getAttendeeEmail())
                .status(rsvp.getStatus().name())
                .respondedAt(rsvp.getRespondedAt())
                .build();
    }

    private RsvpSummary buildSummary(List<PartnerMeetingRsvp> rsvps) {
        int accepted = (int) rsvps.stream().filter(r -> r.getStatus() == RsvpStatus.ACCEPTED).count();
        int declined = (int) rsvps.stream().filter(r -> r.getStatus() == RsvpStatus.DECLINED).count();
        int tentative = (int) rsvps.stream().filter(r -> r.getStatus() == RsvpStatus.TENTATIVE).count();
        return RsvpSummary.builder()
                .accepted(accepted)
                .declined(declined)
                .tentative(tentative)
                .build();
    }
}
