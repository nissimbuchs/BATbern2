package ch.batbern.events.service.slotassignment;

import ch.batbern.events.domain.Session;
import ch.batbern.events.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SessionTimingService
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing - Task 2a
 *
 * IMPORTANT: RED PHASE tests - will fail until SessionTimingService is implemented.
 *
 * Service responsibilities:
 * - Assign timing (startTime, endTime, room) to placeholder sessions
 * - Unassign timing (set to null)
 * - Retrieve unassigned sessions
 * - Track timing changes in session_timing_history table
 *
 * Tests cover AC5-AC12: Session timing assignment and tracking
 */
@ExtendWith(MockitoExtension.class)
class SessionTimingServiceTest {

    private SessionTimingService sessionTimingService;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SessionTimingHistoryRepository sessionTimingHistoryRepository;

    private Session placeholderSession;
    private Session assignedSession;

    @BeforeEach
    void setUp() {
        sessionTimingService = new SessionTimingService(sessionRepository, sessionTimingHistoryRepository);

        placeholderSession = Session.builder()
                .id(UUID.randomUUID())
                .sessionSlug("john-doe-techcorp")
                .title("John Doe - TechCorp")
                .startTime(null)
                .endTime(null)
                .room(null)
                .build();

        assignedSession = Session.builder()
                .id(UUID.randomUUID())
                .sessionSlug("jane-smith-datainc")
                .title("Jane Smith - DataInc")
                .startTime(LocalDateTime.parse("2025-06-15T09:00:00"))
                .endTime(LocalDateTime.parse("2025-06-15T09:45:00"))
                .room("Main Hall")
                .build();
    }

    /**
     * Test: Assign timing to placeholder session
     * AC5-AC6: Organizer assigns session to time slot via drag-and-drop
     */
    @Test
    void should_assignTiming_when_validSessionAndTimingProvided() {
        // Given: Placeholder session (startTime=null, endTime=null)
        String sessionSlug = "john-doe-techcorp";
        LocalDateTime startTime = LocalDateTime.parse("2025-06-15T09:00:00");
        LocalDateTime endTime = LocalDateTime.parse("2025-06-15T09:45:00");
        String room = "Main Hall";
        String changeReason = "drag_drop_reassignment";
        String changedBy = "test-organizer@batbern.ch";

        when(sessionRepository.findBySessionSlug(sessionSlug)).thenReturn(Optional.of(placeholderSession));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: assignTiming(sessionSlug, startTime, endTime, room, changeReason)
        Session result = sessionTimingService.assignTiming(sessionSlug, startTime, endTime, room, changeReason, changedBy);

        // Then: Session updated with timing
        assertThat(result.getStartTime()).isEqualTo(startTime);
        assertThat(result.getEndTime()).isEqualTo(endTime);
        assertThat(result.getRoom()).isEqualTo(room);

        // And: History record created in session_timing_history
        verify(sessionTimingHistoryRepository).save(argThat(history ->
                history.getSessionId().equals(placeholderSession.getId()) &&
                history.getPreviousStartTime() == null &&
                history.getNewStartTime().equals(startTime) &&
                history.getChangeReason().equals(changeReason) &&
                history.getChangedBy().equals(changedBy)
        ));
    }

    /**
     * Test: Unassign timing from session
     * AC5: Organizer can remove timing assignment
     */
    @Test
    void should_unassignTiming_when_sessionHasTiming() {
        // Given: Session with timing assigned
        String sessionSlug = "jane-smith-datainc";
        String changedBy = "test-organizer@batbern.ch";

        when(sessionRepository.findBySessionSlug(sessionSlug)).thenReturn(Optional.of(assignedSession));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: unassignTiming(sessionSlug)
        Session result = sessionTimingService.unassignTiming(sessionSlug, changedBy);

        // Then: startTime, endTime, room set to null
        assertThat(result.getStartTime()).isNull();
        assertThat(result.getEndTime()).isNull();
        assertThat(result.getRoom()).isNull();

        // And: History record created
        verify(sessionTimingHistoryRepository).save(argThat(history ->
                history.getSessionId().equals(assignedSession.getId()) &&
                history.getPreviousStartTime().equals(assignedSession.getStartTime()) &&
                history.getNewStartTime() == null
        ));
    }

    /**
     * Test: Get unassigned sessions (placeholder sessions)
     * AC12: Show unassigned speakers list with real-time updates
     */
    @Test
    void should_getUnassignedSessions_when_placeholderSessionsExist() {
        // Given: Event with 3 sessions, 2 placeholder, 1 assigned
        String eventCode = "BATbern997";
        List<Session> unassignedSessions = List.of(placeholderSession, Session.builder()
                .sessionSlug("bob-wilson-devops")
                .title("Bob Wilson - DevOps AG")
                .startTime(null)
                .endTime(null)
                .build());

        when(sessionRepository.findByEventCodeAndStartTimeIsNull(eventCode)).thenReturn(unassignedSessions);

        // When: getUnassignedSessions(eventCode)
        List<Session> result = sessionTimingService.getUnassignedSessions(eventCode);

        // Then: Returns 2 placeholder sessions
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyElementsOf(unassignedSessions);
    }

    /**
     * Test: Get timing history for session
     * Track all timing changes for audit trail
     */
    @Test
    void should_getTimingHistory_when_sessionHasMultipleChanges() {
        // Given: Session with 3 timing changes in history
        String sessionSlug = "john-doe-techcorp";
        List<SessionTimingHistory> historyRecords = List.of(
                SessionTimingHistory.builder()
                        .sessionId(placeholderSession.getId())
                        .changeReason("drag_drop_reassignment")
                        .build(),
                SessionTimingHistory.builder()
                        .sessionId(placeholderSession.getId())
                        .changeReason("conflict_resolution")
                        .build(),
                SessionTimingHistory.builder()
                        .sessionId(placeholderSession.getId())
                        .changeReason("initial_assignment")
                        .build()
        );

        when(sessionRepository.findBySessionSlug(sessionSlug)).thenReturn(Optional.of(placeholderSession));
        when(sessionTimingHistoryRepository.findBySessionIdOrderByChangedAtDesc(placeholderSession.getId()))
                .thenReturn(historyRecords);

        // When: getTimingHistory(sessionSlug)
        List<SessionTimingHistory> result = sessionTimingService.getTimingHistory(sessionSlug);

        // Then: Returns 3 history records ordered by changed_at DESC
        assertThat(result).hasSize(3);
        assertThat(result).containsExactlyElementsOf(historyRecords);
    }

    /**
     * Test: Throw exception when session not found
     */
    @Test
    void should_throwException_when_sessionNotFound() {
        // Given: Invalid session slug
        String invalidSlug = "non-existent-session";
        LocalDateTime startTime = LocalDateTime.parse("2025-06-15T09:00:00");
        LocalDateTime endTime = LocalDateTime.parse("2025-06-15T09:45:00");

        when(sessionRepository.findBySessionSlug(invalidSlug)).thenReturn(Optional.empty());

        // When: assignTiming(invalidSlug, ...)
        // Then: Throws SessionNotFoundException
        assertThatThrownBy(() -> sessionTimingService.assignTiming(invalidSlug, startTime, endTime, "Main Hall", "initial_assignment", "test-user"))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining(invalidSlug);
    }
}
