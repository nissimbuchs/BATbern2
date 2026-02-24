package ch.batbern.events.watch;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for W4.4 AC4: EVENT_COMPLETED auto-transition.
 *
 * Verifies that WatchSessionService.endSession() transitions the event to
 * EVENT_COMPLETED once all completeable sessions (keynote, presentation,
 * workshop, panel_discussion) have been marked done.
 *
 * Break/lunch/networking sessions are excluded from the all-complete check
 * by design — they never require an explicit organizer "Done" tap.
 *
 * Uses real PostgreSQL via Testcontainers (AbstractIntegrationTest).
 * SimpMessagingTemplate is mocked to prevent WebSocket broadcast from failing.
 */
@Transactional
class WatchEventCompletionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WatchSessionService watchSessionService;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventRepository eventRepository;

    /** Prevents STOMP broadcast from failing — no running WebSocket broker in tests. */
    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    // MARK: - Helpers

    private Event saveEvent(String eventCode, int eventNumber) {
        Event event = Event.builder()
                .eventCode(eventCode)
                .eventNumber(eventNumber)
                .title("BATbern Watch Test")
                .date(Instant.now())
                .registrationDeadline(Instant.now().minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum Bern")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.EVENT_LIVE)
                .organizerUsername("test.organizer")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return eventRepository.save(event);
    }

    private Session saveSession(UUID eventId, String eventCode, String sessionSlug,
            String sessionType) {
        Session session = Session.builder()
                .eventId(eventId)
                .eventCode(eventCode)
                .sessionSlug(sessionSlug)
                .title("Test Session")
                .sessionType(sessionType)
                .startTime(Instant.now().minusSeconds(3600))
                .endTime(Instant.now().minusSeconds(60))
                .build();
        return sessionRepository.save(session);
    }

    // MARK: - AC4: EVENT_COMPLETED transition

    @Test
    @DisplayName("should_transitionEventToCompleted_when_allCompleteableSessionsEnded")
    void should_transitionEventToCompleted_when_allCompleteableSessionsEnded() {
        Event event = saveEvent("BATw44A", 9100);
        saveSession(event.getId(), "BATw44A", "keynote-1", "keynote");
        saveSession(event.getId(), "BATw44A", "talk-1", "presentation");

        watchSessionService.endSession("BATw44A", "keynote-1", "marco.organizer");
        watchSessionService.endSession("BATw44A", "talk-1", "anna.organizer");

        Event updated = eventRepository.findByEventCode("BATw44A").orElseThrow();
        assertThat(updated.getWorkflowState()).isEqualTo(EventWorkflowState.EVENT_COMPLETED);
    }

    @Test
    @DisplayName("should_notTransitionToCompleted_when_someCompleteableSessionsStillPending")
    void should_notTransitionToCompleted_when_someCompleteableSessionsStillPending() {
        Event event = saveEvent("BATw44B", 9101);
        saveSession(event.getId(), "BATw44B", "keynote-1", "keynote");
        saveSession(event.getId(), "BATw44B", "talk-1", "presentation");

        // Only end the keynote — talk-1 is still pending
        watchSessionService.endSession("BATw44B", "keynote-1", "marco.organizer");

        Event updated = eventRepository.findByEventCode("BATw44B").orElseThrow();
        assertThat(updated.getWorkflowState()).isNotEqualTo(EventWorkflowState.EVENT_COMPLETED);
    }

    @Test
    @DisplayName("should_excludeBreakSessions_fromAllCompleteCheck")
    void should_excludeBreakSessions_fromAllCompleteCheck() {
        // One completeable session + one break session.
        // Completing only the talk should trigger EVENT_COMPLETED (break is excluded).
        Event event = saveEvent("BATw44C", 9102);
        saveSession(event.getId(), "BATw44C", "talk-1", "presentation");
        saveSession(event.getId(), "BATw44C", "lunch-break", "lunch");

        watchSessionService.endSession("BATw44C", "talk-1", "marco.organizer");

        Event updated = eventRepository.findByEventCode("BATw44C").orElseThrow();
        assertThat(updated.getWorkflowState()).isEqualTo(EventWorkflowState.EVENT_COMPLETED);
    }

    @Test
    @DisplayName("should_excludeNetworkingSession_fromAllCompleteCheck")
    void should_excludeNetworkingSession_fromAllCompleteCheck() {
        Event event = saveEvent("BATw44D", 9103);
        saveSession(event.getId(), "BATw44D", "keynote-1", "keynote");
        saveSession(event.getId(), "BATw44D", "networking-end", "networking");

        watchSessionService.endSession("BATw44D", "keynote-1", "marco.organizer");

        Event updated = eventRepository.findByEventCode("BATw44D").orElseThrow();
        assertThat(updated.getWorkflowState()).isEqualTo(EventWorkflowState.EVENT_COMPLETED);
    }

    @Test
    @DisplayName("should_includeWorkshopAndPanelDiscussion_inAllCompleteCheck")
    void should_includeWorkshopAndPanelDiscussion_inAllCompleteCheck() {
        Event event = saveEvent("BATw44E", 9104);
        saveSession(event.getId(), "BATw44E", "workshop-1", "workshop");
        saveSession(event.getId(), "BATw44E", "panel-1", "panel_discussion");

        // End only the workshop — panel is still pending → no transition
        watchSessionService.endSession("BATw44E", "workshop-1", "marco.organizer");
        Event afterWorkshop = eventRepository.findByEventCode("BATw44E").orElseThrow();
        assertThat(afterWorkshop.getWorkflowState()).isNotEqualTo(EventWorkflowState.EVENT_COMPLETED);

        // End the panel → all complete → transition
        watchSessionService.endSession("BATw44E", "panel-1", "anna.organizer");
        Event afterPanel = eventRepository.findByEventCode("BATw44E").orElseThrow();
        assertThat(afterPanel.getWorkflowState()).isEqualTo(EventWorkflowState.EVENT_COMPLETED);
    }

    @Test
    @DisplayName("should_beIdempotent_when_eventAlreadyCompletedAndAnotherSessionEnded")
    void should_beIdempotent_when_eventAlreadyCompletedAndAnotherSessionEnded() {
        // Simulate an event that is already EVENT_COMPLETED (e.g., after a server restart
        // and the idempotent endSession re-broadcast path is taken).
        Event event = saveEvent("BATw44F", 9105);
        saveSession(event.getId(), "BATw44F", "talk-1", "presentation");

        // Transition to completed
        watchSessionService.endSession("BATw44F", "talk-1", "marco.organizer");
        Event afterFirst = eventRepository.findByEventCode("BATw44F").orElseThrow();
        assertThat(afterFirst.getWorkflowState()).isEqualTo(EventWorkflowState.EVENT_COMPLETED);

        // Idempotent call — must not throw and must remain EVENT_COMPLETED
        watchSessionService.endSession("BATw44F", "talk-1", "alice.organizer");
        Event afterSecond = eventRepository.findByEventCode("BATw44F").orElseThrow();
        assertThat(afterSecond.getWorkflowState()).isEqualTo(EventWorkflowState.EVENT_COMPLETED);
    }
}
