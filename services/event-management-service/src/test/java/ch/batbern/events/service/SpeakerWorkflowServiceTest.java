package ch.batbern.events.service;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SpeakerWorkflowService - Linear workflow with orthogonal slot assignment.
 *
 * Tests the simplified workflow model:
 * 1. Linear state progression: ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED → CONFIRMED
 * 2. Slot assignment is orthogonal (sets session.startTime, doesn't change state)
 * 3. Auto-confirmation when QUALITY_REVIEWED + slot assigned
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpeakerWorkflowService - Linear Workflow")
class SpeakerWorkflowServiceTest {

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;

    @Mock
    private SessionRepository sessionRepository;

    @InjectMocks
    private SpeakerWorkflowService speakerWorkflowService;

    private SpeakerPool testSpeaker;
    private Session testSession;
    private UUID speakerId;
    private UUID sessionId;
    private String organizerUsername;

    @BeforeEach
    void setUp() {
        speakerId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        organizerUsername = "test-organizer";

        testSpeaker = new SpeakerPool();
        testSpeaker.setId(speakerId);
        testSpeaker.setEventId(UUID.randomUUID());
        testSpeaker.setSpeakerName("Test Speaker");
        testSpeaker.setStatus(SpeakerWorkflowState.ACCEPTED);
        testSpeaker.setSessionId(sessionId);

        testSession = new Session();
        testSession.setId(sessionId);
        testSession.setEventId(testSpeaker.getEventId());
        testSession.setTitle("Test Session");
    }

    // ==================== Linear Workflow Tests ====================

    @Test
    @DisplayName("Should allow linear progression: ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED")
    void shouldAllowLinearProgression() {
        // Given: Speaker is ACCEPTED
        testSpeaker.setStatus(SpeakerWorkflowState.ACCEPTED);
        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(testSpeaker));
        when(speakerPoolRepository.save(any(SpeakerPool.class))).thenReturn(testSpeaker);

        // When: Progress to CONTENT_SUBMITTED
        assertDoesNotThrow(() ->
                speakerWorkflowService.updateSpeakerWorkflowState(
                        speakerId,
                        SpeakerWorkflowState.CONTENT_SUBMITTED,
                        organizerUsername
                )
        );

        // Then: State updated
        ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
        verify(speakerPoolRepository, atLeastOnce()).save(captor.capture());
        assertEquals(SpeakerWorkflowState.CONTENT_SUBMITTED, captor.getValue().getStatus());

        // Given: Now in CONTENT_SUBMITTED
        testSpeaker.setStatus(SpeakerWorkflowState.CONTENT_SUBMITTED);
        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(testSpeaker));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty()); // No slot yet

        // When: Progress to QUALITY_REVIEWED
        assertDoesNotThrow(() ->
                speakerWorkflowService.updateSpeakerWorkflowState(
                        speakerId,
                        SpeakerWorkflowState.QUALITY_REVIEWED,
                        organizerUsername
                )
        );

        // Then: State updated to QUALITY_REVIEWED (not CONFIRMED because no slot)
        verify(speakerPoolRepository, atLeast(2)).save(captor.capture());
        assertEquals(SpeakerWorkflowState.QUALITY_REVIEWED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Should reject SLOT_ASSIGNED state transition (slot assignment is not a state)")
    void shouldRejectSlotAssignedStateTransition() {
        // Given: Speaker is ACCEPTED
        testSpeaker.setStatus(SpeakerWorkflowState.ACCEPTED);
        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(testSpeaker));

        // When/Then: Cannot transition to SLOT_ASSIGNED (it's not a valid state in linear model)
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                speakerWorkflowService.updateSpeakerWorkflowState(
                        speakerId,
                        SpeakerWorkflowState.SLOT_ASSIGNED,
                        organizerUsername
                )
        );

        assertTrue(exception.getMessage().contains("Invalid state transition"));
    }

    // ==================== Auto-Confirmation Tests ====================

    @Test
    @DisplayName("Flow 1: Quality first, then assign slot → auto-confirm when slot assigned externally")
    void shouldAutoConfirmWhenSlotAssignedAfterQualityReview() {
        // Given: Speaker reached QUALITY_REVIEWED (no slot yet)
        testSpeaker.setStatus(SpeakerWorkflowState.CONTENT_SUBMITTED);
        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(testSpeaker));
        when(speakerPoolRepository.save(any(SpeakerPool.class))).thenReturn(testSpeaker);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));

        testSession.setStartTime(null); // No slot initially

        // When: Reach QUALITY_REVIEWED (no auto-confirm yet)
        speakerWorkflowService.updateSpeakerWorkflowState(
                speakerId,
                SpeakerWorkflowState.QUALITY_REVIEWED,
                organizerUsername
        );

        // Then: Stays at QUALITY_REVIEWED (no slot assigned)
        ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
        verify(speakerPoolRepository, times(1)).save(captor.capture());
        assertEquals(SpeakerWorkflowState.QUALITY_REVIEWED, captor.getValue().getStatus());

        // Given: Now organizer assigns slot (via SlotAssignmentService - sets session.startTime)
        testSpeaker.setStatus(SpeakerWorkflowState.QUALITY_REVIEWED);
        testSession.setStartTime(Instant.now()); // Slot now assigned
        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(testSpeaker));

        // When: Organizer manually triggers confirmation by calling the workflow service
        // Note: In practice, this would be triggered after slot assignment
        speakerWorkflowService.updateSpeakerWorkflowState(
                speakerId,
                SpeakerWorkflowState.QUALITY_REVIEWED, // Idempotent call
                organizerUsername
        );

        // Then: Auto-confirms (quality done + slot assigned)
        verify(speakerPoolRepository, atLeast(2)).save(captor.capture());
        SpeakerPool finalState = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(SpeakerWorkflowState.CONFIRMED, finalState.getStatus());
    }

    @Test
    @DisplayName("Flow 2: Assign slot first, then quality review → auto-confirm on quality review")
    void shouldAutoConfirmWhenQualityReviewCompletesWithSlotAlreadyAssigned() {
        // Given: Speaker is CONTENT_SUBMITTED and already has slot assigned
        testSpeaker.setStatus(SpeakerWorkflowState.CONTENT_SUBMITTED);
        testSession.setStartTime(Instant.now()); // Slot assigned early

        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(testSpeaker));
        when(speakerPoolRepository.save(any(SpeakerPool.class))).thenReturn(testSpeaker);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));

        // When: Quality review completes
        speakerWorkflowService.updateSpeakerWorkflowState(
                speakerId,
                SpeakerWorkflowState.QUALITY_REVIEWED,
                organizerUsername
        );

        // Then: Auto-confirms immediately (both conditions met)
        ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
        verify(speakerPoolRepository, atLeast(2)).save(captor.capture());

        SpeakerPool finalState = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(SpeakerWorkflowState.CONFIRMED, finalState.getStatus());
    }

    @Test
    @DisplayName("Flow 3: Assign slot during content review → auto-confirm on quality review")
    void shouldAutoConfirmWhenSlotAssignedDuringReview() {
        // Given: Speaker submitted content, slot assigned during review process
        testSpeaker.setStatus(SpeakerWorkflowState.CONTENT_SUBMITTED);
        testSession.setStartTime(Instant.now()); // Slot assigned during review

        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(testSpeaker));
        when(speakerPoolRepository.save(any(SpeakerPool.class))).thenReturn(testSpeaker);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));

        // When: Quality review completes
        speakerWorkflowService.updateSpeakerWorkflowState(
                speakerId,
                SpeakerWorkflowState.QUALITY_REVIEWED,
                organizerUsername
        );

        // Then: Auto-confirms (quality done + slot assigned)
        ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
        verify(speakerPoolRepository, atLeast(2)).save(captor.capture());

        SpeakerPool finalState = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(SpeakerWorkflowState.CONFIRMED, finalState.getStatus());
    }

    @Test
    @DisplayName("Should NOT auto-confirm when quality reviewed but no slot assigned")
    void shouldNotAutoConfirmWithoutSlot() {
        // Given: Speaker quality reviewed, but no slot assigned
        testSpeaker.setStatus(SpeakerWorkflowState.CONTENT_SUBMITTED);
        testSession.setStartTime(null); // No slot

        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(testSpeaker));
        when(speakerPoolRepository.save(any(SpeakerPool.class))).thenReturn(testSpeaker);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));

        // When: Quality review completes
        speakerWorkflowService.updateSpeakerWorkflowState(
                speakerId,
                SpeakerWorkflowState.QUALITY_REVIEWED,
                organizerUsername
        );

        // Then: Stays at QUALITY_REVIEWED (no auto-confirmation without slot)
        ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
        verify(speakerPoolRepository, times(1)).save(captor.capture());
        assertEquals(SpeakerWorkflowState.QUALITY_REVIEWED, captor.getValue().getStatus());
    }

    // ==================== Invalid Transition Tests ====================

    @Test
    @DisplayName("Should reject skipping states (ACCEPTED → QUALITY_REVIEWED)")
    void shouldRejectSkippingStates() {
        // Given: Speaker in ACCEPTED state
        testSpeaker.setStatus(SpeakerWorkflowState.ACCEPTED);
        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(testSpeaker));

        // When/Then: Cannot skip CONTENT_SUBMITTED
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                speakerWorkflowService.updateSpeakerWorkflowState(
                        speakerId,
                        SpeakerWorkflowState.QUALITY_REVIEWED,
                        organizerUsername
                )
        );

        assertTrue(exception.getMessage().contains("Invalid state transition"));
    }

    @Test
    @DisplayName("Should reject transitions from terminal states")
    void shouldRejectTransitionFromConfirmed() {
        // Given: Speaker is CONFIRMED (terminal state)
        testSpeaker.setStatus(SpeakerWorkflowState.CONFIRMED);
        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(testSpeaker));

        // When/Then: Cannot transition from CONFIRMED
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                speakerWorkflowService.updateSpeakerWorkflowState(
                        speakerId,
                        SpeakerWorkflowState.QUALITY_REVIEWED,
                        organizerUsername
                )
        );

        assertTrue(exception.getMessage().contains("Invalid state transition"));
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle missing session gracefully (no auto-confirmation)")
    void shouldHandleMissingSessionGracefully() {
        // Given: Speaker quality reviewed, but session doesn't exist
        testSpeaker.setStatus(SpeakerWorkflowState.CONTENT_SUBMITTED);
        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(testSpeaker));
        when(speakerPoolRepository.save(any(SpeakerPool.class))).thenReturn(testSpeaker);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        // When: Quality review completes
        assertDoesNotThrow(() ->
                speakerWorkflowService.updateSpeakerWorkflowState(
                        speakerId,
                        SpeakerWorkflowState.QUALITY_REVIEWED,
                        organizerUsername
                )
        );

        // Then: No error, stays at QUALITY_REVIEWED
        ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
        verify(speakerPoolRepository, times(1)).save(captor.capture());
        assertEquals(SpeakerWorkflowState.QUALITY_REVIEWED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Should handle null sessionId gracefully")
    void shouldHandleNullSessionIdGracefully() {
        // Given: Speaker has no session assigned yet
        testSpeaker.setSessionId(null);
        testSpeaker.setStatus(SpeakerWorkflowState.CONTENT_SUBMITTED);

        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(testSpeaker));
        when(speakerPoolRepository.save(any(SpeakerPool.class))).thenReturn(testSpeaker);

        // When: Quality review completes
        assertDoesNotThrow(() ->
                speakerWorkflowService.updateSpeakerWorkflowState(
                        speakerId,
                        SpeakerWorkflowState.QUALITY_REVIEWED,
                        organizerUsername
                )
        );

        // Then: No error, stays at QUALITY_REVIEWED (no auto-confirmation without session)
        ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
        verify(speakerPoolRepository, times(1)).save(captor.capture());
        assertEquals(SpeakerWorkflowState.QUALITY_REVIEWED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Should allow idempotent state transitions")
    void shouldAllowIdempotentTransitions() {
        // Given: Speaker is QUALITY_REVIEWED
        testSpeaker.setStatus(SpeakerWorkflowState.QUALITY_REVIEWED);
        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(testSpeaker));
        when(speakerPoolRepository.save(any(SpeakerPool.class))).thenReturn(testSpeaker);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        // When: Transition to same state
        assertDoesNotThrow(() ->
                speakerWorkflowService.updateSpeakerWorkflowState(
                        speakerId,
                        SpeakerWorkflowState.QUALITY_REVIEWED,
                        organizerUsername
                )
        );

        // Then: No error (idempotent)
        verify(speakerPoolRepository, atLeastOnce()).save(any(SpeakerPool.class));
    }
}
