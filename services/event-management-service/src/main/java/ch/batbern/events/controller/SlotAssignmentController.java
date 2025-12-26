package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.service.slotassignment.ConflictAnalysisResponse;
import ch.batbern.events.service.slotassignment.ConflictDetectionService;
import ch.batbern.events.service.slotassignment.SessionTimingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST controller for slot assignment endpoints
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 *
 * Endpoints:
 * - GET /api/v1/events/{eventCode}/sessions/unassigned
 * - PATCH /api/v1/events/{eventCode}/sessions/{sessionSlug}/timing
 * - POST /api/v1/events/{eventCode}/sessions/bulk-timing
 * - GET /api/v1/events/{eventCode}/sessions/conflicts
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/sessions")
@RequiredArgsConstructor
@Slf4j
public class SlotAssignmentController {

    private final SessionTimingService sessionTimingService;
    private final ConflictDetectionService conflictDetectionService;
    private final EventRepository eventRepository;

    /**
     * Get unassigned sessions (placeholder sessions without timing)
     * AC5, AC12: Show unassigned speakers list
     */
    @GetMapping("/unassigned")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<Session>> getUnassignedSessions(
            @PathVariable String eventCode) {

        log.info("GET /api/v1/events/{}/sessions/unassigned", eventCode);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        List<Session> unassignedSessions = sessionTimingService.getUnassignedSessionsByEventId(event.getId());

        // Set eventCode for API responses
        unassignedSessions.forEach(session -> session.setEventCode(eventCode));

        return ResponseEntity.ok(unassignedSessions);
    }

    /**
     * Assign timing to a session (drag-and-drop)
     * AC5-AC9: Drag-and-drop slot assignment with conflict detection
     */
    @PatchMapping("/{sessionSlug}/timing")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<?> assignTiming(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug,
            @RequestBody TimingAssignmentRequest request) {

        log.info("PATCH /api/v1/events/{}/sessions/{}/timing", eventCode, sessionSlug);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        // First validate session exists (will throw SessionNotFoundException if not found)
        // This ensures 404 is returned before conflict checks
        sessionTimingService.validateSessionExists(eventCode, sessionSlug);

        // Detect conflicts before assignment
        var roomConflict = conflictDetectionService.detectRoomOverlap(
                eventCode, request.startTime, request.endTime, request.room);

        if (roomConflict.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "TIMING_CONFLICT",
                            "message", roomConflict.get().getMessage(),
                            "conflicts", List.of(Map.of(
                                    "type", "room_overlap",
                                    "conflictingSessionSlug", "existing-session" // Simplified for MVP
                            ))
                    ));
        }

        // Check for speaker double-booking
        var speakerConflict = conflictDetectionService.detectSpeakerDoubleBooking(
                sessionSlug, request.startTime, request.endTime);

        if (speakerConflict.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "TIMING_CONFLICT",
                            "message", speakerConflict.get().getMessage(),
                            "conflicts", List.of(Map.of(
                                    "type", "speaker_double_booked",
                                    "conflictingSessionSlug", speakerConflict.get().getConflictingSessionSlug()
                            ))
                    ));
        }

        // Assign timing
        Session updatedSession = sessionTimingService.assignTiming(
                sessionSlug,
                request.startTime,
                request.endTime,
                request.room,
                request.changeReason != null ? request.changeReason : "manual_assignment",
                "organizer" // TODO: Get from security context
        );

        updatedSession.setEventCode(eventCode);

        return ResponseEntity.ok(updatedSession);
    }

    /**
     * Bulk assign timing to multiple sessions
     * AC13: Bulk auto-assignment
     */
    @PostMapping("/bulk-timing")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<?> bulkAssignTiming(
            @PathVariable String eventCode,
            @RequestBody BulkTimingRequest request) {

        log.info("POST /api/v1/events/{}/sessions/bulk-timing ({}  assignments)",
                eventCode, request.assignments.size());

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        // Check for conflicts across all assignments
        // Simplified: just check if any two assignments conflict with each other
        for (int i = 0; i < request.assignments.size(); i++) {
            for (int j = i + 1; j < request.assignments.size(); j++) {
                TimingAssignment a = request.assignments.get(i);
                TimingAssignment b = request.assignments.get(j);

                if (a.room.equals(b.room)
                    && timesOverlap(a.startTime, a.endTime, b.startTime, b.endTime)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of(
                                    "error", "BULK_TIMING_CONFLICTS",
                                    "message", "Conflicts detected - no changes applied",
                                    "conflictCount", 1
                            ));
                }
            }
        }

        // Apply all assignments
        List<Session> assignedSessions = request.assignments.stream()
                .map(assignment -> {
                    Session session = sessionTimingService.assignTiming(
                            assignment.sessionSlug,
                            assignment.startTime,
                            assignment.endTime,
                            assignment.room,
                            request.changeReason != null ? request.changeReason : "bulk_assignment",
                            "organizer"
                    );
                    session.setEventCode(eventCode);
                    return session;
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "assignedCount", assignedSessions.size(),
                "sessions", assignedSessions
        ));
    }

    /**
     * Get comprehensive conflict analysis
     * AC9: Detect all scheduling conflicts
     */
    @GetMapping("/conflicts")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ConflictAnalysisResponse> analyzeConflicts(
            @PathVariable String eventCode) {

        log.info("GET /api/v1/events/{}/sessions/conflicts", eventCode);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        ConflictAnalysisResponse analysis = conflictDetectionService.analyzeAllConflicts(eventCode);

        return ResponseEntity.ok(analysis);
    }

    private boolean timesOverlap(Instant start1, Instant end1, Instant start2, Instant end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    // DTOs

    record TimingAssignmentRequest(
            Instant startTime,
            Instant endTime,
            String room,
            String sessionType,
            String changeReason,
            String notes
    ) {}

    record BulkTimingRequest(
            List<TimingAssignment> assignments,
            String changeReason
    ) {}

    record TimingAssignment(
            String sessionSlug,
            Instant startTime,
            Instant endTime,
            String room
    ) {}
}
