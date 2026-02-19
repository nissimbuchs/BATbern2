package ch.batbern.events.watch;

import ch.batbern.events.AbstractIntegrationTest;
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
 * Integration tests for WatchSessionService.extendSession().
 * W4.3 Task 8.2 (AC2, AC7): extend + downstream cascade + idempotency + not-found.
 *
 * Uses real PostgreSQL via Testcontainers (AbstractIntegrationTest).
 * SimpMessagingTemplate is mocked to prevent WebSocket broadcast from failing.
 */
@Transactional
class WatchExtendSessionTest extends AbstractIntegrationTest {

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

    // MARK: - Task 8.2: extendSession extends scheduledEndTime + cascades downstream

    @Test
    @DisplayName("should_extendEndTimeAndShiftDownstream_when_extendSession")
    void should_extendEndTimeAndShiftDownstream_when_extendSession() {
        Event event = saveEvent("BATbern60", 9010);
        Instant baseTime = Instant.parse("2026-02-14T18:00:00Z");

        // Session A: 18:00 – 18:45 (ACTIVE — being extended)
        Session sessionA = saveSession(event.getId(), "BATbern60", "cloud-native-pitfalls",
                baseTime, baseTime.plus(45, ChronoUnit.MINUTES));
        sessionA.setActualStartTime(baseTime);
        sessionRepository.save(sessionA);

        // Session B: 18:49 – 19:34 (downstream)
        saveSession(event.getId(), "BATbern60", "microservices-mistakes",
                baseTime.plus(49, ChronoUnit.MINUTES), baseTime.plus(94, ChronoUnit.MINUTES));

        // Session C: 19:38 – 20:23 (downstream)
        saveSession(event.getId(), "BATbern60", "event-driven-arch",
                baseTime.plus(98, ChronoUnit.MINUTES), baseTime.plus(143, ChronoUnit.MINUTES));

        watchSessionService.extendSession("BATbern60", "cloud-native-pitfalls", 10, "marco.organizer");

        Session updatedA = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern60", "cloud-native-pitfalls").orElseThrow();
        Session updatedB = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern60", "microservices-mistakes").orElseThrow();
        Session updatedC = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern60", "event-driven-arch").orElseThrow();

        // Session A: endTime extended by 10 min (18:45 → 18:55)
        assertThat(updatedA.getEndTime()).isEqualTo(baseTime.plus(55, ChronoUnit.MINUTES));
        // Session A: startTime unchanged
        assertThat(updatedA.getStartTime()).isEqualTo(baseTime);

        // Session B: shifted +10 min (18:49→18:59, 19:34→19:44)
        assertThat(updatedB.getStartTime()).isEqualTo(baseTime.plus(59, ChronoUnit.MINUTES));
        assertThat(updatedB.getEndTime()).isEqualTo(baseTime.plus(104, ChronoUnit.MINUTES));

        // Session C: shifted +10 min (19:38→19:48, 20:23→20:33)
        assertThat(updatedC.getStartTime()).isEqualTo(baseTime.plus(108, ChronoUnit.MINUTES));
        assertThat(updatedC.getEndTime()).isEqualTo(baseTime.plus(153, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("should_beIdempotent_when_extendSessionOnCompletedSession")
    void should_beIdempotent_when_extendSessionOnCompletedSession() {
        Event event = saveEvent("BATbern61", 9011);
        Instant baseTime = Instant.parse("2026-02-14T18:00:00Z");

        Session session = saveSession(event.getId(), "BATbern61", "completed-talk",
                baseTime, baseTime.plus(45, ChronoUnit.MINUTES));
        session.setActualStartTime(baseTime);
        session.setActualEndTime(baseTime.plus(47, ChronoUnit.MINUTES));
        session.setCompletedByUsername("alice.organizer");
        sessionRepository.save(session);

        // Extend on already-completed session → idempotent, no write
        watchSessionService.extendSession("BATbern61", "completed-talk", 10, "marco.organizer");

        Session updated = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern61", "completed-talk").orElseThrow();
        // End time unchanged — already completed
        assertThat(updated.getEndTime()).isEqualTo(baseTime.plus(45, ChronoUnit.MINUTES));
        assertThat(updated.getCompletedByUsername()).isEqualTo("alice.organizer");
    }

    @Test
    @DisplayName("should_throwSessionNotFoundException_when_extendUnknownSlug")
    void should_throwSessionNotFoundException_when_extendUnknownSlug() {
        assertThatThrownBy(() ->
                watchSessionService.extendSession("BATbern60", "nonexistent-session", 10, "marco.organizer")
        ).isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining("nonexistent-session");
    }

    @Test
    @DisplayName("should_onlyShiftSessionsAfterOldEndTime_when_extendSession")
    void should_onlyShiftSessionsAfterOldEndTime_when_extendSession() {
        Event event = saveEvent("BATbern62", 9012);
        Instant baseTime = Instant.parse("2026-02-14T17:00:00Z");

        // Session A: 17:00 – 17:45 (already completed, BEFORE the session being extended)
        Session sessionA = saveSession(event.getId(), "BATbern62", "early-talk",
                baseTime, baseTime.plus(45, ChronoUnit.MINUTES));
        sessionA.setActualStartTime(baseTime);
        sessionA.setActualEndTime(baseTime.plus(44, ChronoUnit.MINUTES));
        sessionA.setCompletedByUsername("alice.organizer");
        sessionRepository.save(sessionA);

        // Session B: 17:49 – 18:34 (ACTIVE — being extended)
        Session sessionB = saveSession(event.getId(), "BATbern62", "current-talk",
                baseTime.plus(49, ChronoUnit.MINUTES), baseTime.plus(94, ChronoUnit.MINUTES));
        sessionB.setActualStartTime(baseTime.plus(49, ChronoUnit.MINUTES));
        sessionRepository.save(sessionB);

        // Session C: 18:38 – 19:23 (downstream — should shift)
        saveSession(event.getId(), "BATbern62", "next-talk",
                baseTime.plus(98, ChronoUnit.MINUTES), baseTime.plus(143, ChronoUnit.MINUTES));

        watchSessionService.extendSession("BATbern62", "current-talk", 5, "marco.organizer");

        Session updatedA = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern62", "early-talk").orElseThrow();
        Session updatedC = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern62", "next-talk").orElseThrow();

        // Session A (before current): NOT shifted
        assertThat(updatedA.getStartTime()).isEqualTo(baseTime);
        assertThat(updatedA.getEndTime()).isEqualTo(baseTime.plus(45, ChronoUnit.MINUTES));

        // Session C (after current): shifted +5 min
        assertThat(updatedC.getStartTime()).isEqualTo(baseTime.plus(103, ChronoUnit.MINUTES));
        assertThat(updatedC.getEndTime()).isEqualTo(baseTime.plus(148, ChronoUnit.MINUTES));
    }
}
