package ch.batbern.events.controller;

import ch.batbern.events.dto.BatchInviteRequest;
import ch.batbern.events.dto.BatchInviteResponse;
import ch.batbern.events.dto.InviteSpeakerRequest;
import ch.batbern.events.dto.InviteSpeakerResponse;
import ch.batbern.events.dto.SendInvitationRequest;
import ch.batbern.events.dto.SendInvitationResponse;
import ch.batbern.events.service.SpeakerInvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for speaker invitation management.
 * Story 6.1b: Speaker Invitation System
 *
 * Provides endpoints for:
 * - Inviting speakers to events (single and batch)
 * - Sending/resending invitation emails
 *
 * All endpoints require ORGANIZER role.
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/speakers")
@RequiredArgsConstructor
@Slf4j
public class SpeakerInvitationController {

    private final SpeakerInvitationService speakerInvitationService;

    /**
     * Invite a speaker to an event.
     * AC1: Creates SpeakerPool entry
     * AC2: Auto-creates User if needed
     *
     * @param eventCode the event code
     * @param request the invitation request
     * @return 201 Created with speaker details if new, 200 OK if existing
     */
    @PostMapping("/invite")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<InviteSpeakerResponse> inviteSpeaker(
            @PathVariable String eventCode,
            @Valid @RequestBody InviteSpeakerRequest request
    ) {
        log.info("POST /api/v1/events/{}/speakers/invite - email: {}", eventCode, request.email());

        InviteSpeakerResponse response = speakerInvitationService.inviteSpeaker(eventCode, request);

        // Return 201 if newly created, 200 if existing (idempotency)
        HttpStatus status = response.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Batch invite speakers to an event.
     * AC5: Handles multiple invitations with partial failure support
     *
     * @param eventCode the event code
     * @param request the batch invitation request
     * @return 200 OK with results and any errors
     */
    @PostMapping("/invite-batch")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<BatchInviteResponse> inviteBatch(
            @PathVariable String eventCode,
            @Valid @RequestBody BatchInviteRequest request
    ) {
        log.info("POST /api/v1/events/{}/speakers/invite-batch - count: {}",
                eventCode, request.speakers().size());

        BatchInviteResponse response = speakerInvitationService.inviteBatch(eventCode, request);

        // Return 207 Multi-Status if there were partial failures
        HttpStatus status = response.failedCount() > 0 && response.successCount() > 0
                ? HttpStatus.MULTI_STATUS
                : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Send invitation email to a speaker.
     * AC3: Sends personalized email with magic links
     * AC6: Publishes SpeakerInvitationSentEvent
     *
     * @param eventCode the event code
     * @param username the speaker's username
     * @param request the send invitation request
     * @return 200 OK with updated speaker details
     */
    @PostMapping("/{username}/send-invitation")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SendInvitationResponse> sendInvitation(
            @PathVariable String eventCode,
            @PathVariable String username,
            @Valid @RequestBody SendInvitationRequest request
    ) {
        log.info("POST /api/v1/events/{}/speakers/{}/send-invitation", eventCode, username);

        SendInvitationResponse response = speakerInvitationService.sendInvitation(
                eventCode, username, request);

        return ResponseEntity.ok(response);
    }
}
