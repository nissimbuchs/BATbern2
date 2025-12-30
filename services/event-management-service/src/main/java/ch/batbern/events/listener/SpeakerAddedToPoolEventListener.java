package ch.batbern.events.listener;

import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.service.EventWorkflowStateMachine;
import ch.batbern.shared.events.SpeakerAddedToPoolEvent;
import ch.batbern.shared.types.EventWorkflowState;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listener for SpeakerAddedToPoolEvent to automatically transition events to SPEAKER_IDENTIFICATION state.
 *
 * This listener implements the workflow automation logic from the workflow systems reconciliation plan:
 * - When a speaker is added to the pool and the event is in CREATED or TOPIC_SELECTION state,
 *   automatically transition to SPEAKER_IDENTIFICATION state.
 *
 * This eliminates manual state transitions and ensures the workflow accurately reflects
 * that speaker identification has begun.
 *
 * @see SpeakerAddedToPoolEvent
 * @see EventWorkflowState#SPEAKER_IDENTIFICATION
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SpeakerAddedToPoolEventListener {

    private final EventRepository eventRepository;
    private final EventWorkflowStateMachine workflowStateMachine;

    /**
     * Handle speaker added to pool events.
     *
     * Automatically transitions events from CREATED or TOPIC_SELECTION to SPEAKER_IDENTIFICATION
     * when the first speaker is added to the pool.
     *
     * This method is:
     * - Asynchronous: Runs in a separate thread to avoid blocking the speaker addition API
     * - Non-blocking: Exceptions are logged but don't break the speaker addition
     * - Idempotent: Safe to call multiple times (transition validation prevents invalid moves)
     * - Transactional: Runs in a new transaction (REQUIRES_NEW) to ensure state persistence
     *
     * @param event the speaker added to pool event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSpeakerAddedToPool(SpeakerAddedToPoolEvent event) {
        try {
            log.info("Received SpeakerAddedToPoolEvent for event {} (speaker: {})",
                    event.getEventCode(), event.getSpeakerName());

            // Find the Event entity by event code
            Event eventEntity = eventRepository.findByEventCode(event.getEventCode())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Event not found with code: " + event.getEventCode()));

            EventWorkflowState currentState = eventEntity.getWorkflowState();

            // Only transition if event is in CREATED or TOPIC_SELECTION state
            if (currentState == EventWorkflowState.CREATED ||
                currentState == EventWorkflowState.TOPIC_SELECTION) {

                log.info("Auto-transitioning event {} from {} to SPEAKER_IDENTIFICATION (speaker added: {})",
                        event.getEventCode(), currentState, event.getSpeakerName());

                // Transition to SPEAKER_IDENTIFICATION
                workflowStateMachine.transitionToState(
                        event.getEventCode(),
                        EventWorkflowState.SPEAKER_IDENTIFICATION,
                        event.getAddedBy()
                );

                log.info("Successfully transitioned event {} to SPEAKER_IDENTIFICATION",
                        event.getEventCode());

            } else {
                // Event is already past speaker identification phase - no action needed
                log.debug("Event {} is already in state {}, skipping auto-transition to SPEAKER_IDENTIFICATION",
                        event.getEventCode(), currentState);
            }

        } catch (Exception e) {
            // Log error but don't break the speaker addition flow
            // Workflow transition failures should not prevent speaker pool management
            log.error("Failed to auto-transition event {} to SPEAKER_IDENTIFICATION after speaker addition: {}",
                    event.getEventCode(), e.getMessage(), e);
        }
    }
}
