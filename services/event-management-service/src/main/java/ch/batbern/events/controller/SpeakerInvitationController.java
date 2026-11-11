package ch.batbern.events.controller;

import ch.batbern.events.service.SpeakerInvitationService;
import ch.batbern.events.speakers.api.generated.SpeakerInvitationApi;
import ch.batbern.events.speakers.dto.generated.SendInvitationRequest;
import ch.batbern.events.speakers.dto.generated.SendInvitationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller for speaker invitation emails.
 * Story 6.1b: Speaker Invitation System. All endpoints require ORGANIZER role.
 *
 * <p>API-consolidation Phase 7 (2026-06-28): implements the generated
 * {@link SpeakerInvitationApi} (1:1 with the event-speakers `SpeakerInvitation` tag).
 * Paths + @Valid @RequestBody binding are inherited from the interface; the class
 * carries only the `/api/v1` prefix, and the overrides keep the method-level
 * {@code @PreAuthorize}.
 *
 * <p>Story 4.10: the speaker is addressed by its pool entry id ({@code {speakerId}}, one speaker
 * key under {@code /events/{eventCode}/speakers}). The test-only {@code POST /speakers/invite}
 * is gone: a speaker enters the pool with {@code POST /events/{eventCode}/speakers} and is
 * provisioned at promote.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class SpeakerInvitationController implements SpeakerInvitationApi {

    private final SpeakerInvitationService speakerInvitationService;

    /**
     * Send invitation email to a speaker.
     * AC3: Sends personalized email with the Cognito login
     * AC6: Publishes SpeakerInvitationSentEvent
     *
     * @return 200 OK with updated speaker details
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SendInvitationResponse> sendSpeakerInvitation(
            String eventCode, UUID speakerId, SendInvitationRequest sendInvitationRequest) {
        log.info("POST /api/v1/events/{}/speakers/{}/send-invitation", eventCode, speakerId);

        SendInvitationResponse response = speakerInvitationService.sendInvitation(
                eventCode, speakerId, sendInvitationRequest);

        return ResponseEntity.ok(response);
    }
}
