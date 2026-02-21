package ch.batbern.events.controller;

import ch.batbern.events.dto.TimetableResponse;
import ch.batbern.events.service.TimetableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the timetable endpoint.
 *
 * Exposes the single authoritative timeline for an event — the same algorithm
 * used by StructuralSessionService and SessionTimingService — so that the
 * frontend can render the slot grid from backend data rather than computing it locally.
 *
 * GET /api/v1/events/{eventCode}/timetable
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}")
@RequiredArgsConstructor
@Slf4j
public class TimetableController {

    private final TimetableService timetableService;

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
    @GetMapping("/timetable")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TimetableResponse> getTimetable(@PathVariable String eventCode) {
        log.info("GET /api/v1/events/{}/timetable", eventCode);
        TimetableResponse response = timetableService.getTimetable(eventCode);
        return ResponseEntity.ok(response);
    }
}
