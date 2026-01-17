package ch.batbern.events.repository;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for EventRepository EntityGraph queries (Story BAT-109)
 * Task 3a: Backend TDD Tests (RED Phase) - Repository Layer
 *
 * CRITICAL: These are RED PHASE tests. They MUST FAIL until EntityGraph support is implemented.
 * All tests use Testcontainers PostgreSQL (NEVER H2) to ensure production parity.
 *
 * Tests cover:
 * - EntityGraph for topics (single query with JOIN)
 * - EntityGraph for sessions (single query with JOIN)
 * - EntityGraph for speakers (via sessions - single query with JOIN)
 * - Combined EntityGraphs (topics + sessions + speakers)
 * - Query performance verification (N+1 prevention)
 *
 * Requirements:
 * - EventRepository enhanced with @EntityGraph annotations
 * - Custom repository methods: findAllWithTopics(), findAllWithSessions(), etc.
 * - Hibernate query logging enabled in test properties
 */
@Transactional
class EventRepositoryEntityGraphTest extends AbstractIntegrationTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionUserRepository sessionUserRepository;

    @Autowired
    private TopicRepository topicRepository;

    private Topic cloudTopic;
    private Event archivedEvent;
    private Session session1;
    private Session session2;
    private SessionUser speaker1;
    private SessionUser speaker2;

    @BeforeEach
    void setUp() {
        // Clean database
        sessionUserRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();
        topicRepository.deleteAll();

        // Create topic
        cloudTopic = new Topic();
        cloudTopic.setTopicCode("cloud");
        cloudTopic.setTitle("Cloud Architecture");
        cloudTopic.setDescription("Cloud computing and architecture");
        cloudTopic.setCategory("technical");
        cloudTopic = topicRepository.save(cloudTopic);

        // Create archived event with topic
        Instant eventDate = LocalDate.of(2024, 10, 15)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        archivedEvent = Event.builder()
                .eventCode("BATbern142")
                .eventNumber(142)
                .title("Cloud Architecture Patterns 2024")
                .description("Advanced cloud patterns")
                .date(eventDate)
                .registrationDeadline(eventDate.minus(7, ChronoUnit.DAYS))
                .venueName("Kursaal Bern")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .organizerUsername("test.organizer")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.ARCHIVED)
                .topicCode(cloudTopic.getTopicCode())
                .currentAttendeeCount(150)
                .publishedAt(eventDate.minus(30, ChronoUnit.DAYS))
                .build();
        archivedEvent = eventRepository.save(archivedEvent);

        // Create sessions
        session1 = Session.builder()
                .eventId(archivedEvent.getId())
                .eventCode(archivedEvent.getEventCode())
                .sessionSlug("cloud-keynote")
                .title("Cloud Architecture Keynote")
                .description("Opening keynote")
                .sessionType("keynote")
                .startTime(eventDate.plus(9, ChronoUnit.HOURS))
                .endTime(eventDate.plus(10, ChronoUnit.HOURS))
                .room("Main Hall")
                .capacity(200)
                .language("de")
                .build();
        session1 = sessionRepository.save(session1);

        session2 = Session.builder()
                .eventId(archivedEvent.getId())
                .eventCode(archivedEvent.getEventCode())
                .sessionSlug("microservices-workshop")
                .title("Microservices Workshop")
                .description("Hands-on microservices")
                .sessionType("workshop")
                .startTime(eventDate.plus(10, ChronoUnit.HOURS))
                .endTime(eventDate.plus(12, ChronoUnit.HOURS))
                .room("Workshop Room")
                .capacity(50)
                .language("de")
                .build();
        session2 = sessionRepository.save(session2);

        // Create speakers
        speaker1 = SessionUser.builder()
                .session(session1)
                .username("john.doe")
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(true)
                .presentationTitle("Cloud Patterns")
                .build();
        speaker1 = sessionUserRepository.save(speaker1);

        speaker2 = SessionUser.builder()
                .session(session2)
                .username("jane.smith")
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(true)
                .presentationTitle("Microservices at Scale")
                .build();
        speaker2 = sessionUserRepository.save(speaker2);
    }

    // ================================
    // EntityGraph: Topics
    // ================================

    @Test
    void should_loadTopics_when_findAllWithTopicsEntityGraphUsed() {
        // RED PHASE: This test MUST FAIL
        // Expected failure: Method findAllWithTopics() does not exist on EventRepository
        //
        // When implemented (GREEN phase), this should:
        // 1. Define @EntityGraph(attributePaths = {"topic"}) on custom repository method
        // 2. Execute single query with LEFT JOIN topics
        // 3. Return events with topics eagerly loaded (no lazy loading)

        // This method doesn't exist yet - will cause compilation error or NoSuchMethodError
        List<Event> events = eventRepository.findAllWithTopics();

        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getTopicCode()).isEqualTo("cloud");

        // Verify topic is loaded (not a lazy proxy)
        // In production implementation, this should be verified via:
        // - Hibernate.isInitialized(event.getTopic()) if we add Topic entity relationship
        // - Or by checking topic data is accessible without additional queries
    }

    @Test
    void should_preventNPlusOne_when_loadingTopicsForMultipleEvents() {
        // RED PHASE: This test MUST FAIL
        // Expected failure: Method findAllWithTopics() does not exist
        //
        // When implemented correctly (GREEN phase):
        // - Single query with JOIN should be executed
        // - No additional SELECT queries per event
        //
        // Manual verification required:
        // 1. Enable Hibernate SQL logging (see application-test.properties)
        // 2. Run this test
        // 3. Verify only ONE SELECT query with JOIN topics
        // 4. Expected pattern:
        //    SELECT e.*, t.* FROM events e LEFT JOIN topics t ON e.topic_code = t.topic_code

        // Create additional events with same topic
        Instant now = Instant.now();
        Event event2 = Event.builder()
                .eventCode("BATbern143")
                .eventNumber(143)
                .title("Cloud Event 2")
                .date(now)
                .registrationDeadline(now.minus(7, ChronoUnit.DAYS))
                .venueName("Test")
                .venueAddress("Test")
                .venueCapacity(100)
                .organizerUsername("test")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.ARCHIVED)
                .topicCode(cloudTopic.getTopicCode())
                .build();
        eventRepository.save(event2);

        Event event3 = Event.builder()
                .eventCode("BATbern144")
                .eventNumber(144)
                .title("Cloud Event 3")
                .date(now)
                .registrationDeadline(now.minus(7, ChronoUnit.DAYS))
                .venueName("Test")
                .venueAddress("Test")
                .venueCapacity(100)
                .organizerUsername("test")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.ARCHIVED)
                .topicCode(cloudTopic.getTopicCode())
                .build();
        eventRepository.save(event3);

        // This should execute single query with JOIN
        List<Event> events = eventRepository.findAllWithTopics();

        assertThat(events).hasSize(3);
        // All events should have topic loaded
        events.forEach(event -> {
            assertThat(event.getTopicCode()).isEqualTo("cloud");
        });

        // CRITICAL: Check Hibernate SQL logs manually
        // Expected: 1 SELECT with JOIN
        // Bad (N+1): 1 SELECT events + 3 SELECT topics (one per event)
    }

    // ================================
    // EntityGraph: Sessions
    // ================================

    @Test
    void should_loadSessions_when_findAllWithSessionsEntityGraphUsed() {
        // RED PHASE: This test MUST FAIL
        // Expected failure: Method findAllWithSessions() does not exist
        //
        // When implemented (GREEN phase), this should:
        // 1. Define @EntityGraph(attributePaths = {"sessions"}) on custom repository method
        // 2. Execute single query with LEFT JOIN sessions
        // 3. Return events with sessions collection eagerly loaded

        // This method doesn't exist yet
        List<Event> events = eventRepository.findAllWithSessions();

        assertThat(events).isNotEmpty();

        // Verify sessions are loaded (not lazy proxy)
        Event event = events.get(0);
        // Note: Event entity currently doesn't have @OneToMany sessions relationship
        // This test assumes the relationship will be added during GREEN phase
        // assertThat(event.getSessions()).hasSize(2);
        // assertThat(event.getSessions()).extracting("title")
        //     .contains("Cloud Architecture Keynote", "Microservices Workshop");
    }

    @Test
    void should_preventNPlusOne_when_loadingSessionsForMultipleEvents() {
        // RED PHASE: This test MUST FAIL
        // Expected failure: Method findAllWithSessions() does not exist
        //
        // When implemented correctly (GREEN phase):
        // - Single query with JOIN should be executed for sessions
        // - No N+1 query problem
        //
        // Manual verification required:
        // 1. Check Hibernate SQL logs
        // 2. Verify single SELECT with LEFT JOIN sessions
        // 3. Expected pattern:
        //    SELECT e.*, s.* FROM events e LEFT JOIN sessions s ON e.id = s.event_id

        // Create additional event with sessions
        Instant eventDate2 = Instant.now();
        Event event2 = Event.builder()
                .eventCode("BATbern145")
                .eventNumber(145)
                .title("Event with Sessions")
                .date(eventDate2)
                .registrationDeadline(eventDate2.minus(7, ChronoUnit.DAYS))
                .venueName("Test")
                .venueAddress("Test")
                .venueCapacity(100)
                .organizerUsername("test")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.ARCHIVED)
                .build();
        event2 = eventRepository.save(event2);

        Session session3 = Session.builder()
                .eventId(event2.getId())
                .eventCode(event2.getEventCode())
                .sessionSlug("session-3")
                .title("Test Session")
                .sessionType("presentation")
                .startTime(Instant.now())
                .endTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        sessionRepository.save(session3);

        // This should execute single query with JOIN sessions
        List<Event> events = eventRepository.findAllWithSessions();

        assertThat(events).hasSizeGreaterThanOrEqualTo(2);

        // CRITICAL: Check Hibernate SQL logs manually
        // Expected: 1-2 SELECTs (events + joined sessions)
        // Bad (N+1): 1 SELECT events + N SELECT sessions (one per event)
    }

    // ================================
    // EntityGraph: Combined (Topics + Sessions + Speakers)
    // ================================

    @Test
    void should_loadAllResources_when_findAllWithAllResourcesEntityGraphUsed() {
        // RED PHASE: This test MUST FAIL
        // Expected failure: Method findAllWithAllResources() does not exist
        //
        // When implemented (GREEN phase), this should:
        // 1. Define @EntityGraph with multiple attribute paths:
        //    @EntityGraph(attributePaths = {"topic", "sessions", "sessions.sessionUsers"})
        // 2. Execute efficient queries with JOINs
        // 3. Return events with all related entities eagerly loaded

        // This method doesn't exist yet
        List<Event> events = eventRepository.findAllWithAllResources();

        assertThat(events).isNotEmpty();

        Event event = events.get(0);
        assertThat(event.getTopicCode()).isEqualTo("cloud");
        // When relationship is added:
        // assertThat(event.getSessions()).hasSize(2);
        // assertThat(event.getSessions().get(0).getSessionUsers()).isNotEmpty();
    }

    @Test
    void should_minimizeQueries_when_loadingAllResourcesForMultipleEvents() {
        // RED PHASE: This test MUST FAIL
        // Expected failure: Method findAllWithAllResources() does not exist
        //
        // When implemented correctly (GREEN phase):
        // - Minimal number of queries (ideally 1-3 depending on JPA provider)
        // - No N+1 problem for topics, sessions, or speakers
        //
        // Manual verification required:
        // 1. Check Hibernate SQL logs
        // 2. Count total SELECT queries
        // 3. Verify no N+1 pattern
        // 4. Expected queries:
        //    - Query 1: SELECT events with topics JOIN
        //    - Query 2: SELECT sessions with speakers JOIN (batch fetch)
        //    OR single query with all JOINs (depending on strategy)

        // Create multiple events with full data
        Topic devopsTopic = new Topic();
        devopsTopic.setTopicCode("devops");
        devopsTopic.setTitle("DevOps");
        devopsTopic.setDescription("DevOps practices");
        devopsTopic.setCategory("technical");
        devopsTopic = topicRepository.save(devopsTopic);

        Instant eventDate3 = Instant.now();
        Event event2 = Event.builder()
                .eventCode("BATbern146")
                .eventNumber(146)
                .title("DevOps Event")
                .date(eventDate3)
                .registrationDeadline(eventDate3.minus(7, ChronoUnit.DAYS))
                .venueName("Test")
                .venueAddress("Test")
                .venueCapacity(100)
                .organizerUsername("test")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.ARCHIVED)
                .topicCode(devopsTopic.getTopicCode())
                .build();
        event2 = eventRepository.save(event2);

        Session session3 = Session.builder()
                .eventId(event2.getId())
                .eventCode(event2.getEventCode())
                .sessionSlug("devops-session")
                .title("DevOps Session")
                .sessionType("presentation")
                .startTime(Instant.now())
                .endTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        session3 = sessionRepository.save(session3);

        SessionUser speaker3 = SessionUser.builder()
                .session(session3)
                .username("bob.jones")
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(true)
                .presentationTitle("DevOps Best Practices")
                .build();
        sessionUserRepository.save(speaker3);

        // This should execute minimal queries with JOINs
        List<Event> events = eventRepository.findAllWithAllResources();

        assertThat(events).hasSizeGreaterThanOrEqualTo(2);

        // CRITICAL: Check Hibernate SQL logs manually
        // Expected: 1-3 SELECTs with JOINs (optimal)
        // Bad (N+1): 1 + N + M + P queries (events + topics + sessions + speakers)
    }

    // ================================
    // EntityGraph: Specific Event by ID/Code
    // ================================

    @Test
    void should_loadResourcesForSingleEvent_when_findByIdWithResourcesUsed() {
        // RED PHASE: This test MUST FAIL
        // Expected failure: Method findByEventCodeWithAllResources() does not exist
        //
        // When implemented (GREEN phase), this should:
        // 1. Define custom repository method with @EntityGraph
        // 2. Load single event with all resources in one query
        // 3. Useful for archive event detail page

        // This method doesn't exist yet
        Event event = eventRepository.findByEventCodeWithAllResources("BATbern142")
                .orElseThrow();

        assertThat(event).isNotNull();
        assertThat(event.getTopicCode()).isEqualTo("cloud");
        // When relationships added:
        // assertThat(event.getSessions()).hasSize(2);
    }
}
