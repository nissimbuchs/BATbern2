package ch.batbern.events.watch;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.exception.SessionNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for WatchSessionService.endSession().
 * W4.2 Task 7.4 + Task 9.2 (AC2, AC4): session state mutation + idempotency + not-found.
 *
 * Uses real PostgreSQL via Testcontainers (AbstractIntegrationTest).
 * SimpMessagingTemplate is mocked to prevent WebSocket broadcast from failing.
 */
@Transactional
class WatchSessionServiceTest extends AbstractIntegrationTest {

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
                .title("Test Event")
                .date(Instant.now().plus(30, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(20, ChronoUnit.DAYS))
                .venueName("Kornhausforum Bern")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.CREATED)
                .organizerUsername("test.organizer")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return eventRepository.save(event);
    }

    private Session saveSession(UUID eventId, String eventCode, String sessionSlug,
            Instant scheduledEndTime) {
        Session session = Session.builder()
                .eventId(eventId)
                .eventCode(eventCode)
                .sessionSlug(sessionSlug)
                .title("Test Talk")
                .sessionType("presentation")
                .startTime(Instant.now().minusSeconds(3600))
                .endTime(scheduledEndTime)
                .build();
        return sessionRepository.save(session);
    }

    // MARK: - Task 7.4: endSession sets fields correctly

    @Test
    @DisplayName("should_setActualEndTimeAndCompletedByUsername_when_endSession")
    void should_setActualEndTimeAndCompletedByUsername_when_endSession() {
        Event event = saveEvent("BATbern56", 9001);
        Instant scheduledEnd = Instant.now().minusSeconds(120); // 2 min overrun
        saveSession(event.getId(), "BATbern56", "cloud-native-pitfalls", scheduledEnd);

        watchSessionService.endSession("BATbern56", "cloud-native-pitfalls", "marco.organizer");

        Session updated = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern56", "cloud-native-pitfalls")
                .orElseThrow();
        assertThat(updated.getActualEndTime()).isNotNull();
        assertThat(updated.getCompletedByUsername()).isEqualTo("marco.organizer");
        assertThat(updated.getOverrunMinutes()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("should_setOverrunMinutesToZero_when_sessionEndedOnTime")
    void should_setOverrunMinutesToZero_when_sessionEndedOnTime() {
        Event event = saveEvent("BATbern57", 9002);
        saveSession(event.getId(), "BATbern57", "on-time-session",
                Instant.now().plusSeconds(300)); // 5 min remaining

        watchSessionService.endSession("BATbern57", "on-time-session", "marco.organizer");

        Session updated = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern57", "on-time-session")
                .orElseThrow();
        assertThat(updated.getOverrunMinutes()).isEqualTo(0);
        assertThat(updated.getCompletedByUsername()).isEqualTo("marco.organizer");
    }

    // MARK: - Task 7.4: idempotency (AC4)

    @Test
    @DisplayName("should_beIdempotent_when_endSessionCalledTwice")
    void should_beIdempotent_when_endSessionCalledTwice() {
        Event event = saveEvent("BATbern58", 9003);
        saveSession(event.getId(), "BATbern58", "microservices-intro",
                Instant.now().plusSeconds(60));

        watchSessionService.endSession("BATbern58", "microservices-intro", "marco.organizer");
        watchSessionService.endSession("BATbern58", "microservices-intro", "alice.organizer");

        // First caller's username is preserved — no second write (AC4)
        Session updated = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern58", "microservices-intro")
                .orElseThrow();
        assertThat(updated.getCompletedByUsername()).isEqualTo("marco.organizer");
    }

    // MARK: - Task 9.2: SessionNotFoundException when slug not found

    @Test
    @DisplayName("should_throwSessionNotFoundException_when_slugNotFound")
    void should_throwSessionNotFoundException_when_slugNotFound() {
        assertThatThrownBy(() ->
                watchSessionService.endSession("BATbern56", "nonexistent-session", "marco.organizer")
        ).isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining("nonexistent-session");
    }

    // MARK: - Task 9.2: findByEventCodeAndSessionSlug only targets requested session

    @Test
    @DisplayName("should_onlyUpdateTargetedSession_when_otherSessionsExistInSameEvent")
    void should_onlyUpdateTargetedSession_when_otherSessionsExistInSameEvent() {
        // Two sessions in same event — endSession only updates the targeted slug
        Event event = saveEvent("BATbern56", 9004);
        saveSession(event.getId(), "BATbern56", "cloud-native-pitfalls",
                Instant.now().minusSeconds(120));
        saveSession(event.getId(), "BATbern56", "microservices-mistakes",
                Instant.now().plusSeconds(1800));

        watchSessionService.endSession("BATbern56", "cloud-native-pitfalls", "marco.organizer");

        Session targeted = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern56", "cloud-native-pitfalls")
                .orElseThrow();
        Session untouched = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern56", "microservices-mistakes")
                .orElseThrow();

        assertThat(targeted.getCompletedByUsername()).isEqualTo("marco.organizer");
        assertThat(untouched.getCompletedByUsername()).isNull();
    }
}
