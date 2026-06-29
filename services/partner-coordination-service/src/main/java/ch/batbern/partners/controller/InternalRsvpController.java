package ch.batbern.partners.controller;

import ch.batbern.partners.domain.PartnerMeetingRsvp;
import ch.batbern.partners.domain.RsvpStatus;
import ch.batbern.partners.dto.RecordRsvpRequest;
import ch.batbern.partners.meetings.dto.generated.RsvpDTO;
import ch.batbern.partners.service.PartnerMeetingRsvpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal RSVP recording endpoint for inbound iCal REPLY emails — Story 10.27 (AC6).
 *
 * <p>{@code POST /internal/partner-meetings/rsvps} is NOT JWT-protected — it is secured by the
 * VPC / Service Connect private DNS namespace only and is intentionally NOT exposed through the
 * API gateway, so it is not part of the partner-meetings-api public contract. It lives outside the
 * {@code /api/v1} prefix and therefore in its own controller (separate from the contract-first
 * {@link PartnerMeetingRsvpController}).
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class InternalRsvpController {

    private final PartnerMeetingRsvpService rsvpService;

    /**
     * Record or update an RSVP from an inbound iCal REPLY email (AC6).
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

        return ResponseEntity.ok(PartnerMeetingRsvpService.toRsvpDTO(rsvp));
    }
}
