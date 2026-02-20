package ch.batbern.events.service.slotassignment;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionTimingHistory;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.TimetableSlot;
import ch.batbern.events.exception.SessionNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionTimingHistoryRepository;
import ch.batbern.events.service.TimetableService;
import ch.batbern.shared.events.SessionTimingAssignedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing session timing assignment
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 *
 * Responsibilities:
 * - Assign timing (startTime, endTime, room) to placeholder sessions
 * - Unassign timing (set to null)
 * - Retrieve unassigned sessions
 * - Track timing changes in session_timing_history table
 *
 * Available slot computation is delegated to {@link TimetableService#getTimetable}
 * to guarantee the same slot positions the UI displays are used for auto-assign.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SessionTimingService {

    private static final List<String> STRUCTURAL_TYPES = List.of("moderation", "break", "lunch");

    private final SessionRepository sessionRepository;
    private final SessionTimingHistoryRepository sessionTimingHistoryRepository;
    private final ch.batbern.events.repository.SpeakerPoolRepository speakerPoolRepository;
    private final ch.batbern.events.service.SpeakerWorkflowService speakerWorkflowService;
    private final EventRepository eventRepository;
    private final TimetableService timetableService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Assign timing to a session (drag-and-drop slot assignment)
     */
    public Session assignTiming(String sessionSlug, Instant startTime, Instant endTime,
                               String room, String changeReason, String changedBy) {
        log.info("Assigning timing to session: {} ({} - {})",  sessionSlug, startTime, endTime);

        Session session = sessionRepository.findBySessionSlug(sessionSlug)
                .orElseThrow(() -> new SessionNotFoundException(sessionSlug));

        // Save previous timing for history
        Instant previousStartTime = session.getStartTime();
        Instant previousEndTime = session.getEndTime();
        String previousRoom = session.getRoom();

        // Detect whether scheduled times are actually changing
        boolean timingChanged = !startTime.equals(previousStartTime) || !endTime.equals(previousEndTime);

        // Update session with new timing
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        session.setRoom(room);

        // Clear actual execution data whenever scheduled times change — the Watch app
        // derives session state from actualStartTime / completedByUsername; stale values
        // after a reschedule would corrupt the W4.x countdown and Delayed-button logic.
        if (timingChanged) {
            session.setActualStartTime(null);
            session.setActualEndTime(null);
            session.setOverrunMinutes(null);
            session.setCompletedByUsername(null);
        }

        Session savedSession = sessionRepository.save(session);

        // Create history record
        SessionTimingHistory history = SessionTimingHistory.builder()
                .sessionId(session.getId())
                .previousStartTime(previousStartTime)
                .previousEndTime(previousEndTime)
                .previousRoom(previousRoom)
                .newStartTime(startTime)
                .newEndTime(endTime)
                .newRoom(room)
                .changeReason(changeReason)
                .changedBy(changedBy)
                .build();

        sessionTimingHistoryRepository.save(history);

        // Story BAT-11 Task 6: Auto-confirm speaker if quality reviewed
        checkAndAutoConfirmSpeaker(session, changedBy);

        // Publish SessionTimingAssignedEvent to trigger workflow transition to AGENDA_PUBLISHED
        publishSessionTimingAssignedEvent(session, changedBy);

        log.info("Session timing assigned successfully: {}", sessionSlug);
        return savedSession;
    }

    /**
     * Unassign timing from a session (clear slot assignment)
     */
    public Session unassignTiming(String sessionSlug, String changedBy) {
        log.info("Unassigning timing from session: {}", sessionSlug);

        Session session = sessionRepository.findBySessionSlug(sessionSlug)
                .orElseThrow(() -> new SessionNotFoundException(sessionSlug));

        // Save previous timing for history
        Instant previousStartTime = session.getStartTime();
        Instant previousEndTime = session.getEndTime();
        String previousRoom = session.getRoom();

        // Clear timing
        session.setStartTime(null);
        session.setEndTime(null);
        session.setRoom(null);

        Session savedSession = sessionRepository.save(session);

        // Create history record
        SessionTimingHistory history = SessionTimingHistory.builder()
                .sessionId(session.getId())
                .previousStartTime(previousStartTime)
                .previousEndTime(previousEndTime)
                .previousRoom(previousRoom)
                .newStartTime(null)
                .newEndTime(null)
                .newRoom(null)
                .changeReason("manual_adjustment")
                .changedBy(changedBy)
                .build();

        sessionTimingHistoryRepository.save(history);

        log.info("Session timing unassigned successfully: {}", sessionSlug);
        return savedSession;
    }

    /**
     * Clear all session timings for an event (reset all sessions to unassigned state)
     * AC: Clear All button functionality
     *
     * @param eventId Event UUID
     * @param changedBy Username of organizer performing the clear
     * @return Number of sessions cleared
     */
    public int clearAllTimings(UUID eventId, String changedBy) {
        log.info("Clearing all timings for event: {}", eventId);

        // Find all sessions with timing assigned
        List<Session> assignedSessions = sessionRepository.findByEventId(eventId).stream()
                .filter(session -> session.getStartTime() != null)
                .toList();

        log.info("Found {} sessions with timing to clear", assignedSessions.size());

        // Clear timing for each session
        int clearedCount = 0;
        for (Session session : assignedSessions) {
            try {
                unassignTiming(session.getSessionSlug(), changedBy);
                clearedCount++;
            } catch (Exception e) {
                log.error("Failed to clear timing for session: {}", session.getSessionSlug(), e);
                // Continue clearing other sessions even if one fails
            }
        }

        log.info("Successfully cleared {} session timings", clearedCount);
        return clearedCount;
    }

    /**
     * Auto-assign all unassigned sessions to available time slots.
     *
     * Slot positions are sourced from {@link TimetableService#getTimetable} to guarantee
     * that the positions used here match exactly what the drag-and-drop UI shows.
     *
     * @param event     Event entity
     * @param changedBy Username of organizer performing the auto-assignment
     * @return Number of sessions assigned
     */
    public int autoAssignTimings(Event event, String changedBy) {
        log.info("Auto-assigning sessions for event: {}", event.getEventCode());

        List<Session> unassignedSessions = getUnassignedSessionsByEventId(event.getId());
        if (unassignedSessions.isEmpty()) {
            log.info("No unassigned sessions to auto-assign");
            return 0;
        }

        log.info("Found {} unassigned sessions to assign", unassignedSessions.size());

        // Use timetable to get free speaker slots — guarantees UI/backend parity
        List<TimetableSlot> freeSlots = timetableService.getTimetable(event.getEventCode())
                .getSlots().stream()
                .filter(s -> s.getType() == TimetableSlot.Type.SPEAKER_SLOT)
                .filter(s -> s.getAssignedSessionSlug() == null)
                .toList();

        int assignedCount = 0;
        int slotIndex = 0;
        for (Session session : unassignedSessions) {
            if (slotIndex >= freeSlots.size()) {
                log.warn("No more available slots — {} sessions remain unassigned",
                        unassignedSessions.size() - assignedCount);
                break;
            }
            TimetableSlot slot = freeSlots.get(slotIndex++);
            try {
                assignTiming(session.getSessionSlug(), slot.getStartTime(), slot.getEndTime(),
                        "Main Hall", "preference_matching", changedBy);
                assignedCount++;
            } catch (Exception e) {
                log.error("Failed to auto-assign session: {}", session.getSessionSlug(), e);
            }
        }

        log.info("Auto-assigned {} of {} sessions", assignedCount, unassignedSessions.size());
        return assignedCount;
    }

    /**
     * Validate that a session exists (throws SessionNotFoundException if not found)
     */
    @Transactional(readOnly = true)
    public void validateSessionExists(String eventCode, String sessionSlug) {
        log.debug("Validating session exists: {} in event: {}", sessionSlug, eventCode);

        // Check if session exists - will throw SessionNotFoundException if not found
        sessionRepository.findBySessionSlug(sessionSlug)
                .orElseThrow(() -> new SessionNotFoundException(sessionSlug));
    }

    /**
     * Get unassigned sessions (placeholder sessions without timing)
     */
    @Transactional(readOnly = true)
    public List<Session> getUnassignedSessions(String eventCode) {
        log.info("Fetching unassigned sessions for event: {}", eventCode);
        return sessionRepository.findByEventCodeAndStartTimeIsNull(eventCode).stream()
                .filter(s -> s.getSessionType() == null || !STRUCTURAL_TYPES.contains(s.getSessionType()))
                .toList();
    }

    /**
     * Get unassigned sessions by event ID
     */
    @Transactional(readOnly = true)
    public List<Session> getUnassignedSessionsByEventId(UUID eventId) {
        log.info("Fetching unassigned sessions for event ID: {}", eventId);
        return sessionRepository.findByEventIdAndStartTimeIsNull(eventId).stream()
                .filter(s -> s.getSessionType() == null || !STRUCTURAL_TYPES.contains(s.getSessionType()))
                .toList();
    }

    /**
     * Get timing history for a session
     */
    @Transactional(readOnly = true)
    public List<SessionTimingHistory> getTimingHistory(String sessionSlug) {
        log.info("Fetching timing history for session: {}", sessionSlug);

        Session session = sessionRepository.findBySessionSlug(sessionSlug)
                .orElseThrow(() -> new SessionNotFoundException(sessionSlug));

        return sessionTimingHistoryRepository.findBySessionIdOrderByChangedAtDesc(session.getId());
    }

    /**
     * Check if speaker for this session needs auto-confirmation.
     *
     * Story BAT-11 Task 6: Workflow State Machine Integration
     *
     * When a session gets timing assigned, check if its speaker is in
     * QUALITY_REVIEWED state. If so, auto-confirm them by triggering
     * SpeakerWorkflowService state transition to CONFIRMED.
     *
     * @param session The session that just got timing assigned
     * @param organizer Username of organizer making the change
     */
    private void checkAndAutoConfirmSpeaker(Session session, String organizer) {
        // Find speakers assigned to this session (can be multiple for panels/co-presentations)
        List<SpeakerPool> speakers = speakerPoolRepository.findBySessionId(session.getId());

        for (SpeakerPool speaker : speakers) {
            log.debug("Found speaker {} for session {}", speaker.getId(), session.getId());

            // Check if speaker is in QUALITY_REVIEWED state
            if (speaker.getStatus() == ch.batbern.shared.types.SpeakerWorkflowState.QUALITY_REVIEWED) {
                log.info("Speaker {} is quality reviewed and now has timing - auto-confirming",
                        speaker.getId());

                // Trigger state transition to CONFIRMED
                speakerWorkflowService.updateSpeakerWorkflowState(
                        speaker.getId(),
                        ch.batbern.shared.types.SpeakerWorkflowState.CONFIRMED,
                        organizer
                );
            } else {
                log.debug("Speaker {} not ready for auto-confirmation (state: {})",
                        speaker.getId(), speaker.getStatus());
            }
        }
    }

    /**
     * Publish SessionTimingAssignedEvent for workflow automation.
     *
     * This event triggers the listener to check if all sessions have timing
     * and if the published phase is "speakers" or "agenda", then auto-transition
     * the event to AGENDA_PUBLISHED state.
     *
     * @param session The session that just got timing assigned
     * @param assignedBy Username of organizer making the change
     */
    private void publishSessionTimingAssignedEvent(Session session, String assignedBy) {
        try {
            // Get event details
            Event event = eventRepository.findById(session.getEventId())
                    .orElse(null);

            if (event == null) {
                log.warn("Event not found for session {}, skipping SessionTimingAssignedEvent", session.getId());
                return;
            }

            // Get speaker details if available (use first speaker for event - sessions can have multiple)
            List<SpeakerPool> speakers = speakerPoolRepository.findBySessionId(session.getId());
            Optional<SpeakerPool> primarySpeaker = speakers.isEmpty() ? Optional.empty() : Optional.of(speakers.get(0));

            // Convert Instant to LocalDateTime
            LocalDateTime startTime = session.getStartTime() != null
                    ? LocalDateTime.ofInstant(session.getStartTime(), ZoneId.systemDefault())
                    : null;

            if (startTime == null) {
                log.warn("Session {} has null startTime after assignment, skipping event publication", session.getId());
                return;
            }

            // Calculate duration in minutes
            Integer duration = null;
            if (session.getEndTime() != null) {
                long durationSeconds = session.getEndTime().getEpochSecond() - session.getStartTime().getEpochSecond();
                duration = (int) (durationSeconds / 60);
            }

            SessionTimingAssignedEvent timingEvent = SessionTimingAssignedEvent.builder()
                    .eventId(event.getId())
                    .eventCode(event.getEventCode())
                    .sessionId(session.getId())
                    .sessionTitle(session.getTitle())
                    .startTime(startTime)
                    .duration(duration)
                    .speakerPoolId(primarySpeaker.map(SpeakerPool::getId).orElse(null))
                    .speakerName(primarySpeaker.map(SpeakerPool::getSpeakerName).orElse(null))
                    .assignedBy(assignedBy)
                    .build();

            eventPublisher.publishEvent(timingEvent);
            log.debug("Published SessionTimingAssignedEvent for session: {}, event: {}",
                    session.getId(), event.getEventCode());

        } catch (Exception e) {
            // Log error but don't break the timing assignment
            log.error("Failed to publish SessionTimingAssignedEvent for session {}: {}",
                    session.getId(), e.getMessage(), e);
        }
    }
}
