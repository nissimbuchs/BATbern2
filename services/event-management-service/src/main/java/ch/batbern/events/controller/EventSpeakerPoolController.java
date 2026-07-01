package ch.batbern.events.controller;

import ch.batbern.events.service.SpeakerPoolService;
import ch.batbern.events.speakers.api.generated.EventActionsApi;
import ch.batbern.events.speakers.dto.generated.AddSpeakerToPoolRequest;
import ch.batbern.events.speakers.dto.generated.PatchSpeakerPoolRequest;
import ch.batbern.events.speakers.dto.generated.SpeakerPoolResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Event speaker-pool (brainstorming) endpoints, implementing the generated {@link EventActionsApi}
 * contract (event-speakers-api.openapi.yml — "Event Actions" tag). Phase 7 (ADR-006).
 *
 * <p>Extracted from {@code EventController} so the speaker-pool CRUD is contract-enforced without
 * re-pathing the (much larger, public-facing) event CRUD/registration controller. The remaining
 * EventController CRUD/EventDetail wiring is deferred — see the api-consolidation plan (EventController
 * needs the typed-event-response + lazy-loaded session-materials story first).
 *
 * Story 5.2 (AC9-13): Speaker Pool Management.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventSpeakerPoolController implements EventActionsApi {

    private final SpeakerPoolService speakerPoolService;

    /** GET /api/v1/events/{eventCode}/speakers/pool — list potential speakers being brainstormed. */
    @Override
    public ResponseEntity<List<SpeakerPoolResponse>> getSpeakerPool(String eventCode) {
        // Exceptions handled by GlobalExceptionHandler (EventNotFoundException → 404).
        return ResponseEntity.ok(speakerPoolService.getSpeakerPoolForEvent(eventCode));
    }

    /** POST /api/v1/events/{eventCode}/speakers/pool — add a potential speaker during brainstorming. */
    @Override
    public ResponseEntity<SpeakerPoolResponse> addSpeakerToPool(
            String eventCode, AddSpeakerToPoolRequest addSpeakerToPoolRequest) {
        // GlobalExceptionHandler: EventNotFoundException → 404, IllegalArgumentException → 400.
        SpeakerPoolResponse response = speakerPoolService.addSpeakerToPool(eventCode, addSpeakerToPoolRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** DELETE /api/v1/events/{eventCode}/speakers/pool/{speakerId} — remove a pool entry. */
    @Override
    public ResponseEntity<Void> deleteSpeakerFromPool(String eventCode, UUID speakerId) {
        // GlobalExceptionHandler: EventNotFoundException → 404, IllegalArgumentException → 404 (not found).
        speakerPoolService.deleteSpeakerFromPool(eventCode, speakerId.toString());
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/v1/events/{eventCode}/speakers/pool/{speakerId} — partial update (assigned organizer,
     * notes, brainstorm metadata). Story 11.D.1 (AR23): email may NOT be updated here (use promote);
     * unknown fields → 400 (additionalProperties:false on the request schema).
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SpeakerPoolResponse> patchSpeakerPoolEntry(
            String eventCode, UUID speakerId, PatchSpeakerPoolRequest patchSpeakerPoolRequest) {
        SpeakerPoolResponse response = speakerPoolService.patchEntry(
                eventCode, speakerId.toString(), patchSpeakerPoolRequest);
        return ResponseEntity.ok(response);
    }
}
