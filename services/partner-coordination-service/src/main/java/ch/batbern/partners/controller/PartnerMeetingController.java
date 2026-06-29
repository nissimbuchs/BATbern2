package ch.batbern.partners.controller;

import ch.batbern.partners.meetings.api.generated.PartnerMeetingsApi;
import ch.batbern.partners.meetings.dto.generated.CreateMeetingRequest;
import ch.batbern.partners.meetings.dto.generated.PartnerMeetingDTO;
import ch.batbern.partners.meetings.dto.generated.SendInviteResponse;
import ch.batbern.partners.meetings.dto.generated.UpdateMeetingRequest;
import ch.batbern.partners.security.SecurityContextHelper;
import ch.batbern.partners.service.PartnerMeetingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for partner meeting coordination — Story 8.3.
 *
 * Implements the generated {@link PartnerMeetingsApi} (partner-meetings-api spec). All endpoints
 * require ORGANIZER role (AC6). POST /send-invite returns 202 Accepted (async email dispatch) — AC8.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PartnerMeetingController implements PartnerMeetingsApi {

    private final PartnerMeetingService meetingService;
    private final SecurityContextHelper securityContextHelper;

    /** List all partner meetings sorted by date descending (AC5). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<PartnerMeetingDTO>> listPartnerMeetings() {
        return ResponseEntity.ok(meetingService.getMeetings());
    }

    /**
     * Create a new partner meeting linked to a BATbern event (AC1).
     * Date is auto-filled from event-management-service.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PartnerMeetingDTO> createPartnerMeeting(CreateMeetingRequest createMeetingRequest) {
        String organizerUsername = securityContextHelper.getCurrentUsername();
        PartnerMeetingDTO created = meetingService.createMeeting(createMeetingRequest, organizerUsername);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Get a single meeting. */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PartnerMeetingDTO> getPartnerMeeting(UUID meetingId) {
        return ResponseEntity.ok(meetingService.getMeeting(meetingId));
    }

    /** Update agenda or notes (AC2, AC4). All fields optional. */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PartnerMeetingDTO> updatePartnerMeeting(
            UUID meetingId, UpdateMeetingRequest updateMeetingRequest) {
        return ResponseEntity.ok(meetingService.updateMeeting(meetingId, updateMeetingRequest));
    }

    /**
     * Generate ICS and send to all partner contacts via SES (AC3).
     * Returns 202 Accepted immediately — email dispatched asynchronously (AC8).
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SendInviteResponse> sendPartnerMeetingInvite(UUID meetingId) {
        SendInviteResponse response = meetingService.sendInvite(meetingId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Delete a partner meeting. If a calendar invite was already sent, a METHOD:CANCEL ICS
     * is dispatched asynchronously to all partners and organizers.
     * RSVPs are cascade-deleted automatically (V9 migration ON DELETE CASCADE).
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deletePartnerMeeting(UUID meetingId) {
        meetingService.deleteMeeting(meetingId);
        return ResponseEntity.noContent().build();
    }
}
