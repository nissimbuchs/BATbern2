package ch.batbern.events.listener;

import ch.batbern.events.domain.QnaOpenTrigger;
import ch.batbern.events.event.SpeakersPhasePublishedEvent;
import ch.batbern.events.service.SessionQnaService;
import ch.batbern.shared.events.EventWorkflowTransitionEvent;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Opens per-session Q&A windows (Story 7.5 "The Apéro Continues"), at the moment configured by the
 * event's {@code qnaOpenTrigger}:
 * <ul>
 *   <li>{@code EVENT_COMPLETED} (default) — on the workflow transition to EVENT_COMPLETED.</li>
 *   <li>{@code SPEAKERS_PUBLISHED} — when the speakers publishing phase goes live.</li>
 * </ul>
 *
 * <p>Both fire for scheduled and manual paths. {@code SessionQnaService.openWindowsIfTrigger} is
 * idempotent and a no-op when the event's trigger doesn't match (so both listeners can fire freely);
 * failures are logged but never break the transition / publish.
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
            sessionQnaService.openWindowsIfTrigger(event.getEventCode(), QnaOpenTrigger.EVENT_COMPLETED);
        } catch (Exception e) {
            log.error("Failed to open Q&A windows for completed event {}: {}",
                    event.getEventCode(), e.getMessage(), e);
        }
    }

    @EventListener
    public void handleSpeakersPhasePublished(SpeakersPhasePublishedEvent event) {
        try {
            sessionQnaService.openWindowsIfTrigger(event.eventCode(), QnaOpenTrigger.SPEAKERS_PUBLISHED);
        } catch (Exception e) {
            log.error("Failed to open Q&A windows on speakers-publish for event {}: {}",
                    event.eventCode(), e.getMessage(), e);
        }
    }
}
