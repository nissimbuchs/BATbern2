package ch.batbern.events.controller;

import ch.batbern.events.dto.SelfNominateSpeakerRequest;
import ch.batbern.events.dto.SpeakerPoolResponse;
import ch.batbern.events.service.SpeakerPoolService;
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
 * Attendee speaker self-nomination — Story 7.2 "I Could Speak on That".
 *
 * <p>Lets a logged-in attendee raise their hand with a proposed talk once an event's topic is
 * set + published, feeding BATbern's speaker pipeline from the floor (pull) rather than only
 * organizer outreach (push). The nomination composes onto the unified speaker workflow
 * (Epic 11 / ADR-009): it lands as a {@code speaker_pool} row at {@code IDENTIFIED} tagged
 * {@code source = 'self_nomination'} and follows the unchanged triage → promote path.
 *
 * <p>Security: {@code @PreAuthorize("hasRole('ATTENDEE')")} role-gates the endpoint; the
 * api-gateway + this service's {@code SecurityConfig} already require authentication for any
 * non-public route, so anonymous callers are rejected before reaching the controller (401 at
 * the gateway). Provisioning (Cognito user + SPEAKER role + session_users row) is NOT triggered
 * here — that remains organizer-only at promote-to-READY ({@code SpeakerStatusController}).
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/speakers")
@RequiredArgsConstructor
@Slf4j
public class SelfNominationController {

    private final SpeakerPoolService speakerPoolService;

    /**
     * Self-nominate as a speaker for {@code eventCode} (AC1).
     *
     * @param eventCode the target event (supplied by the upcoming-event card)
     * @param request   proposed talk: {@code sessionTitle} + {@code abstract} (identity is
     *                  auto-filled from the attendee's profile server-side)
     * @return 201 Created with the new pool entry (status IDENTIFIED, source self_nomination)
     */
    @PostMapping("/self-nominate")
    @PreAuthorize("hasRole('ATTENDEE')")
    public ResponseEntity<SpeakerPoolResponse> selfNominate(
            @PathVariable String eventCode,
            @Valid @RequestBody SelfNominateSpeakerRequest request) {
        SpeakerPoolResponse response = speakerPoolService.selfNominate(eventCode, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
