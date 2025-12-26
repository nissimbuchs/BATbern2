package ch.batbern.events.service.slotassignment;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionTimingHistory;
import ch.batbern.events.exception.SessionNotFoundException;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionTimingHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Mock
    private ch.batbern.events.repository.SpeakerPoolRepository speakerPoolRepository;

    @Mock
    private ch.batbern.events.service.SpeakerWorkflowService speakerWorkflowService;

    private Session placeholderSession;
    private Session assignedSession;

    @BeforeEach
    void setUp() {
        sessionTimingService = new SessionTimingService(
                sessionRepository,
                sessionTimingHistoryRepository,
                speakerPoolRepository,
                speakerWorkflowService
        );

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
                .startTime(Instant.parse("2025-06-15T09:00:00Z"))
                .endTime(Instant.parse("2025-06-15T09:45:00Z"))
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
        Instant startTime = Instant.parse("2025-06-15T09:00:00Z");
        Instant endTime = Instant.parse("2025-06-15T09:45:00Z");
        String room = "Main Hall";
        String changeReason = "drag_drop_reassignment";
        String changedBy = "test-organizer@batbern.ch";

        when(sessionRepository.findBySessionSlug(sessionSlug)).thenReturn(Optional.of(placeholderSession));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(speakerPoolRepository.findBySessionId(any())).thenReturn(Optional.empty()); // No speaker for this session

        // When: assignTiming(sessionSlug, startTime, endTime, room, changeReason)
        Session result = sessionTimingService.assignTiming(sessionSlug, startTime, endTime, room, changeReason, changedBy);

        // Then: Session updated with timing
        assertThat(result.getStartTime()).isEqualTo(startTime);
        assertThat(result.getEndTime()).isEqualTo(endTime);
        assertThat(result.getRoom()).isEqualTo(room);

        // And: History record created in session_timing_history
        verify(sessionTimingHistoryRepository).save(argThat(history ->
                history.getSessionId().equals(placeholderSession.getId())
                && history.getPreviousStartTime() == null
                && history.getNewStartTime().equals(startTime)
                && history.getChangeReason().equals(changeReason)
                && history.getChangedBy().equals(changedBy)
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

        // Store original values before they're modified
        Instant originalStartTime = assignedSession.getStartTime();
        Instant originalEndTime = assignedSession.getEndTime();
        String originalRoom = assignedSession.getRoom();
        UUID sessionId = assignedSession.getId();

        when(sessionRepository.findBySessionSlug(sessionSlug)).thenReturn(Optional.of(assignedSession));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: unassignTiming(sessionSlug)
        Session result = sessionTimingService.unassignTiming(sessionSlug, changedBy);

        // Then: startTime, endTime, room set to null
        assertThat(result.getStartTime()).isNull();
        assertThat(result.getEndTime()).isNull();
        assertThat(result.getRoom()).isNull();

        // And: History record created with original values
        org.mockito.ArgumentCaptor<SessionTimingHistory> historyCaptor =
                org.mockito.ArgumentCaptor.forClass(SessionTimingHistory.class);
        verify(sessionTimingHistoryRepository).save(historyCaptor.capture());

        SessionTimingHistory capturedHistory = historyCaptor.getValue();
        assertThat(capturedHistory.getSessionId()).isEqualTo(sessionId);
        assertThat(capturedHistory.getPreviousStartTime()).isEqualTo(originalStartTime);
        assertThat(capturedHistory.getPreviousEndTime()).isEqualTo(originalEndTime);
        assertThat(capturedHistory.getPreviousRoom()).isEqualTo(originalRoom);
        assertThat(capturedHistory.getNewStartTime()).isNull();
        assertThat(capturedHistory.getNewEndTime()).isNull();
        assertThat(capturedHistory.getNewRoom()).isNull();
        assertThat(capturedHistory.getChangedBy()).isEqualTo(changedBy);
        assertThat(capturedHistory.getChangeReason()).isEqualTo("manual_adjustment");
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
        Instant startTime = Instant.parse("2025-06-15T09:00:00Z");
        Instant endTime = Instant.parse("2025-06-15T09:45:00Z");

        when(sessionRepository.findBySessionSlug(invalidSlug)).thenReturn(Optional.empty());

        // When: assignTiming(invalidSlug, ...)
        // Then: Throws SessionNotFoundException
        assertThatThrownBy(() -> sessionTimingService.assignTiming(invalidSlug, startTime, endTime, "Main Hall", "initial_assignment", "test-user"))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining(invalidSlug);
    }

    /**
     * Test: Auto-confirm speaker when timing assigned and speaker is quality reviewed
     * Story BAT-11 Task 6: Workflow State Machine Integration
     *
     * When a session gets timing assigned, if its speaker is already in QUALITY_REVIEWED state,
     * the speaker should be automatically transitioned to CONFIRMED state.
     */
    @Test
    void should_autoConfirmSpeaker_when_timingAssignedAndSpeakerQualityReviewed() {
        // Given: Session with a speaker in QUALITY_REVIEWED state
        UUID speakerId = UUID.randomUUID();
        ch.batbern.events.domain.SpeakerPool speaker = ch.batbern.events.domain.SpeakerPool.builder()
                .id(speakerId)
                .sessionId(placeholderSession.getId())
                .status(ch.batbern.shared.types.SpeakerWorkflowState.QUALITY_REVIEWED)
                .build();

        String sessionSlug = "john-doe-techcorp";
        Instant startTime = Instant.parse("2025-06-15T09:00:00Z");
        Instant endTime = Instant.parse("2025-06-15T09:45:00Z");
        String room = "Main Hall";
        String changedBy = "test-organizer@batbern.ch";

        when(sessionRepository.findBySessionSlug(sessionSlug)).thenReturn(Optional.of(placeholderSession));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(speakerPoolRepository.findBySessionId(placeholderSession.getId())).thenReturn(Optional.of(speaker));

        // When: assignTiming() is called
        sessionTimingService.assignTiming(sessionSlug, startTime, endTime, room, "initial_assignment", changedBy);

        // Then: Speaker workflow service should be called to transition speaker to CONFIRMED
        verify(speakerWorkflowService).updateSpeakerWorkflowState(
                eq(speakerId),
                eq(ch.batbern.shared.types.SpeakerWorkflowState.CONFIRMED),
                eq(changedBy)
        );
    }

    /**
     * Test: Do not auto-confirm speaker when timing assigned but speaker not in QUALITY_REVIEWED state
     * Story BAT-11 Task 6: Workflow State Machine Integration
     */
    @Test
    void should_notAutoConfirmSpeaker_when_timingAssignedButSpeakerNotQualityReviewed() {
        // Given: Session with a speaker in ACCEPTED state (not QUALITY_REVIEWED)
        UUID speakerId = UUID.randomUUID();
        ch.batbern.events.domain.SpeakerPool speaker = ch.batbern.events.domain.SpeakerPool.builder()
                .id(speakerId)
                .sessionId(placeholderSession.getId())
                .status(ch.batbern.shared.types.SpeakerWorkflowState.ACCEPTED)
                .build();

        String sessionSlug = "john-doe-techcorp";
        Instant startTime = Instant.parse("2025-06-15T09:00:00Z");
        Instant endTime = Instant.parse("2025-06-15T09:45:00Z");
        String room = "Main Hall";
        String changedBy = "test-organizer@batbern.ch";

        when(sessionRepository.findBySessionSlug(sessionSlug)).thenReturn(Optional.of(placeholderSession));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(speakerPoolRepository.findBySessionId(placeholderSession.getId())).thenReturn(Optional.of(speaker));

        // When: assignTiming() is called
        sessionTimingService.assignTiming(sessionSlug, startTime, endTime, room, "initial_assignment", changedBy);

        // Then: Speaker workflow service should NOT be called
        verify(speakerWorkflowService, never()).updateSpeakerWorkflowState(any(), any(), any());
    }
}
