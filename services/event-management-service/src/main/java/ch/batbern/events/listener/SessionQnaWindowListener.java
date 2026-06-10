package ch.batbern.events.listener;

import ch.batbern.events.service.SessionQnaService;
import ch.batbern.shared.events.EventWorkflowTransitionEvent;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Opens per-session Q&A windows when an event reaches {@code EVENT_COMPLETED} (Story 7.5, AC1).
 *
 * <p>Fires for BOTH the scheduled completion ({@code EventWorkflowScheduledService}) and a manual
 * organizer transition — they both publish {@link EventWorkflowTransitionEvent}. Window creation is
 * idempotent ({@code SessionQnaService.openWindowsForCompletedEvent}), and — like the sibling task
 * listener — failures are logged but never break the state transition.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SessionQnaWindowListener {

    private final SessionQnaService sessionQnaService;

    @EventListener
    public void handleWorkflowTransition(EventWorkflowTransitionEvent event) {
        if (event.getToState() != EventWorkflowState.EVENT_COMPLETED) {
            return;
        }
        try {
            sessionQnaService.openWindowsForCompletedEvent(event.getEventCode());
        } catch (Exception e) {
            log.error("Failed to open Q&A windows for completed event {}: {}",
                    event.getEventCode(), e.getMessage(), e);
        }
    }
}
