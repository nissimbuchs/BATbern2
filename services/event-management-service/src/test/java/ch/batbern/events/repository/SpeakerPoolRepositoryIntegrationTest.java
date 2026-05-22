package ch.batbern.events.repository;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SpeakerPoolRepository.countPublishableByEventId(UUID).
 *
 * Story 11.B.3 AC8: Verifies the 6 required test scenarios for the publishability gate
 * used by EventWorkflowStateMachine.validateAllSpeakersConfirmed.
 */
@Transactional
@DisplayName("SpeakerPoolRepository.countPublishableByEventId — AC8 coverage")
class SpeakerPoolRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    private Event testEvent;
    private static final String TEST_EVENT_CODE = "BATbern99";

    @BeforeEach
    void setUp() {
        speakerPoolRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();

        Instant eventDate = Instant.now().plus(60, ChronoUnit.DAYS);
        testEvent = new Event();
        testEvent.setEventCode(TEST_EVENT_CODE);
        testEvent.setTitle("Publishable Count Test Event");
        testEvent.setEventNumber(99);
        testEvent.setDate(eventDate);
        testEvent.setWorkflowState(EventWorkflowState.SLOT_ASSIGNMENT);
        testEvent.setOrganizerUsername("test.organizer");
        testEvent.setEventType(EventType.FULL_DAY);
        testEvent.setRegistrationDeadline(eventDate.minus(14, ChronoUnit.DAYS));
        testEvent.setVenueName("Test Venue");
        testEvent.setVenueAddress("Bern, Switzerland");
        testEvent.setVenueCapacity(100);
        testEvent = eventRepository.save(testEvent);
    }

    private SpeakerPool saveSpeaker(SpeakerWorkflowState status, UUID sessionId) {
        return speakerPoolRepository.save(SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("Speaker " + UUID.randomUUID())
                .status(status)
                .sessionId(sessionId)
                .build());
    }

    private Session saveSession(Instant startTime) {
        Session s = new Session();
        s.setEventId(testEvent.getId());
        s.setEventCode(TEST_EVENT_CODE);
        s.setTitle("Test Session");
        s.setSessionSlug("test-session-" + UUID.randomUUID());
        s.setSessionType("presentation");
        s.setStartTime(startTime);
        return sessionRepository.save(s);
    }

    @Test
    @DisplayName("AC8 case 1: Empty pool → count 0")
    void countPublishable_emptyPool_returnsZero() {
        assertThat(speakerPoolRepository.countPublishableByEventId(testEvent.getId())).isZero();
    }

    @Test
    @DisplayName("AC8 case 2: Speakers present but none QUALITY_REVIEWED → count 0")
    void countPublishable_noQualityReviewedSpeakers_returnsZero() {
        Session s = saveSession(Instant.now().plus(1, ChronoUnit.DAYS));
        saveSpeaker(SpeakerWorkflowState.ACCEPTED, s.getId());
        saveSpeaker(SpeakerWorkflowState.CONTENT_SUBMITTED, s.getId());

        assertThat(speakerPoolRepository.countPublishableByEventId(testEvent.getId())).isZero();
    }

    @Test
    @DisplayName("AC8 case 3: QUALITY_REVIEWED with sessionId=null → count 0")
    void countPublishable_qualityReviewedWithNoSession_returnsZero() {
        saveSpeaker(SpeakerWorkflowState.QUALITY_REVIEWED, null);

        assertThat(speakerPoolRepository.countPublishableByEventId(testEvent.getId())).isZero();
    }

    @Test
    @DisplayName("AC8 case 4: QUALITY_REVIEWED with session.startTime=null → count 0")
    void countPublishable_qualityReviewedWithNullStartTime_returnsZero() {
        Session s = saveSession(null);
        saveSpeaker(SpeakerWorkflowState.QUALITY_REVIEWED, s.getId());

        assertThat(speakerPoolRepository.countPublishableByEventId(testEvent.getId())).isZero();
    }

    @Test
    @DisplayName("AC8 case 5: QUALITY_REVIEWED with non-null session.startTime → count 1")
    void countPublishable_qualityReviewedWithNonNullStartTime_returnsOne() {
        Session s = saveSession(Instant.now().plus(1, ChronoUnit.DAYS));
        saveSpeaker(SpeakerWorkflowState.QUALITY_REVIEWED, s.getId());

        assertThat(speakerPoolRepository.countPublishableByEventId(testEvent.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("AC8 case 6: Mixed scenario — only fully publishable speaker counted")
    void countPublishable_mixedScenario_countsOnlyPublishable() {
        Session sessionWithTime = saveSession(Instant.now().plus(1, ChronoUnit.DAYS));
        Session sessionWithoutTime = saveSession(null);

        saveSpeaker(SpeakerWorkflowState.IDENTIFIED, null);                              // not QR, no session
        saveSpeaker(SpeakerWorkflowState.ACCEPTED, sessionWithTime.getId());             // not QR
        saveSpeaker(SpeakerWorkflowState.QUALITY_REVIEWED, null);                        // QR, no session
        saveSpeaker(SpeakerWorkflowState.QUALITY_REVIEWED, sessionWithoutTime.getId());  // QR, null startTime
        saveSpeaker(SpeakerWorkflowState.QUALITY_REVIEWED, sessionWithTime.getId());     // ✅ publishable

        assertThat(speakerPoolRepository.countPublishableByEventId(testEvent.getId())).isEqualTo(1L);
    }
}
