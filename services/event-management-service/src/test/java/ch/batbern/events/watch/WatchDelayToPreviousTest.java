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
 * Integration tests for WatchSessionService.delayToPreviousSession().
 * W4.3 Task 9.2 (AC4, AC7): delay + cascade + idempotency + no-previous-session.
 *
 * Uses real PostgreSQL via Testcontainers (AbstractIntegrationTest).
 * SimpMessagingTemplate is mocked to prevent WebSocket broadcast from failing.
 */
@Transactional
class WatchDelayToPreviousTest extends AbstractIntegrationTest {

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
            Instant startTime, Instant endTime) {
        Session session = Session.builder()
                .eventId(eventId)
                .eventCode(eventCode)
                .sessionSlug(sessionSlug)
                .title("Talk: " + sessionSlug)
                .sessionType("presentation")
                .startTime(startTime)
                .endTime(endTime)
                .build();
        return sessionRepository.save(session);
    }

    // MARK: - Task 9.2: delayToPreviousSession resets current + re-activates previous + cascades

    @Test
    @DisplayName("should_resetCurrentAndReactivatePrevious_when_delayToPreviousSession")
    void should_resetCurrentAndReactivatePrevious_when_delayToPreviousSession() {
        Event event = saveEvent("BATbern70", 9020);
        Instant baseTime = Instant.parse("2026-02-14T18:00:00Z");

        // Session A: 18:00 – 18:45 (previous — was completed, now being re-activated)
        Session sessionA = saveSession(event.getId(), "BATbern70", "cloud-native-pitfalls",
                baseTime, baseTime.plus(45, ChronoUnit.MINUTES));
        sessionA.setActualStartTime(baseTime);
        sessionA.setActualEndTime(baseTime.plus(44, ChronoUnit.MINUTES));
        sessionA.setCompletedByUsername("marco.organizer");
        sessionA.setOverrunMinutes(0);
        sessionRepository.save(sessionA);

        // Session B: 18:49 – 19:34 (current — was just auto-advanced, now being reset)
        Session sessionB = saveSession(event.getId(), "BATbern70", "microservices-mistakes",
                baseTime.plus(49, ChronoUnit.MINUTES), baseTime.plus(94, ChronoUnit.MINUTES));
        sessionB.setActualStartTime(baseTime.plus(49, ChronoUnit.MINUTES));
        sessionRepository.save(sessionB);

        // Session C: 19:38 – 20:23 (downstream — should shift)
        saveSession(event.getId(), "BATbern70", "event-driven-arch",
                baseTime.plus(98, ChronoUnit.MINUTES), baseTime.plus(143, ChronoUnit.MINUTES));

        watchSessionService.delayToPreviousSession(
                "BATbern70", "microservices-mistakes", 5, "marco.organizer");

        Session updatedA = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern70", "cloud-native-pitfalls").orElseThrow();
        Session updatedB = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern70", "microservices-mistakes").orElseThrow();
        Session updatedC = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern70", "event-driven-arch").orElseThrow();

        // Previous (A): re-activated — actualEndTime cleared, endTime extended +5 min
        assertThat(updatedA.getActualEndTime()).isNull();
        assertThat(updatedA.getCompletedByUsername()).isNull();
        assertThat(updatedA.getOverrunMinutes()).isNull();
        assertThat(updatedA.getEndTime()).isEqualTo(baseTime.plus(50, ChronoUnit.MINUTES));
        // actualStartTime preserved (session was running)
        assertThat(updatedA.getActualStartTime()).isEqualTo(baseTime);

        // Current (B): reset to SCHEDULED — actualStartTime cleared
        assertThat(updatedB.getActualStartTime()).isNull();
        // Shifted +5 min
        assertThat(updatedB.getStartTime()).isEqualTo(baseTime.plus(54, ChronoUnit.MINUTES));
        assertThat(updatedB.getEndTime()).isEqualTo(baseTime.plus(99, ChronoUnit.MINUTES));

        // Downstream (C): shifted +5 min
        assertThat(updatedC.getStartTime()).isEqualTo(baseTime.plus(103, ChronoUnit.MINUTES));
        assertThat(updatedC.getEndTime()).isEqualTo(baseTime.plus(148, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("should_beIdempotent_when_previousAlreadyActive")
    void should_beIdempotent_when_previousAlreadyActive() {
        Event event = saveEvent("BATbern71", 9021);
        Instant baseTime = Instant.parse("2026-02-14T18:00:00Z");

        // Session A: previous — already ACTIVE (has actualStartTime, no actualEndTime)
        Session sessionA = saveSession(event.getId(), "BATbern71", "already-active-talk",
                baseTime, baseTime.plus(50, ChronoUnit.MINUTES));
        sessionA.setActualStartTime(baseTime);
        sessionRepository.save(sessionA);

        // Session B: current — already reset to SCHEDULED (no actualStartTime)
        saveSession(event.getId(), "BATbern71", "already-reset-talk",
                baseTime.plus(54, ChronoUnit.MINUTES), baseTime.plus(99, ChronoUnit.MINUTES));

        // Second call should be idempotent — no writes
        watchSessionService.delayToPreviousSession(
                "BATbern71", "already-reset-talk", 5, "marco.organizer");

        Session updatedA = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern71", "already-active-talk").orElseThrow();
        Session updatedB = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern71", "already-reset-talk").orElseThrow();

        // End time NOT extended again (idempotent skip — previous side)
        assertThat(updatedA.getEndTime()).isEqualTo(baseTime.plus(50, ChronoUnit.MINUTES));

        // Current session NOT cascaded again (idempotent skip — downstream side)
        assertThat(updatedB.getStartTime()).isEqualTo(baseTime.plus(54, ChronoUnit.MINUTES));
        assertThat(updatedB.getEndTime()).isEqualTo(baseTime.plus(99, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("should_shiftAllSessionsForward_when_noPreviousSession")
    void should_shiftAllSessionsForward_when_noPreviousSession() {
        Event event = saveEvent("BATbern72", 9022);
        Instant baseTime = Instant.parse("2026-02-14T18:00:00Z");

        // Session A: first session (no previous)
        Session sessionA = saveSession(event.getId(), "BATbern72", "first-session-ever",
                baseTime, baseTime.plus(45, ChronoUnit.MINUTES));
        sessionA.setActualStartTime(baseTime);
        sessionRepository.save(sessionA);

        // Session B: downstream session that must also shift
        saveSession(event.getId(), "BATbern72", "second-session",
                baseTime.plus(45, ChronoUnit.MINUTES), baseTime.plus(90, ChronoUnit.MINUTES));

        // Should NOT throw — first session delay shifts everything forward
        watchSessionService.delayToPreviousSession(
                "BATbern72", "first-session-ever", 5, "marco.organizer");

        Session updatedA = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern72", "first-session-ever").orElseThrow();
        assertThat(updatedA.getStartTime())
                .isEqualTo(baseTime.plus(5, ChronoUnit.MINUTES));
        assertThat(updatedA.getEndTime())
                .isEqualTo(baseTime.plus(50, ChronoUnit.MINUTES));

        Session updatedB = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern72", "second-session").orElseThrow();
        assertThat(updatedB.getStartTime())
                .isEqualTo(baseTime.plus(50, ChronoUnit.MINUTES));
        assertThat(updatedB.getEndTime())
                .isEqualTo(baseTime.plus(95, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("should_throwSessionNotFoundException_when_currentSlugNotFound")
    void should_throwSessionNotFoundException_when_currentSlugNotFound() {
        assertThatThrownBy(() ->
                watchSessionService.delayToPreviousSession(
                        "BATbern70", "nonexistent-session", 5, "marco.organizer")
        ).isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining("nonexistent-session");
    }
}
