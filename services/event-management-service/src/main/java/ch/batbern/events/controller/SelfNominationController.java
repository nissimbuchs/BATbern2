package ch.batbern.events.controller;

import ch.batbern.events.speakers.api.generated.SpeakerSelfNominationApi;
import ch.batbern.events.speakers.dto.generated.SelfNominateSpeakerRequest;
import ch.batbern.events.speakers.dto.generated.SpeakerPoolResponse;
import ch.batbern.events.service.SpeakerPoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 *
 * <p>API-consolidation Phase 7 (2026-06-28): implements the generated
 * {@link SpeakerSelfNominationApi} (re-tagged out of the Event Actions grab-bag into a 1:1
 * tag). The path + {@code @Valid @RequestBody} binding are inherited from the interface; the
 * class carries only the {@code /api/v1} prefix, and the override keeps the {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class SelfNominationController implements SpeakerSelfNominationApi {

    private final SpeakerPoolService speakerPoolService;

    /**
     * Self-nominate as a speaker for {@code eventCode} (AC1).
     *
     * @param eventCode the target event (supplied by the upcoming-event card)
     * @param selfNominateSpeakerRequest proposed talk: {@code sessionTitle} + {@code abstract}
     *                  (identity is auto-filled from the attendee's profile server-side)
     * @return 201 Created with the new pool entry (status IDENTIFIED, source self_nomination)
     */
    @Override
    @PreAuthorize("hasRole('ATTENDEE')")
    public ResponseEntity<SpeakerPoolResponse> selfNominateSpeaker(
            String eventCode, SelfNominateSpeakerRequest selfNominateSpeakerRequest) {
        SpeakerPoolResponse response = speakerPoolService.selfNominate(eventCode, selfNominateSpeakerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
