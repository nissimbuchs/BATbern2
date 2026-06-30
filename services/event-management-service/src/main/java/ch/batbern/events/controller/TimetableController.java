package ch.batbern.events.controller;

import ch.batbern.events.mapper.TimetableMapper;
import ch.batbern.events.service.TimetableService;
import ch.batbern.events.sessions.api.generated.TimetableApi;
import ch.batbern.events.sessions.dto.generated.TimetableResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the timetable endpoint.
 *
 * Exposes the single authoritative timeline for an event — the same algorithm
 * used by StructuralSessionService and SessionTimingService — so that the
 * frontend can render the slot grid from backend data rather than computing it locally.
 *
 * <p>Contract-first (Phase 7): implements the generated {@link TimetableApi}. The internal
 * {@link TimetableService#getTimetable(String)} keeps returning the internal scheduling model
 * (Instant-based, used for slot↔session keying and reorder logic); {@link TimetableMapper}
 * converts it to the generated wire {@link TimetableResponse} at this boundary.
 *
 * GET /api/v1/events/{eventCode}/timetable
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class TimetableController implements TimetableApi {

    private final TimetableService timetableService;
    private final TimetableMapper timetableMapper;

    /**
     * Get the full timetable for an event.
     *
     * Returns all slots (MODERATION, BREAK, LUNCH, SPEAKER_SLOT) in chronological order,
     * enriched with DB session slugs and assigned speaker session slugs.
     * Also includes unassigned speaker sessions.
     *
     * @param eventCode Public event identifier (e.g., "BATbern142")
     * @return 200 TimetableResponse, 404 if event or event type config not found
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TimetableResponse> getEventTimetable(String eventCode) {
        log.info("GET /api/v1/events/{}/timetable", eventCode);
        return ResponseEntity.ok(timetableMapper.toWire(timetableService.getTimetable(eventCode)));
    }
}
