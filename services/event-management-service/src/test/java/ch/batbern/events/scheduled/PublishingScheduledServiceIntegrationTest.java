package ch.batbern.events.scheduled;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.PublishingConfig;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.core.dto.generated.PatchPublishingRequest;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.PublishingConfigRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.publishing.PublishingService;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PublishingScheduledService
 * Story BAT-16 (AC5): Scheduled Auto-Publishing
 *
 * Tests:
 * - Auto-publish speakers phase 30 days before event (default) or on the stored day (4.9b)
 * - Auto-publish agenda phase 14 days before event (default) or on the stored day (4.9b)
 * - Respect auto-publish configuration flags
 * - Skip events without complete timing
 */
@SpringBootTest
@Transactional
public class PublishingScheduledServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PublishingScheduledService publishingScheduledService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private PublishingConfigRepository publishingConfigRepository;

    @Autowired
    private PublishingService publishingService;

    // Use timestamp-based sequence to ensure uniqueness across test runs
    private int eventNumberSequence = (int) (System.currentTimeMillis() % 50000) + 50000;

    @BeforeEach
    void setUp() {
        // Each test uses unique event numbers to avoid constraint violations
        eventNumberSequence += 1;
    }

    /**
     * AC5: Auto-publish speakers phase when event is 30 days away
     */
    @Test
    void should_autoPublishSpeakers_when_eventIsThirtyDaysAway() {
        // Given: Event happening 30 days from now with no published phase
        Instant eventDate = LocalDate.now().plusDays(30)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        Event event = createEvent("BAT-SPEAKERS-30", eventDate, null);
        createAcceptedSpeaker(event);

        // When: Auto-publish job runs
        publishingScheduledService.autoPublishSpeakers();

        // Then: Speakers phase should be published
        Event updated = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getCurrentPublishedPhase()).isEqualTo("speakers");
        assertThat(updated.getLastPublishedAt()).isNotNull();
    }

    /**
     * AC5: Should not auto-publish speakers if already published
     */
    @Test
    void should_notAutoPublishSpeakers_when_alreadyPublished() {
        // Given: Event 30 days away with speakers already published
        Instant eventDate = LocalDate.now().plusDays(30)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        Event event = createEvent("BAT-SPEAKERS-DONE", eventDate, "speakers");
        Instant originalPublishTime = event.getLastPublishedAt();

        // When: Auto-publish job runs
        publishingScheduledService.autoPublishSpeakers();

        // Then: Should not re-publish (phase remains speakers, timestamp unchanged)
        Event updated = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getCurrentPublishedPhase()).isEqualTo("speakers");
        assertThat(updated.getLastPublishedAt()).isEqualTo(originalPublishTime);
    }

    /**
     * AC5: Respect auto-publish configuration (disabled)
     */
    @Test
    void should_notAutoPublishSpeakers_when_autoPublishDisabled() {
        // Given: Event 30 days away with auto-publish disabled
        Instant eventDate = LocalDate.now().plusDays(30)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        Event event = createEvent("BAT-SPEAKERS-DISABLED", eventDate, null);
        createAcceptedSpeaker(event);

        // Disable auto-publish
        PublishingConfig config = PublishingConfig.builder()
                .eventId(event.getId())
                .autoPublishSpeakers(false)
                .build();
        publishingConfigRepository.save(config);

        // When: Auto-publish job runs
        publishingScheduledService.autoPublishSpeakers();

        // Then: Should not publish
        Event updated = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getCurrentPublishedPhase()).isNull();
    }

    /**
     * AC5: Auto-publish agenda phase when event is 14 days away
     */
    @Test
    void should_autoPublishAgenda_when_eventIsFourteenDaysAwayAndTimingComplete() {
        // Given: Event 14 days away with speakers published and all sessions timed
        Instant eventDate = LocalDate.now().plusDays(14)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        Event event = createEvent("BAT-AGENDA-14", eventDate, "speakers");
        createSessionWithTiming(event, eventDate);

        // When: Auto-publish job runs
        publishingScheduledService.autoPublishAgenda();

        // Then: Agenda phase should be published
        Event updated = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getCurrentPublishedPhase()).isEqualTo("agenda");
        assertThat(updated.getWorkflowState()).isEqualTo(EventWorkflowState.AGENDA_PUBLISHED);
    }

    /**
     * AC5: Should not auto-publish agenda if sessions lack timing
     */
    @Test
    void should_notAutoPublishAgenda_when_sessionsLackTiming() {
        // Given: Event 14 days away with speakers published but sessions without timing
        Instant eventDate = LocalDate.now().plusDays(14)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        Event event = createEvent("BAT-AGENDA-NO-TIMING", eventDate, "speakers");
        createSessionWithoutTiming(event);

        // When: Auto-publish job runs
        publishingScheduledService.autoPublishAgenda();

        // Then: Should not publish agenda
        Event updated = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getCurrentPublishedPhase()).isEqualTo("speakers");
        assertThat(updated.getWorkflowState()).isNotEqualTo(EventWorkflowState.AGENDA_PUBLISHED);
    }

    /**
     * AC5: Should not auto-publish agenda if speakers not yet published
     */
    @Test
    void should_notAutoPublishAgenda_when_speakersNotPublished() {
        // Given: Event 14 days away with topic phase (speakers not published)
        Instant eventDate = LocalDate.now().plusDays(14)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        Event event = createEvent("BAT-AGENDA-NO-SPEAKERS", eventDate, "topic");
        createSessionWithTiming(event, eventDate);

        // When: Auto-publish job runs
        publishingScheduledService.autoPublishAgenda();

        // Then: Should not publish agenda
        Event updated = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getCurrentPublishedPhase()).isEqualTo("topic");
    }

    /**
     * AC5: Respect auto-publish configuration for agenda
     */
    @Test
    void should_notAutoPublishAgenda_when_autoPublishDisabled() {
        // Given: Event 14 days away with auto-publish agenda disabled
        Instant eventDate = LocalDate.now().plusDays(14)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        Event event = createEvent("BAT-AGENDA-DISABLED", eventDate, "speakers");
        createSessionWithTiming(event, eventDate);

        // Disable auto-publish agenda
        PublishingConfig config = PublishingConfig.builder()
                .eventId(event.getId())
                .autoPublishAgenda(false)
                .build();
        publishingConfigRepository.save(config);

        // When: Auto-publish job runs
        publishingScheduledService.autoPublishAgenda();

        // Then: Should not publish
        Event updated = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getCurrentPublishedPhase()).isEqualTo("speakers");
    }

    /**
     * AC5: Should handle events with no sessions (skip agenda publish)
     */
    @Test
    void should_notAutoPublishAgenda_when_noSessions() {
        // Given: Event 14 days away with no sessions
        Instant eventDate = LocalDate.now().plusDays(14)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        Event event = createEvent("BAT-AGENDA-NO-SESSIONS", eventDate, "speakers");

        // When: Auto-publish job runs
        publishingScheduledService.autoPublishAgenda();

        // Then: Should not publish
        Event updated = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getCurrentPublishedPhase()).isEqualTo("speakers");
    }

    /**
     * Story 4.9b: the job honours the stored days, not a fixed 30. An event 10 days away with a
     * 10-day schedule is published; one 30 days away with the same schedule is not (it would have
     * been under the fixed 30 days).
     */
    @Test
    void should_autoPublishSpeakersOnStoredDay_when_scheduleDiffersFromDefault() {
        Event tenDaysAway = createEvent("BAT-SPEAKERS-10", startOfDayIn(10), null);
        Event thirtyDaysAway = createEvent("BAT-SPEAKERS-30-NOT", startOfDayIn(30), null);
        for (Event event : List.of(tenDaysAway, thirtyDaysAway)) {
            publishingConfigRepository.save(PublishingConfig.builder()
                    .eventId(event.getId())
                    .autoPublishSpeakersDaysBefore(10)
                    .build());
        }

        publishingScheduledService.autoPublishSpeakers();

        assertThat(eventRepository.findById(tenDaysAway.getId()).orElseThrow().getCurrentPublishedPhase())
                .isEqualTo("speakers");
        assertThat(eventRepository.findById(thirtyDaysAway.getId()).orElseThrow().getCurrentPublishedPhase())
                .isNull();
    }

    @Test
    void should_autoPublishAgendaOnStoredDay_when_scheduleDiffersFromDefault() {
        Event sevenDaysAway = createEvent("BAT-AGENDA-7", startOfDayIn(7), "speakers");
        createSessionWithTiming(sevenDaysAway, sevenDaysAway.getDate());
        Event fourteenDaysAway = createEvent("BAT-AGENDA-14-NOT", startOfDayIn(14), "speakers");
        createSessionWithTiming(fourteenDaysAway, fourteenDaysAway.getDate());
        for (Event event : List.of(sevenDaysAway, fourteenDaysAway)) {
            publishingConfigRepository.save(PublishingConfig.builder()
                    .eventId(event.getId())
                    .autoPublishAgendaDaysBefore(7)
                    .build());
        }

        publishingScheduledService.autoPublishAgenda();

        Event published = eventRepository.findById(sevenDaysAway.getId()).orElseThrow();
        assertThat(published.getCurrentPublishedPhase()).isEqualTo("agenda");
        assertThat(published.getWorkflowState()).isEqualTo(EventWorkflowState.AGENDA_PUBLISHED);
        assertThat(eventRepository.findById(fourteenDaysAway.getId()).orElseThrow().getCurrentPublishedPhase())
                .isEqualTo("speakers");
    }

    /**
     * The schedule set through the API is the one the job reads: PATCH switches speakers off for
     * one event, the default event next to it is still published.
     */
    @Test
    void should_skipOnlyTheDisabledEvent_when_twoEventsShareTheDay() {
        Event disabled = createEvent("BAT-SPEAKERS-OFF", startOfDayIn(30), null);
        Event byDefault = createEvent("BAT-SPEAKERS-ON", startOfDayIn(30), null);
        publishingService.patchPublishing(disabled.getEventCode(),
                PatchPublishingRequest.builder().phase2Enabled(false).build());

        publishingScheduledService.autoPublishSpeakers();

        assertThat(eventRepository.findById(disabled.getId()).orElseThrow().getCurrentPublishedPhase()).isNull();
        assertThat(eventRepository.findById(byDefault.getId()).orElseThrow().getCurrentPublishedPhase())
                .isEqualTo("speakers");
    }

    private static Instant startOfDayIn(int days) {
        return LocalDate.now().plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    // ============================================
    // Helper Methods
    // ============================================

    private Event createEvent(String eventCode, Instant eventDate, String currentPhase) {
        Event event = Event.builder()
                .eventCode(eventCode)
                .title("Test Event " + eventCode)
                .eventNumber(eventNumberSequence++)
                .date(eventDate)
                .registrationDeadline(eventDate.minus(7, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .venueCapacity(200)
                .organizerUsername("test.organizer")
                .eventType(EventType.FULL_DAY)
                .workflowState(EventWorkflowState.SLOT_ASSIGNMENT)
                .topicCode("cloud-architecture")
                .currentPublishedPhase(currentPhase)
                .lastPublishedAt(currentPhase != null ? Instant.now().minus(1, ChronoUnit.DAYS) : null)
                .build();
        return eventRepository.save(event);
    }

    private void createAcceptedSpeaker(Event event) {
        SpeakerPool speaker = SpeakerPool.builder()
                .eventId(event.getId())
                .speakerName("John Doe")
                .company("TechCorp")
                .status(SpeakerWorkflowState.ACCEPTED)
                .build();
        speakerPoolRepository.save(speaker);
    }

    private void createSessionWithTiming(Event event, Instant eventDate) {
        Session session = Session.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .sessionSlug("test-session-" + event.getEventCode().toLowerCase())
                .title("Test Session")
                .sessionType("presentation")
                .startTime(eventDate.plus(2, ChronoUnit.HOURS))
                .endTime(eventDate.plus(3, ChronoUnit.HOURS))
                .build();
        sessionRepository.save(session);
    }

    private void createSessionWithoutTiming(Event event) {
        Session session = Session.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .sessionSlug("test-session-no-timing")
                .title("Test Session Without Timing")
                .sessionType("presentation")
                .startTime(null) // Missing timing
                .endTime(null) // Missing timing
                .build();
        sessionRepository.save(session);
    }
}
