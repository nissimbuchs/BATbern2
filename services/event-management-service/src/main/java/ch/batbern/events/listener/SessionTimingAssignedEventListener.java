package ch.batbern.events.listener;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.service.EventWorkflowStateMachine;
import ch.batbern.shared.events.SessionTimingAssignedEvent;
import ch.batbern.shared.types.EventWorkflowState;
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
 * Listener for SessionTimingAssignedEvent to automatically transition events to AGENDA_PUBLISHED state.
 *
 * This listener implements the workflow automation logic from the workflow systems reconciliation plan:
 * - When all sessions have timing assigned AND the event's published phase is "speakers" or "agenda",
 *   automatically transition from SLOT_ASSIGNMENT to AGENDA_PUBLISHED state.
 *
 * This eliminates manual state transitions and ensures the workflow accurately reflects
 * that the agenda is ready for public viewing.
 *
 * @see SessionTimingAssignedEvent
 * @see EventWorkflowState#AGENDA_PUBLISHED
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SessionTimingAssignedEventListener {

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final EventWorkflowStateMachine workflowStateMachine;

    /**
     * Handle session timing assigned events.
     *
     * Automatically transitions events from SLOT_ASSIGNMENT to AGENDA_PUBLISHED
     * when all sessions have timing assigned and the published phase is "speakers" or "agenda".
     *
     * This method is:
     * - Asynchronous: Runs in a separate thread to avoid blocking the session timing assignment API
     * - Non-blocking: Exceptions are logged but don't break the session timing assignment
     * - Idempotent: Safe to call multiple times (transition validation prevents invalid moves)
     * - Transactional: Runs in a new transaction (REQUIRES_NEW) to ensure state persistence
     *
     * @param event the session timing assigned event
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSessionTimingAssigned(SessionTimingAssignedEvent event) {
        try {
            log.info("Received SessionTimingAssignedEvent for event {} (session: {})",
                    event.getEventCode(), event.getSessionTitle());

            // Find the Event entity by event code
            Event eventEntity = eventRepository.findByEventCode(event.getEventCode())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Event not found with code: " + event.getEventCode()));

            EventWorkflowState currentState = eventEntity.getWorkflowState();

            // Only transition if event is in SLOT_ASSIGNMENT state
            if (currentState != EventWorkflowState.SLOT_ASSIGNMENT) {
                log.debug("Event {} is in state {}, not SLOT_ASSIGNMENT. Skipping auto-transition.",
                        event.getEventCode(), currentState);
                return;
            }

            // Check if all sessions have timing assigned (startTime != null)
            List<Session> allSessions = sessionRepository.findByEventId(eventEntity.getId());
            if (allSessions.isEmpty()) {
                log.debug("Event {} has no sessions. Skipping auto-transition.", event.getEventCode());
                return;
            }

            long sessionsWithTiming = allSessions.stream()
                    .filter(s -> s.getStartTime() != null)
                    .count();

            if (sessionsWithTiming < allSessions.size()) {
                log.debug("Event {} has {} sessions, only {} have timing assigned. Skipping auto-transition.",
                        event.getEventCode(), allSessions.size(), sessionsWithTiming);
                return;
            }

            // Check if published phase is "speakers" or "agenda"
            String publishedPhase = eventEntity.getCurrentPublishedPhase();
            if (publishedPhase == null
                || (!publishedPhase.equals("speakers") && !publishedPhase.equals("agenda"))) {
                log.debug("Event {} published phase is '{}', not 'speakers' or 'agenda'. Skipping auto-transition.",
                        event.getEventCode(), publishedPhase);
                return;
            }

            log.info("Auto-transitioning event {} from SLOT_ASSIGNMENT to AGENDA_PUBLISHED "
                            + "(all {} sessions have timing, published phase: {})",
                    event.getEventCode(), allSessions.size(), publishedPhase);

            // Transition to AGENDA_PUBLISHED
            workflowStateMachine.transitionToState(
                    event.getEventCode(),
                    EventWorkflowState.AGENDA_PUBLISHED,
                    event.getAssignedBy()
            );

            log.info("Successfully transitioned event {} to AGENDA_PUBLISHED", event.getEventCode());

        } catch (Exception e) {
            // Log error but don't break the session timing assignment flow
            // Workflow transition failures should not prevent slot assignment
            log.error("Failed to auto-transition event {} to AGENDA_PUBLISHED after session timing assignment: {}",
                    event.getEventCode(), e.getMessage(), e);
        }
    }
}
