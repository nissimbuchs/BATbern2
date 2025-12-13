package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.exception.InvalidStateTransitionException;
import ch.batbern.events.exception.WorkflowValidationException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.events.EventWorkflowTransitionEvent;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EventWorkflowStateMachine - Story 5.1a AC4-11 (RED Phase)
 *
 * Test Strategy: TDD Red-Green-Refactor
 * - These tests are written BEFORE implementation (RED Phase)
 * - Tests should FAIL initially because EventWorkflowStateMachine doesn't exist yet
 * - Implementation in Task 4 will make these tests pass (GREEN Phase)
 *
 * Coverage Requirements:
 * - >90% line coverage for state machine logic
 * - 100% branch coverage for validation methods
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventWorkflowStateMachine Unit Tests")
class EventWorkflowStateMachineTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private WorkflowTransitionValidator transitionValidator;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private EventWorkflowStateMachine stateMachine;

    private Event testEvent;
    private UUID eventId;
    private String eventCode;
    private String organizerUsername;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        eventCode = "BATbern56";
        organizerUsername = "john.doe";

        testEvent = Event.builder()
                .id(eventId)
                .eventCode(eventCode)
                .title("Test Event")
                .workflowState(EventWorkflowState.CREATED)
                .organizerUsername(organizerUsername)
                .build();
    }

    // Test 2.1: AC4 - should_transitionSuccessfully_when_validStateTransition_attempted
    @Test
    @DisplayName("Test 2.1: Should transition successfully from CREATED to TOPIC_SELECTION")
    void should_transitionSuccessfully_when_validStateTransition_attempted() {
        // Given: Event in CREATED state
        when(eventRepository.findByEventCode(eventCode)).thenReturn(Optional.of(testEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: Transition to TOPIC_SELECTION
        Event result = stateMachine.transitionToState(eventCode, EventWorkflowState.TOPIC_SELECTION, organizerUsername);

        // Then: State updated successfully
        assertThat(result.getWorkflowState()).isEqualTo(EventWorkflowState.TOPIC_SELECTION);
        assertThat(result.getUpdatedBy()).isEqualTo(organizerUsername);
        assertThat(result.getUpdatedAt()).isNotNull();

        // Verify transition validation was called
        verify(transitionValidator).validateTransition(
                eq(EventWorkflowState.CREATED),
                eq(EventWorkflowState.TOPIC_SELECTION),
                any(Event.class)
        );

        // Verify event was saved
        verify(eventRepository).save(testEvent);

        // Verify domain event was published
        ArgumentCaptor<EventWorkflowTransitionEvent> eventCaptor = ArgumentCaptor.forClass(EventWorkflowTransitionEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        EventWorkflowTransitionEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getEventCode()).isEqualTo(eventCode);
        assertThat(publishedEvent.getFromState()).isEqualTo(EventWorkflowState.CREATED);
        assertThat(publishedEvent.getToState()).isEqualTo(EventWorkflowState.TOPIC_SELECTION);
        assertThat(publishedEvent.getOrganizerUsername()).isEqualTo(organizerUsername);
    }

    // Test 2.2: AC6 - should_throwWorkflowException_when_invalidStateTransition_attempted
    @Test
    @DisplayName("Test 2.2: Should throw exception for invalid state transition (CREATED → ARCHIVED)")
    void should_throwWorkflowException_when_invalidStateTransition_attempted() {
        // Given: Event in CREATED state attempting invalid transition to ARCHIVED
        when(eventRepository.findByEventCode(eventCode)).thenReturn(Optional.of(testEvent));
        doThrow(new InvalidStateTransitionException(EventWorkflowState.CREATED, EventWorkflowState.ARCHIVED))
                .when(transitionValidator).validateTransition(any(), any(), any());

        // When/Then: Transition should fail
        assertThatThrownBy(() ->
                stateMachine.transitionToState(eventCode, EventWorkflowState.ARCHIVED, organizerUsername)
        )
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Invalid transition from CREATED to ARCHIVED");

        // Verify event was NOT saved
        verify(eventRepository, never()).save(any(Event.class));

        // Verify domain event was NOT published
        verify(eventPublisher, never()).publish(any());
    }

    // Test 2.3: AC5 - should_throwValidationException_when_insufficientSpeakers_forTransition
    @Test
    @DisplayName("Test 2.3: Should throw validation exception when insufficient speakers for SPEAKER_OUTREACH")
    void should_throwValidationException_when_insufficientSpeakers_forTransition() {
        // Given: Event with insufficient speakers (need 6, have 3)
        testEvent.setWorkflowState(EventWorkflowState.SPEAKER_BRAINSTORMING);
        when(eventRepository.findByEventCode(eventCode)).thenReturn(Optional.of(testEvent));

        // When/Then: Transition to SPEAKER_OUTREACH should fail
        assertThatThrownBy(() ->
                stateMachine.transitionToState(eventCode, EventWorkflowState.SPEAKER_OUTREACH, organizerUsername)
        )
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("Insufficient speakers identified");

        // Verify event was NOT saved
        verify(eventRepository, never()).save(any(Event.class));

        // Verify domain event was NOT published
        verify(eventPublisher, never()).publish(any());
    }

    // Test 2.4: AC5 - should_throwValidationException_when_contentNotSubmitted_forQualityReview
    @Test
    @DisplayName("Test 2.4: Should throw validation exception when content not submitted for QUALITY_REVIEW")
    void should_throwValidationException_when_contentNotSubmitted_forQualityReview() {
        // Given: Event without all content submitted
        testEvent.setWorkflowState(EventWorkflowState.CONTENT_COLLECTION);
        when(eventRepository.findByEventCode(eventCode)).thenReturn(Optional.of(testEvent));

        // When/Then: Transition to QUALITY_REVIEW should fail
        assertThatThrownBy(() ->
                stateMachine.transitionToState(eventCode, EventWorkflowState.QUALITY_REVIEW, organizerUsername)
        )
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("Not all content submitted");

        // Verify event was NOT saved
        verify(eventRepository, never()).save(any(Event.class));
    }

    // Test 2.5: AC5 - should_throwValidationException_when_thresholdNotMet_forSlotAssignment
    @Test
    @DisplayName("Test 2.5: Should throw validation exception when threshold not met for SLOT_ASSIGNMENT")
    void should_throwValidationException_when_thresholdNotMet_forSlotAssignment() {
        // Given: Event that hasn't met minimum speaker threshold
        testEvent.setWorkflowState(EventWorkflowState.THRESHOLD_CHECK);
        when(eventRepository.findByEventCode(eventCode)).thenReturn(Optional.of(testEvent));

        // When/Then: Transition to SLOT_ASSIGNMENT should fail
        assertThatThrownBy(() ->
                stateMachine.transitionToState(eventCode, EventWorkflowState.SLOT_ASSIGNMENT, organizerUsername)
        )
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("Minimum threshold not met");

        // Verify event was NOT saved
        verify(eventRepository, never()).save(any(Event.class));
    }

    // Test 2.6: AC5 - should_throwValidationException_when_slotsNotAssigned_forAgendaFinalization
    @Test
    @DisplayName("Test 2.6: Should throw validation exception when slots not assigned for AGENDA_FINALIZED")
    void should_throwValidationException_when_slotsNotAssigned_forAgendaFinalization() {
        // Given: Event with unassigned slots
        testEvent.setWorkflowState(EventWorkflowState.AGENDA_PUBLISHED);
        when(eventRepository.findByEventCode(eventCode)).thenReturn(Optional.of(testEvent));

        // When/Then: Transition to AGENDA_FINALIZED should fail
        assertThatThrownBy(() ->
                stateMachine.transitionToState(eventCode, EventWorkflowState.AGENDA_FINALIZED, organizerUsername)
        )
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("Not all slots assigned");

        // Verify event was NOT saved
        verify(eventRepository, never()).save(any(Event.class));
    }

    // Test 2.7: AC4 - should_publishDomainEvent_when_successfulStateTransition_occurs
    @Test
    @DisplayName("Test 2.7: Should publish domain event on successful state transition")
    void should_publishDomainEvent_when_successfulStateTransition_occurs() {
        // Given: Valid state transition
        when(eventRepository.findByEventCode(eventCode)).thenReturn(Optional.of(testEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: Transition occurs
        stateMachine.transitionToState(eventCode, EventWorkflowState.TOPIC_SELECTION, organizerUsername);

        // Then: Domain event published
        ArgumentCaptor<EventWorkflowTransitionEvent> eventCaptor = ArgumentCaptor.forClass(EventWorkflowTransitionEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        EventWorkflowTransitionEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getEventCode()).isEqualTo(eventCode);
        assertThat(publishedEvent.getFromState()).isEqualTo(EventWorkflowState.CREATED);
        assertThat(publishedEvent.getToState()).isEqualTo(EventWorkflowState.TOPIC_SELECTION);
        assertThat(publishedEvent.getOrganizerUsername()).isEqualTo(organizerUsername);
        assertThat(publishedEvent.getTransitionedAt()).isNotNull();
        assertThat(publishedEvent.getContext()).containsKeys("eventCode", "fromState", "toState", "organizer");
    }

    // Test 2.8: Event not found scenario
    @Test
    @DisplayName("Should throw exception when event not found")
    void should_throwException_when_eventNotFound() {
        // Given: Event doesn't exist
        when(eventRepository.findByEventCode(eventCode)).thenReturn(Optional.empty());

        // When/Then: Should throw exception
        assertThatThrownBy(() ->
                stateMachine.transitionToState(eventCode, EventWorkflowState.TOPIC_SELECTION, organizerUsername)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event not found");

        // Verify no save or publish occurred
        verify(eventRepository, never()).save(any(Event.class));
        verify(eventPublisher, never()).publish(any());
    }

    // Test: Multiple sequential transitions
    @Test
    @DisplayName("Should handle multiple sequential state transitions")
    void should_handleMultipleTransitions_when_sequentiallyExecuted() {
        // Given: Event starting in CREATED state
        when(eventRepository.findByEventCode(eventCode)).thenReturn(Optional.of(testEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: First transition to TOPIC_SELECTION
        Event afterFirst = stateMachine.transitionToState(eventCode, EventWorkflowState.TOPIC_SELECTION, organizerUsername);
        assertThat(afterFirst.getWorkflowState()).isEqualTo(EventWorkflowState.TOPIC_SELECTION);

        // Update mock to return updated state
        testEvent.setWorkflowState(EventWorkflowState.TOPIC_SELECTION);

        // When: Second transition to SPEAKER_BRAINSTORMING
        Event afterSecond = stateMachine.transitionToState(eventCode, EventWorkflowState.SPEAKER_BRAINSTORMING, organizerUsername);

        // Then: Both transitions successful
        assertThat(afterSecond.getWorkflowState()).isEqualTo(EventWorkflowState.SPEAKER_BRAINSTORMING);

        // Verify two domain events published (once per transition)
        verify(eventPublisher, times(2)).publish(any(EventWorkflowTransitionEvent.class));
    }
}
