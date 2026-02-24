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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SpeakerPoolRepository E2E support methods.
 *
 * Tests the repository methods used by E2ETestTokenController for
 * finding speakers with/without sessions for test token generation.
 *
 * Key scenarios tested:
 * - Find speakers with sessions (any workflow status)
 * - Find speakers by event code with various filters
 */
@Transactional
class SpeakerPoolRepositoryE2EMethodsTest extends AbstractIntegrationTest {

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    private Event testEvent;
    private Session testSession;
    private static final String TEST_EVENT_CODE = "BATbern123";

    @BeforeEach
    void setUp() {
        speakerPoolRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();

        // Create test event with all required fields
        Instant eventDate = LocalDate.now().plusMonths(2).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        testEvent = new Event();
        testEvent.setEventCode(TEST_EVENT_CODE);
        testEvent.setTitle("Test Event");
        testEvent.setEventNumber(123);
        testEvent.setDate(eventDate);
        testEvent.setWorkflowState(EventWorkflowState.SPEAKER_IDENTIFICATION);
        testEvent.setOrganizerUsername("test.organizer");
        testEvent.setEventType(EventType.FULL_DAY);
        testEvent.setRegistrationDeadline(eventDate.minusSeconds(7 * 24 * 3600)); // 7 days before event
        testEvent.setVenueName("Test Venue");
        testEvent.setVenueAddress("123 Test Street, Bern");
        testEvent.setVenueCapacity(200);
        testEvent = eventRepository.save(testEvent);

        // Create test session
        testSession = new Session();
        testSession.setEventId(testEvent.getId());
        testSession.setEventCode(TEST_EVENT_CODE);
        testSession.setTitle("Test Session");
        testSession.setSessionSlug("test-session");
        testSession.setSessionType("presentation");
        testSession = sessionRepository.save(testSession);
    }

    @Nested
    @DisplayName("findByEventCodeAndSessionIdIsNotNullOrderByCreatedAtDesc")
    class FindByEventCodeAndSessionIdIsNotNull {

        @Test
        @DisplayName("should find speaker with session in QUALITY_REVIEWED status")
        void should_findSpeaker_withSession_inQualityReviewedStatus() {
            // Given: Speaker with session in QUALITY_REVIEWED status
            SpeakerPool speaker = createSpeaker("Test Speaker", SpeakerWorkflowState.QUALITY_REVIEWED, testSession.getId());

            // When
            List<SpeakerPool> result = speakerPoolRepository
                    .findByEventCodeAndSessionIdIsNotNullOrderByCreatedAtDesc(TEST_EVENT_CODE, PageRequest.of(0, 10));

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSpeakerName()).isEqualTo("Test Speaker");
            assertThat(result.get(0).getSessionId()).isEqualTo(testSession.getId());
        }

        @Test
        @DisplayName("should find speaker with session in CONFIRMED status")
        void should_findSpeaker_withSession_inConfirmedStatus() {
            // Given: Speaker with session in CONFIRMED status
            SpeakerPool speaker = createSpeaker("Confirmed Speaker", SpeakerWorkflowState.CONFIRMED, testSession.getId());

            // When
            List<SpeakerPool> result = speakerPoolRepository
                    .findByEventCodeAndSessionIdIsNotNullOrderByCreatedAtDesc(TEST_EVENT_CODE, PageRequest.of(0, 10));

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(SpeakerWorkflowState.CONFIRMED);
        }

