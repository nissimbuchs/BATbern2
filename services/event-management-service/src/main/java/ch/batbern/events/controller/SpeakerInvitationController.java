package ch.batbern.events.controller;

import ch.batbern.events.speakers.api.generated.SpeakerInvitationApi;
import ch.batbern.events.speakers.dto.generated.BatchInviteRequest;
import ch.batbern.events.speakers.dto.generated.BatchInviteResponse;
import ch.batbern.events.speakers.dto.generated.InviteSpeakerRequest;
import ch.batbern.events.speakers.dto.generated.InviteSpeakerResponse;
import ch.batbern.events.speakers.dto.generated.SendInvitationRequest;
import ch.batbern.events.speakers.dto.generated.SendInvitationResponse;
import ch.batbern.events.service.SpeakerInvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 *
 * <p>API-consolidation Phase 7 (2026-06-28): implements the generated
 * {@link SpeakerInvitationApi} (1:1 with the event-speakers `SpeakerInvitation` tag).
 * Paths + @Valid @RequestBody binding are inherited from the interface; the class
 * carries only the `/api/v1` prefix, and the overrides keep the method-level
 * {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class SpeakerInvitationController implements SpeakerInvitationApi {

    private final SpeakerInvitationService speakerInvitationService;

    /**
     * Invite a speaker to an event.
     * AC1: Creates SpeakerPool entry
     * AC2: Auto-creates User if needed
     *
     * @return 201 Created with speaker details if new, 200 OK if existing
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<InviteSpeakerResponse> inviteSpeaker(
            String eventCode, InviteSpeakerRequest inviteSpeakerRequest) {
        log.info("POST /api/v1/events/{}/speakers/invite - email: {}",
                eventCode, inviteSpeakerRequest.getEmail());

        InviteSpeakerResponse response = speakerInvitationService.inviteSpeaker(eventCode, inviteSpeakerRequest);

        // Return 201 if newly created, 200 if existing (idempotency)
        HttpStatus status = Boolean.TRUE.equals(response.getCreated()) ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Batch invite speakers to an event.
     * AC5: Handles multiple invitations with partial failure support
     *
     * @return 200 OK with results and any errors (207 Multi-Status on partial failure)
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<BatchInviteResponse> inviteSpeakerBatch(
            String eventCode, BatchInviteRequest batchInviteRequest) {
        log.info("POST /api/v1/events/{}/speakers/invite-batch - count: {}",
                eventCode, batchInviteRequest.getSpeakers().size());

        BatchInviteResponse response = speakerInvitationService.inviteBatch(eventCode, batchInviteRequest);

        // Return 207 Multi-Status if there were partial failures
        HttpStatus status = response.getFailedCount() > 0 && response.getSuccessCount() > 0
                ? HttpStatus.MULTI_STATUS
                : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Send invitation email to a speaker.
     * AC3: Sends personalized email with magic links
     * AC6: Publishes SpeakerInvitationSentEvent
     *
     * @return 200 OK with updated speaker details
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SendInvitationResponse> sendSpeakerInvitation(
            String eventCode, String username, SendInvitationRequest sendInvitationRequest) {
        log.info("POST /api/v1/events/{}/speakers/{}/send-invitation", eventCode, username);

        SendInvitationResponse response = speakerInvitationService.sendInvitation(
                eventCode, username, sendInvitationRequest);

        return ResponseEntity.ok(response);
    }
}
