package ch.batbern.events.listener;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.EventWorkflowStateMachine;
import ch.batbern.shared.events.SpeakerAcceptedEvent;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Listener for SpeakerAcceptedEvent to automatically transition events to SLOT_ASSIGNMENT state.
 *
 * This listener implements the workflow automation logic from the workflow systems reconciliation plan:
 * - When a speaker accepts and the minimum speaker threshold is met (1+ accepted speakers),
 *   automatically transition from SPEAKER_IDENTIFICATION to SLOT_ASSIGNMENT state.
 *
 * This eliminates manual state transitions and ensures the workflow accurately reflects
 * that enough speakers have been secured to begin slot assignment.
 *
 * @see SpeakerAcceptedEvent
 * @see EventWorkflowState#SLOT_ASSIGNMENT
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SpeakerAcceptedEventListener {

    private final EventRepository eventRepository;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventWorkflowStateMachine workflowStateMachine;

    /**
     * Handle speaker accepted events.
     *
     * Automatically transitions events from SPEAKER_IDENTIFICATION to SLOT_ASSIGNMENT
     * when the minimum speaker threshold is met (1+ speakers in ACCEPTED or later states).
     *
     * This method is:
     * - Asynchronous: Runs in a separate thread to avoid blocking the speaker status update API
     * - Non-blocking: Exceptions are logged but don't break the speaker status update
     * - Idempotent: Safe to call multiple times (transition validation prevents invalid moves)
     * - Transactional: Runs in a new transaction (REQUIRES_NEW) to ensure state persistence
     *
     * @param event the speaker accepted event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSpeakerAccepted(SpeakerAcceptedEvent event) {
        try {
            log.info("Received SpeakerAcceptedEvent for event {} (speaker: {})",
                    event.getEventCode(), event.getSpeakerName());

            // Find the Event entity by event code
            Event eventEntity = eventRepository.findByEventCode(event.getEventCode())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Event not found with code: " + event.getEventCode()));

            EventWorkflowState currentState = eventEntity.getWorkflowState();

            // Only transition if event is in SPEAKER_IDENTIFICATION state
            if (currentState != EventWorkflowState.SPEAKER_IDENTIFICATION) {
                log.debug("Event {} is in state {}, not SPEAKER_IDENTIFICATION. Skipping auto-transition.",
                        event.getEventCode(), currentState);
                return;
            }

            // Check if minimum speaker threshold is met (1+ speakers in ACCEPTED or later states)
            List<SpeakerPool> speakers = speakerPoolRepository.findByEventId(eventEntity.getId());
            long acceptedCount = speakers.stream()
                    .filter(sp -> isAcceptedOrLater(sp.getStatus()))
                    .count();

            if (acceptedCount < 1) {
                log.debug("Event {} has only {} accepted speakers (min: 1). Skipping auto-transition.",
                        event.getEventCode(), acceptedCount);
                return;
            }

            log.info("Auto-transitioning event {} from SPEAKER_IDENTIFICATION to SLOT_ASSIGNMENT "
                            + "(threshold met: {} accepted speakers)",
                    event.getEventCode(), acceptedCount);

            // Transition to SLOT_ASSIGNMENT
            workflowStateMachine.transitionToState(
                    event.getEventCode(),
                    EventWorkflowState.SLOT_ASSIGNMENT,
                    event.getAcceptedBy()
            );

            log.info("Successfully transitioned event {} to SLOT_ASSIGNMENT", event.getEventCode());

        } catch (Exception e) {
            // Log error but don't break the speaker acceptance flow
            // Workflow transition failures should not prevent speaker status management
            log.error("Failed to auto-transition event {} to SLOT_ASSIGNMENT after speaker acceptance: {}",
                    event.getEventCode(), e.getMessage(), e);
        }
    }

    /**
     * Check if speaker status is ACCEPTED or later in the workflow.
     *
     * States considered as "accepted or later":
     * - ACCEPTED
     * - CONTENT_SUBMITTED
     * - QUALITY_REVIEWED
     * - CONFIRMED
     *
     * @param status the speaker workflow state
     * @return true if accepted or later, false otherwise
     */
    private boolean isAcceptedOrLater(SpeakerWorkflowState status) {
        return status == SpeakerWorkflowState.ACCEPTED
               || status == SpeakerWorkflowState.CONTENT_SUBMITTED
               || status == SpeakerWorkflowState.QUALITY_REVIEWED
               || status == SpeakerWorkflowState.CONFIRMED;
    }
}