        @Test
        @DisplayName("should find speaker with session in CONTENT_SUBMITTED status")
        void should_findSpeaker_withSession_inContentSubmittedStatus() {
            // Given: Speaker with session in CONTENT_SUBMITTED status
            SpeakerPool speaker = createSpeaker("Content Speaker", SpeakerWorkflowState.CONTENT_SUBMITTED, testSession.getId());

            // When
            List<SpeakerPool> result = speakerPoolRepository
                    .findByEventCodeAndSessionIdIsNotNullOrderByCreatedAtDesc(TEST_EVENT_CODE, PageRequest.of(0, 10));

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);
        }

        @Test
        @DisplayName("should NOT find speaker without session")
        void should_notFindSpeaker_withoutSession() {
            // Given: Speaker without session
            SpeakerPool speakerNoSession = createSpeaker("No Session Speaker", SpeakerWorkflowState.ACCEPTED, null);

            // When
            List<SpeakerPool> result = speakerPoolRepository
                    .findByEventCodeAndSessionIdIsNotNullOrderByCreatedAtDesc(TEST_EVENT_CODE, PageRequest.of(0, 10));

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return speakers ordered by createdAt descending")
        void should_returnSpeakers_orderedByCreatedAtDesc() throws InterruptedException {
            // Given: Multiple speakers with sessions created at different times
            SpeakerPool olderSpeaker = createSpeaker("Older Speaker", SpeakerWorkflowState.QUALITY_REVIEWED, testSession.getId());
            Thread.sleep(10); // Ensure different timestamps

            Session session2 = new Session();
            session2.setEventId(testEvent.getId());
            session2.setEventCode(TEST_EVENT_CODE);
            session2.setTitle("Session 2");
            session2.setSessionSlug("session-2-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            session2.setSessionType("presentation");
            session2 = sessionRepository.save(session2);

            SpeakerPool newerSpeaker = createSpeaker("Newer Speaker", SpeakerWorkflowState.CONFIRMED, session2.getId());

            // When
            List<SpeakerPool> result = speakerPoolRepository
                    .findByEventCodeAndSessionIdIsNotNullOrderByCreatedAtDesc(TEST_EVENT_CODE, PageRequest.of(0, 10));

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getSpeakerName()).isEqualTo("Newer Speaker"); // Most recent first
            assertThat(result.get(1).getSpeakerName()).isEqualTo("Older Speaker");
        }

        @Test
        @DisplayName("should respect page limit")
        void should_respectPageLimit() {
            // Given: Multiple speakers
            createSpeaker("Speaker 1", SpeakerWorkflowState.QUALITY_REVIEWED, testSession.getId());

            Session session2 = new Session();
            session2.setEventId(testEvent.getId());
            session2.setEventCode(TEST_EVENT_CODE);
            session2.setTitle("Session 2");
            session2.setSessionSlug("session-2-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            session2.setSessionType("presentation");
            session2 = sessionRepository.save(session2);
            createSpeaker("Speaker 2", SpeakerWorkflowState.CONFIRMED, session2.getId());

            // When: Limit to 1 result
            List<SpeakerPool> result = speakerPoolRepository
                    .findByEventCodeAndSessionIdIsNotNullOrderByCreatedAtDesc(TEST_EVENT_CODE, PageRequest.of(0, 1));

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return empty list for non-existent event code")
        void should_returnEmptyList_forNonExistentEventCode() {
            // Given: Speaker with session exists
            createSpeaker("Test Speaker", SpeakerWorkflowState.QUALITY_REVIEWED, testSession.getId());

            // When: Query with wrong event code
            List<SpeakerPool> result = speakerPoolRepository
                    .findByEventCodeAndSessionIdIsNotNullOrderByCreatedAtDesc("BATbern999", PageRequest.of(0, 10));

            // Then
            assertThat(result).isEmpty();
        }
    }

    private SpeakerPool createSpeaker(String name, SpeakerWorkflowState status, java.util.UUID sessionId) {
        SpeakerPool speaker = new SpeakerPool();
        speaker.setEventId(testEvent.getId());
        speaker.setSpeakerName(name);
        speaker.setEmail(name.toLowerCase().replace(" ", ".") + "@test.com");
        speaker.setStatus(status);
        speaker.setSessionId(sessionId);
        speaker.setCreatedAt(Instant.now());
        return speakerPoolRepository.save(speaker);
    }
}
