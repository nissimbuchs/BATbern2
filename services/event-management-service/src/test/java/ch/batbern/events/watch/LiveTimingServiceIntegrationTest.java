package ch.batbern.events.watch;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.LiveTimingPresenceRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.watch.dto.LiveTimingActionRequest;
import ch.batbern.events.watch.dto.LiveTimingActionRequest.LiveTimingActionType;
import ch.batbern.events.watch.dto.LiveTimingResponse;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 15.1 — integration tests for {@link LiveTimingService}.
 *
 * Covers AC1 (action recompute + version bump), AC3 (version read from the DB, presence
 * persisted with TTL), AC5 (cascade parity with the WatchSessionService path), and the
 * not-found path. Uses real PostgreSQL via Testcontainers.
 */
@Transactional
class LiveTimingServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LiveTimingService liveTimingService;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private LiveTimingPresenceRepository presenceRepository;

    /** WatchSessionService broadcasts over STOMP — no broker in tests. */
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    /** Speaker enrichment makes HTTP calls; mocked (tests here use speaker-less sessions). */
    @MockitoBean
    private UserApiClient userApiClient;

    // MARK: - Helpers

    private Event saveEvent(String eventCode, int eventNumber) {
        Event event = Event.builder()
                .eventCode(eventCode)
                .eventNumber(eventNumber)
                .title("Test Event")
                .date(Instant.now().plus(1, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(0, ChronoUnit.DAYS))
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

    // MARK: - GET snapshot

    @Test
    @DisplayName("should_returnSnapshotWithVersionZero_when_noActionsYet")
    void should_returnSnapshotWithVersionZero_when_noActionsYet() {
        Event event = saveEvent("BATbern70", 9070);
        Instant base = Instant.parse("2026-02-14T18:00:00Z");
        saveSession(event.getId(), "BATbern70", "talk-a", base, base.plus(45, ChronoUnit.MINUTES));

        LiveTimingResponse snapshot = liveTimingService.getLiveTiming("BATbern70");

        assertThat(snapshot.eventCode()).isEqualTo("BATbern70");
        assertThat(snapshot.version()).isZero();
        assertThat(snapshot.organizerPresent()).isFalse();
        assertThat(snapshot.sessions()).hasSize(1);
        assertThat(snapshot.sessions().get(0).getSessionSlug()).isEqualTo("talk-a");
    }

    @Test
    @DisplayName("should_deriveCurrentSessionSlug_when_sessionActuallyStarted")
    void should_deriveCurrentSessionSlug_when_sessionActuallyStarted() {
        Event event = saveEvent("BATbern71", 9071);
        Instant base = Instant.now().minus(10, ChronoUnit.MINUTES);
        Session active = saveSession(event.getId(), "BATbern71", "active-talk",
                base, base.plus(45, ChronoUnit.MINUTES));
        active.setActualStartTime(base);
        sessionRepository.save(active);

        LiveTimingResponse snapshot = liveTimingService.getLiveTiming("BATbern71");

        assertThat(snapshot.currentSessionSlug()).isEqualTo("active-talk");
    }

    @Test
    @DisplayName("should_throwEventNotFound_when_unknownEvent")
    void should_throwEventNotFound_when_unknownEvent() {
        assertThatThrownBy(() -> liveTimingService.getLiveTiming("BATbern-nope"))
                .isInstanceOf(EventNotFoundException.class);
    }

    // MARK: - Actions bump version (AC1) + cascade parity (AC5)

    @Test
    @DisplayName("should_extendAndCascadeAndBumpVersion_when_applyExtendAction")
    void should_extendAndCascadeAndBumpVersion_when_applyExtendAction() {
        Event event = saveEvent("BATbern72", 9072);
        Instant base = Instant.parse("2026-02-14T18:00:00Z");

        Session a = saveSession(event.getId(), "BATbern72", "talk-a",
                base, base.plus(45, ChronoUnit.MINUTES));
        a.setActualStartTime(base);
        sessionRepository.save(a);
        saveSession(event.getId(), "BATbern72", "talk-b",
                base.plus(49, ChronoUnit.MINUTES), base.plus(94, ChronoUnit.MINUTES));

        LiveTimingResponse snapshot = liveTimingService.applyAction(
                "BATbern72",
                new LiveTimingActionRequest(LiveTimingActionType.EXTEND_SESSION, "talk-a", 10),
                "marco.organizer");

        // Version bumped 0 -> 1 (AC1) and visible in the returned snapshot
        assertThat(snapshot.version()).isEqualTo(1L);
        assertThat(eventRepository.findLiveTimingVersionByEventCode("BATbern72")).contains(1L);

        // Cascade parity (AC5): A end +10, B shifted +10
        Session updatedA = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern72", "talk-a").orElseThrow();
        Session updatedB = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern72", "talk-b").orElseThrow();
        assertThat(updatedA.getEndTime()).isEqualTo(base.plus(55, ChronoUnit.MINUTES));
        assertThat(updatedB.getStartTime()).isEqualTo(base.plus(59, ChronoUnit.MINUTES));
        assertThat(updatedB.getEndTime()).isEqualTo(base.plus(104, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("should_endSessionAndBumpVersion_when_applyEndAction")
    void should_endSessionAndBumpVersion_when_applyEndAction() {
        Event event = saveEvent("BATbern73", 9073);
        Instant base = Instant.now().minus(5, ChronoUnit.MINUTES);
        Session a = saveSession(event.getId(), "BATbern73", "talk-a",
                base, base.plus(45, ChronoUnit.MINUTES));
        a.setActualStartTime(base);
        sessionRepository.save(a);

        LiveTimingResponse snapshot = liveTimingService.applyAction(
                "BATbern73",
                new LiveTimingActionRequest(LiveTimingActionType.END_SESSION, "talk-a", null),
                "marco.organizer");

        assertThat(snapshot.version()).isEqualTo(1L);
        Session updated = sessionRepository
                .findByEventCodeAndSessionSlug("BATbern73", "talk-a").orElseThrow();
        assertThat(updated.getCompletedByUsername()).isEqualTo("marco.organizer");
        assertThat(updated.getActualEndTime()).isNotNull();
    }

    @Test
    @DisplayName("should_incrementVersionMonotonically_when_multipleActions")
    void should_incrementVersionMonotonically_when_multipleActions() {
        Event event = saveEvent("BATbern74", 9074);
        Instant base = Instant.parse("2026-02-14T18:00:00Z");
        Session a = saveSession(event.getId(), "BATbern74", "talk-a",
                base, base.plus(45, ChronoUnit.MINUTES));
        a.setActualStartTime(base);
        sessionRepository.save(a);

        liveTimingService.applyAction("BATbern74",
                new LiveTimingActionRequest(LiveTimingActionType.EXTEND_SESSION, "talk-a", 5),
                "marco.organizer");
        liveTimingService.applyAction("BATbern74",
                new LiveTimingActionRequest(LiveTimingActionType.EXTEND_SESSION, "talk-a", 5),
                "marco.organizer");

        // Read from the DB (AC3 — not from a per-task in-memory field)
        assertThat(eventRepository.findLiveTimingVersionByEventCode("BATbern74")).contains(2L);
    }

    // MARK: - Presence (AC3)

    @Test
    @DisplayName("should_reportOrganizerPresent_when_recentOrganizerPoll")
    void should_reportOrganizerPresent_when_recentOrganizerPoll() {
        Event event = saveEvent("BATbern75", 9075);
        saveSession(event.getId(), "BATbern75", "talk-a",
                Instant.now(), Instant.now().plus(45, ChronoUnit.MINUTES));

        liveTimingService.recordOrganizerPoll("BATbern75", "marco.organizer");

        assertThat(liveTimingService.getLiveTiming("BATbern75").organizerPresent()).isTrue();
    }

    @Test
    @DisplayName("should_reportOrganizerAbsent_when_lastSeenBeyondTtl")
    void should_reportOrganizerAbsent_when_lastSeenBeyondTtl() {
        Event event = saveEvent("BATbern76", 9076);
        saveSession(event.getId(), "BATbern76", "talk-a",
                Instant.now(), Instant.now().plus(45, ChronoUnit.MINUTES));

        // Stale heartbeat well beyond the 30s TTL
        presenceRepository.upsertPresence("BATbern76", "marco.organizer",
                Instant.now().minus(5, ChronoUnit.MINUTES));

        assertThat(liveTimingService.getLiveTiming("BATbern76").organizerPresent()).isFalse();
    }
}
