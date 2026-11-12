package ch.batbern.partners.controller;

import ch.batbern.partners.meetings.api.generated.PartnerMeetingRsvpsApi;
import ch.batbern.partners.meetings.dto.generated.MeetingRsvpListResponse;
import ch.batbern.partners.service.PartnerMeetingRsvpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Organizer-facing RSVP query endpoint — Story 10.27 (AC7).
 *
 * Implements the generated {@link PartnerMeetingRsvpsApi} (partner-meetings-api spec).
 * Inbound iCal REPLYs are recorded through {@code POST /internal/calendar-replies}, the calendar
 * reply contract event-management owns (VPC-internal, not exposed through the gateway), served by
 * {@link InternalRsvpController} outside the {@code /api/v1} contract.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PartnerMeetingRsvpController implements PartnerMeetingRsvpsApi {

    private final PartnerMeetingRsvpService rsvpService;

    /** Get RSVP list + summary for a partner meeting — organizer only (AC7). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MeetingRsvpListResponse> getMeetingRsvps(UUID meetingId) {
        return ResponseEntity.ok(rsvpService.getMeetingRsvpResponse(meetingId));
    }
}
