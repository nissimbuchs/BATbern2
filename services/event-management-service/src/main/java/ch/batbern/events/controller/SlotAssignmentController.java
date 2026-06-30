package ch.batbern.events.controller;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.TimetableResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.mapper.TimetableMapper;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.service.SessionService;
import ch.batbern.events.service.slotassignment.ConflictDetectionService;
import ch.batbern.events.service.slotassignment.SessionTimingService;
import ch.batbern.events.service.slotassignment.SlotAssignmentMode;
import ch.batbern.events.service.slotassignment.SlotReorderService;
import ch.batbern.events.sessions.api.generated.SlotAssignmentApi;
import ch.batbern.events.sessions.dto.generated.AutoAssignResponse;
import ch.batbern.events.sessions.dto.generated.BulkTimingResponse;
import ch.batbern.events.sessions.dto.generated.ClearTimingsResponse;
import ch.batbern.events.sessions.dto.generated.ConflictAnalysisResponse;
import ch.batbern.events.sessions.dto.generated.SessionResponse;
import ch.batbern.events.sessions.dto.generated.SessionTimingRequest;
import ch.batbern.events.sessions.dto.generated.SlotAssignmentRequest;
import ch.batbern.events.sessions.dto.generated.TimingConflictError;
import ch.batbern.events.sessions.dto.generated.TimingConflictItem;
import ch.batbern.events.sessions.dto.generated.BulkTimingConflictError;
import ch.batbern.events.sessions.dto.generated.BulkTimingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * REST controller for slot-assignment endpoints (Story BAT-11 / 5.7, Story 15.3).
 *
 * <p>API-consolidation Phase 7: implements the generated {@link SlotAssignmentApi}. Session-returning
 * ops map the domain entity to the enriched generated {@link SessionResponse} (the FE already types
 * these responses as the generated session shape — fixes the prior raw-JPA-entity leak). The two
 * 409 conflict paths throw {@link TimingConflictException}/{@link BulkTimingConflictException},
 * rendered to typed bodies by the {@code @ExceptionHandler}s below.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class SlotAssignmentController implements SlotAssignmentApi {

    private static final String ACTOR = "organizer"; // TODO: resolve from security context

    private final SessionTimingService sessionTimingService;
    private final SlotReorderService slotReorderService;
    private final ConflictDetectionService conflictDetectionService;
    private final EventRepository eventRepository;
    private final SessionService sessionService;
    private final TimetableMapper timetableMapper;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<SessionResponse>> getUnassignedSessions(String eventCode) {
        log.info("GET /api/v1/events/{}/sessions/unassigned", eventCode);
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        List<SessionResponse> response = sessionTimingService
                .getUnassignedSessionsByEventId(event.getId()).stream()
                .map(session -> sessionService.toSessionResponse(session, eventCode))
                .toList();
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<SessionResponse> assignTiming(
            String eventCode, String sessionSlug, SessionTimingRequest request) {
        log.info("PATCH /api/v1/events/{}/sessions/{}/timing", eventCode, sessionSlug);

        eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));
        // 404 before conflict checks
        sessionTimingService.validateSessionExists(eventCode, sessionSlug);

        Instant startTime = toInstant(request.getStartTime());
        Instant endTime = toInstant(request.getEndTime());

        conflictDetectionService.detectRoomOverlap(eventCode, startTime, endTime, request.getRoom(), sessionSlug)
                .ifPresent(c -> {
                    throw new TimingConflictException(c.getMessage(), "room_overlap", "existing-session");
                });
        conflictDetectionService.detectSpeakerDoubleBooking(sessionSlug, startTime, endTime)
                .ifPresent(c -> {
                    throw new TimingConflictException(
                            c.getMessage(), "speaker_double_booked", c.getConflictingSessionSlug());
                });

        Session updated = sessionTimingService.assignTiming(
                sessionSlug, startTime, endTime, request.getRoom(),
                request.getChangeReason() != null ? request.getChangeReason() : "manual_adjustment", ACTOR);
        updated.setEventCode(eventCode);
        return ResponseEntity.ok(sessionService.toSessionResponse(updated, eventCode));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<ch.batbern.events.sessions.dto.generated.TimetableResponse> assignSessionToSlot(
            String eventCode, String sessionSlug, SlotAssignmentRequest request) {
        log.info("POST /api/v1/events/{}/sessions/{}/slot ({} -> {})",
                eventCode, sessionSlug, request.getMode(), request.getTargetSlotKey());

        TimetableResponse timetable = slotReorderService.assignToSlot(
                eventCode, sessionSlug, request.getTargetSlotKey(),
                SlotAssignmentMode.valueOf(request.getMode().name()), ACTOR);
        return ResponseEntity.ok(timetableMapper.toWire(timetable));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<SessionResponse> unassignTiming(String eventCode, String sessionSlug) {
        log.info("DELETE /api/v1/events/{}/sessions/{}/timing", eventCode, sessionSlug);
        eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));
        sessionTimingService.validateSessionExists(eventCode, sessionSlug);

        Session updated = sessionTimingService.unassignTiming(sessionSlug, ACTOR);
        updated.setEventCode(eventCode);
        return ResponseEntity.ok(sessionService.toSessionResponse(updated, eventCode));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<BulkTimingResponse> bulkAssignTiming(String eventCode, BulkTimingRequest request) {
        log.info("POST /api/v1/events/{}/sessions/bulk-timing ({} assignments)",
                eventCode, request.getAssignments().size());

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        // All-or-nothing: any cross-assignment conflict applies no changes.
        var assignments = request.getAssignments();
        for (int i = 0; i < assignments.size(); i++) {
            for (int j = i + 1; j < assignments.size(); j++) {
                var a = assignments.get(i);
                var b = assignments.get(j);
                if (a.getRoom() != null && a.getRoom().equals(b.getRoom())
                        && timesOverlap(toInstant(a.getStartTime()), toInstant(a.getEndTime()),
                        toInstant(b.getStartTime()), toInstant(b.getEndTime()))) {
                    throw new BulkTimingConflictException("Conflicts detected - no changes applied", 1);
                }
            }
        }

        List<SessionResponse> sessions = assignments.stream()
                .map(assignment -> {
                    Session session = sessionTimingService.assignTiming(
                            assignment.getSessionSlug(),
                            toInstant(assignment.getStartTime()),
                            toInstant(assignment.getEndTime()),
                            assignment.getRoom(),
                            request.getChangeReason() != null ? request.getChangeReason() : "bulk_assignment",
                            ACTOR);
                    session.setEventCode(eventCode);
                    return sessionService.toSessionResponse(session, eventCode);
                })
                .toList();

        return ResponseEntity.ok(BulkTimingResponse.builder()
                .assignedCount(sessions.size())
                .sessions(sessions)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ConflictAnalysisResponse> analyzeConflicts(String eventCode) {
        log.info("GET /api/v1/events/{}/sessions/conflicts", eventCode);
        eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));
        return ResponseEntity.ok(conflictDetectionService.analyzeAllConflicts(eventCode));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<ClearTimingsResponse> clearAllTimings(String eventCode) {
        log.info("DELETE /api/v1/events/{}/sessions/timing", eventCode);
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));
        int clearedCount = sessionTimingService.clearAllTimings(event.getId(), ACTOR);
        return ResponseEntity.ok(ClearTimingsResponse.builder()
                .message("All session timings cleared successfully")
                .clearedCount(clearedCount)
                .build());
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<AutoAssignResponse> autoAssignTimings(String eventCode) {
        log.info("POST /api/v1/events/{}/sessions/auto-assign", eventCode);
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));
        int assignedCount = sessionTimingService.autoAssignTimings(event, ACTOR);
        return ResponseEntity.ok(AutoAssignResponse.builder()
                .message("Sessions auto-assigned successfully")
                .assignedCount(assignedCount)
                .build());
    }

    // === Conflict rendering (409) ===

    @ExceptionHandler(TimingConflictException.class)
    public ResponseEntity<TimingConflictError> handleTimingConflict(TimingConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(TimingConflictError.builder()
                .error("TIMING_CONFLICT")
                .message(ex.getMessage())
                .conflicts(List.of(TimingConflictItem.builder()
                        .type(ex.type)
                        .conflictingSessionSlug(ex.conflictingSessionSlug)
                        .build()))
                .build());
    }

    @ExceptionHandler(BulkTimingConflictException.class)
    public ResponseEntity<BulkTimingConflictError> handleBulkTimingConflict(BulkTimingConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(BulkTimingConflictError.builder()
                .error("BULK_TIMING_CONFLICTS")
                .message(ex.getMessage())
                .conflictCount(ex.conflictCount)
                .build());
    }

    private static Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }

    private static boolean timesOverlap(Instant start1, Instant end1, Instant start2, Instant end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    /** 409 — single-session timing conflict (room overlap / speaker double-booking). */
    static class TimingConflictException extends RuntimeException {
        final String type;
        final String conflictingSessionSlug;

        TimingConflictException(String message, String type, String conflictingSessionSlug) {
            super(message);
            this.type = type;
            this.conflictingSessionSlug = conflictingSessionSlug;
        }
    }

    /** 409 — bulk timing conflicts detected; no changes applied. */
    static class BulkTimingConflictException extends RuntimeException {
        final int conflictCount;

        BulkTimingConflictException(String message, int conflictCount) {
            super(message);
            this.conflictCount = conflictCount;
        }
    }
}
