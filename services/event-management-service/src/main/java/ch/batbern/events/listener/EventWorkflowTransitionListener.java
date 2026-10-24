package ch.batbern.events.listener;

import ch.batbern.events.domain.Event;
import ch.batbern.events.event.SpeakersPhasePublishedEvent;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.service.EventTaskService;
import ch.batbern.shared.events.EventWorkflowTransitionEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listener for EventWorkflowTransitionEvent to automatically activate pending tasks
 * when events reach their trigger states (Story 5.5 AC23).
 *
 * When an event transitions to a new workflow state, this listener:
 * 1. Finds all pending tasks for the event that match the new state
 * 2. Activates them by changing status from "pending" to "todo"
 *
 * This enables task automation while maintaining idempotency (AC36).
 *
 * <p>It also handles {@link SpeakersPhasePublishedEvent}: tasks with the
 * {@code speakers_published} trigger activate, and superseded newsletter tasks are cancelled
 * (organizer cockpit plan T5 + T7).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventWorkflowTransitionListener {

    private final EventTaskService eventTaskService;
    private final EventRepository eventRepository;

    /**
     * Handle workflow state transition events.
     *
     * Activates pending tasks when an event reaches their trigger state.
     * Non-blocking - exceptions are logged but don't break the workflow transition.
     *
     * @param event the workflow transition event
     */
    @EventListener
    public void handleWorkflowTransition(EventWorkflowTransitionEvent event) {
        try {
            log.info("Received workflow transition event: {} -> {} for event {}",
                    event.getFromState(), event.getToState(), event.getEventCode());

            // Find the Event entity by event code
            Event eventEntity = eventRepository.findByEventCode(event.getEventCode())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Event not found with code: " + event.getEventCode()));

            // Convert EventWorkflowState enum to lowercase_snake_case (database format)
            // e.g., EventWorkflowState.TOPIC_SELECTION -> "topic_selection"
            String triggeredState = event.getToState().name().toLowerCase();

            // Activate pending tasks that match this trigger state
            eventTaskService.autoCreateTasksForState(
                    eventEntity.getId(),
                    triggeredState,
                    eventEntity.getDate()
            );

            log.debug("Successfully processed workflow transition for event {}", event.getEventCode());

        } catch (Exception e) {
            // Log error but don't break the workflow transition
            // Task activation failures should not prevent event state changes
            log.error("Failed to activate tasks for event {} transitioning to state {}: {}",
                    event.getEventCode(), event.getToState(), e.getMessage(), e);
        }
    }

    /**
     * Activate {@code speakers_published} tasks and cancel superseded newsletter tasks when the
     * speakers phase is published. Non-blocking, like the transition handler.
     *
     * @param event the speakers-phase-published signal
     */
    @EventListener
    public void handleSpeakersPhasePublished(SpeakersPhasePublishedEvent event) {
        try {
            eventTaskService.onSpeakersPhasePublished(event.eventCode());
        } catch (Exception e) {
            log.error("Failed to activate tasks for event {} after speakers publish: {}",
                    event.eventCode(), e.getMessage(), e);
        }
    }
}
