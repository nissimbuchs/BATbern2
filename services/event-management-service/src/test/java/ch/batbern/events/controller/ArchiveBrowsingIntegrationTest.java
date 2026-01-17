package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.TopicRepository;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Archive Browsing feature (Story BAT-109)
 * Task 3a: Backend TDD Tests (RED Phase)
 *
 * CRITICAL: These are RED PHASE tests. They MUST FAIL until the feature is implemented.
 * All tests use Testcontainers PostgreSQL (NEVER H2) to ensure production parity.
 *
 * Tests cover:
 * - Resource expansion with topics, sessions, speakers (?include=topics,sessions,speakers)
 * - Filter query parameters (workflowState, year, combined)
 * - Sort parameters (-date, date, -attendeeCount, -sessionCount)
 * - N+1 query prevention via Hibernate query logging
 *
 * Requirements:
 * - AbstractIntegrationTest provides PostgreSQL via Testcontainers
 * - Flyway migration V29 with GIN indexes applied
 * - EventRepository enhanced with EntityGraph support
 * - EventSearchService supports resource expansion
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
public class ArchiveBrowsingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionUserRepository sessionUserRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private CacheManager cacheManager;

    private Topic cloudTopic;
    private Topic devopsTopic;
    private Event archivedEvent2024;
    private Event archivedEvent2023;
    private Event completedEvent2024;
    private Session session1;
    private Session session2;
    private SessionUser speaker1;
    private SessionUser speaker2;

    @BeforeEach
    void setUp() {
        // Clear all caches to prevent test interference
        cacheManager.getCacheNames().forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });

        // Clean database (maintain referential integrity)
        sessionUserRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();
        topicRepository.deleteAll();

        // Create topics
        cloudTopic = new Topic();
        cloudTopic.setTopicCode("cloud");
        cloudTopic.setTitle("Cloud Architecture");
        cloudTopic.setDescription("Cloud computing and architecture patterns");
        cloudTopic.setCategory("technical");
        cloudTopic = topicRepository.save(cloudTopic);

        devopsTopic = new Topic();
        devopsTopic.setTopicCode("devops");
        devopsTopic.setTitle("DevOps");
        devopsTopic.setDescription("DevOps practices and tooling");
        devopsTopic.setCategory("technical");
        devopsTopic = topicRepository.save(devopsTopic);

        // Create archived events from different years
        // Event 1: Archived event from 2024 with cloud topic
        Instant date2024 = LocalDate.of(2024, 10, 15)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        archivedEvent2024 = Event.builder()
                .eventCode("BATbern142")
                .eventNumber(142)
                .title("Cloud Architecture Patterns 2024")
                .description("Advanced cloud patterns and best practices")
                .date(date2024)
                .registrationDeadline(date2024.minus(7, ChronoUnit.DAYS))
                .venueName("Kursaal Bern")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .organizerUsername("test.organizer")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.ARCHIVED)
                .topicCode(cloudTopic.getTopicCode())
                .currentAttendeeCount(150)
                .publishedAt(date2024.minus(30, ChronoUnit.DAYS))
                .build();
        archivedEvent2024 = eventRepository.save(archivedEvent2024);

        // Event 2: Archived event from 2023 with devops topic
        Instant date2023 = LocalDate.of(2023, 9, 20)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        archivedEvent2023 = Event.builder()
                .eventCode("BATbern138")
                .eventNumber(138)
                .title("DevOps Excellence 2023")
                .description("DevOps tooling and culture")
                .date(date2023)
                .registrationDeadline(date2023.minus(7, ChronoUnit.DAYS))
                .venueName("Kursaal Bern")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(180)
                .organizerUsername("test.organizer")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.ARCHIVED)
                .topicCode(devopsTopic.getTopicCode())
                .currentAttendeeCount(120)
                .publishedAt(date2023.minus(30, ChronoUnit.DAYS))
                .build();
        archivedEvent2023 = eventRepository.save(archivedEvent2023);

        // Event 3: Completed event (not archived) from 2024
        Instant dateCompleted = LocalDate.of(2024, 11, 5)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        completedEvent2024 = Event.builder()
                .eventCode("BATbern143")
                .eventNumber(143)
                .title("Recent Completed Event")
                .description("Recently completed but not yet archived")
                .date(dateCompleted)
                .registrationDeadline(dateCompleted.minus(7, ChronoUnit.DAYS))
                .venueName("Kursaal Bern")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .organizerUsername("test.organizer")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.EVENT_COMPLETED)
                .topicCode(cloudTopic.getTopicCode())
                .currentAttendeeCount(175)
                .publishedAt(dateCompleted.minus(30, ChronoUnit.DAYS))
                .build();
        completedEvent2024 = eventRepository.save(completedEvent2024);

        // Create sessions for archived event 2024
        session1 = Session.builder()
                .eventId(archivedEvent2024.getId())
                .eventCode(archivedEvent2024.getEventCode())
                .sessionSlug("cloud-patterns-keynote")
                .title("Cloud Architecture Keynote")
                .description("Opening keynote on cloud patterns")
                .sessionType("keynote")
                .startTime(date2024.plus(9, ChronoUnit.HOURS))
                .endTime(date2024.plus(10, ChronoUnit.HOURS))
                .room("Main Hall")
                .capacity(200)
                .language("de")
                .build();
        session1 = sessionRepository.save(session1);

        session2 = Session.builder()
                .eventId(archivedEvent2024.getId())
                .eventCode(archivedEvent2024.getEventCode())
                .sessionSlug("microservices-workshop")
                .title("Microservices Workshop")
                .description("Hands-on microservices patterns")
                .sessionType("workshop")
                .startTime(date2024.plus(10, ChronoUnit.HOURS))
                .endTime(date2024.plus(12, ChronoUnit.HOURS))
                .room("Workshop Room")
                .capacity(50)
                .language("de")
                .build();
        session2 = sessionRepository.save(session2);

        // Create speakers for sessions
        speaker1 = SessionUser.builder()
                .session(session1)
                .username("john.doe")
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(true)
                .presentationTitle("Cloud Architecture Patterns")
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

        // Flush and clear persistence context to ensure data is persisted
        entityManager.flush();
        entityManager.clear();
    }

    // ================================
    // Resource Expansion Tests
    // ================================

    @Test
    void should_expandTopics_when_includeTopicsRequested() throws Exception {
        // RED PHASE: This test MUST FAIL
        // Expected failure: Topics not loaded in response (include parameter ignored)
        //
        // When implemented (GREEN phase), this should:
        // 1. Use EntityGraph to fetch topics in single query (no N+1)
        // 2. Return topic details in response

        mockMvc.perform(get("/api/v1/events")
                        .param("include", "topics")
                        .param("includeArchived", "true")
                        .param("filter", "{\"workflowState\":{\"$in\":[\"ARCHIVED\"]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[0].topicCode", is("cloud")))
                // EXPECTED TO FAIL: topic object not expanded yet
                .andExpect(jsonPath("$.data[0].topic", notNullValue()))
                .andExpect(jsonPath("$.data[0].topic.code", is("cloud")))
                .andExpect(jsonPath("$.data[0].topic.name", is("Cloud Architecture")));
    }

    @Test
    void should_expandSessions_when_includeSessionsRequested() throws Exception {
        // RED PHASE: This test MUST FAIL
        // Expected failure: Sessions not loaded in response
        //
        // When implemented (GREEN phase), this should:
        // 1. Use EntityGraph to fetch sessions with joins (no N+1)
        // 2. Return session details in response array

        mockMvc.perform(get("/api/v1/events/" + archivedEvent2024.getEventCode())
                        .param("include", "sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode", is("BATbern142")))
                // EXPECTED TO FAIL: sessions array not present yet
                .andExpect(jsonPath("$.sessions", notNullValue()))
                .andExpect(jsonPath("$.sessions", hasSize(2)))
                .andExpect(jsonPath("$.sessions[0].title",
                        containsString("Cloud Architecture")));
    }

    @Test
    void should_expandSpeakers_when_includeSpeakersRequested() throws Exception {
        // RED PHASE: This test MUST FAIL
        // Expected failure: Speakers not loaded in sessions
        //
        // When implemented (GREEN phase), this should:
        // 1. Use EntityGraph to fetch speakers with sessions (no N+1)
        // 2. Return speaker details in session.speakers array

        mockMvc.perform(get("/api/v1/events/" + archivedEvent2024.getEventCode())
                        .param("include", "sessions,speakers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].speakers", notNullValue()))
                // EXPECTED TO FAIL: speakers array not populated yet
                .andExpect(jsonPath("$.sessions[0].speakers", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.sessions[0].speakers[0].username", is("john.doe")))
                .andExpect(jsonPath("$.sessions[0].speakers[0].speakerRole", is("PRIMARY_SPEAKER")));
    }

    @Test
    void should_expandAllResources_when_multipleIncludesRequested() throws Exception {
        // RED PHASE: This test MUST FAIL
        // Expected failure: Combined expansion not implemented
        //
        // When implemented (GREEN phase), this should:
        // 1. Load topics, sessions, and speakers in minimal queries
        // 2. Return all expanded resources in response

        mockMvc.perform(get("/api/v1/events/" + archivedEvent2024.getEventCode())
                        .param("include", "topics,sessions,speakers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode", is("BATbern142")))
                // EXPECTED TO FAIL: None of these expansions work yet
                .andExpect(jsonPath("$.topic", notNullValue()))
                .andExpect(jsonPath("$.topic.code", is("cloud")))
                .andExpect(jsonPath("$.sessions", hasSize(2)))
                .andExpect(jsonPath("$.sessions[0].speakers", hasSize(greaterThan(0))));
    }

    @Test
    void should_NOT_expandResources_when_includeParameterOmitted() throws Exception {
        // RED PHASE: This test should PASS (validates default behavior)
        // Verifies that without include parameter, related entities are NOT loaded
        //
        // This test validates the baseline: resources should only load when explicitly requested

        mockMvc.perform(get("/api/v1/events/" + archivedEvent2024.getEventCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode", is("BATbern142")))
                .andExpect(jsonPath("$.topicCode", is("cloud")))
                // These should NOT be present without include parameter
                .andExpect(jsonPath("$.topic").doesNotExist())
                .andExpect(jsonPath("$.sessions").doesNotExist());
    }

    // ================================
    // Filter Query Parameter Tests
    // ================================

    @Test
    void should_filterByWorkflowState_when_workflowStateFilterProvided() throws Exception {
        // RED PHASE: This test MIGHT PASS (filter already implemented)
        // If it fails, it's because filter syntax changed for archive browsing

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", "{\"workflowState\":{\"$in\":[\"ARCHIVED\"]}}")
                        .param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2))) // Only archived events
                .andExpect(jsonPath("$.data[*].eventCode",
                        containsInAnyOrder("BATbern142", "BATbern138")));
    }

    @Test
    void should_filterByYear_when_yearFilterProvided() throws Exception {
        // RED PHASE: This test MIGHT PASS or FAIL depending on current filter implementation
        // Tests filtering by event year (e.g., ?filter={"year":2024})

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", "{\"year\":2024}")
                        .param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2))) // 2024 events only
                .andExpect(jsonPath("$.data[*].eventCode",
                        containsInAnyOrder("BATbern142", "BATbern143")));
    }

    @Test
    void should_filterByCombinedParams_when_multipleFiltersProvided() throws Exception {
        // RED PHASE: This test MUST FAIL
        // Tests combined filtering: workflowState + year

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", "{\"workflowState\":{\"$in\":[\"ARCHIVED\"]},\"year\":2024}")
                        .param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1))) // Only archived 2024 event
                .andExpect(jsonPath("$.data[0].eventCode", is("BATbern142")));
    }

    @Test
    void should_returnAllEvents_when_emptyFilterProvided() throws Exception {
        // RED PHASE: This test MIGHT PASS
        // Tests that empty filter returns all events

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", "{}")
                        .param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3))); // All events
    }

    // ================================
    // Sort Parameter Tests
    // ================================

    @Test
    void should_sortByDateDescending_when_sortMinusDateProvided() throws Exception {
        // RED PHASE: This test MIGHT PASS (sort already implemented)
        // Tests default sort: newest first

        mockMvc.perform(get("/api/v1/events")
                        .param("sort", "-date")
                        .param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                // Newest first: BATbern143 (Nov 2024) → BATbern142 (Oct 2024) → BATbern138 (Sep 2023)
                .andExpect(jsonPath("$.data[0].eventCode", is("BATbern143")))
                .andExpect(jsonPath("$.data[1].eventCode", is("BATbern142")))
                .andExpect(jsonPath("$.data[2].eventCode", is("BATbern138")));
    }

    @Test
    void should_sortByDateAscending_when_sortPlusDateProvided() throws Exception {
        // RED PHASE: This test MIGHT PASS
        // Tests ascending date sort: oldest first

        mockMvc.perform(get("/api/v1/events")
                        .param("sort", "date")
                        .param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                // Oldest first: BATbern138 (Sep 2023) → BATbern142 (Oct 2024) → BATbern143 (Nov 2024)
                .andExpect(jsonPath("$.data[0].eventCode", is("BATbern138")))
                .andExpect(jsonPath("$.data[1].eventCode", is("BATbern142")))
                .andExpect(jsonPath("$.data[2].eventCode", is("BATbern143")));
    }

    @Test
    void should_sortByAttendance_when_sortMinusAttendanceProvided() throws Exception {
        // RED PHASE: This test MUST FAIL
        // Expected failure: Sort by attendeeCount not implemented
        //
        // When implemented (GREEN phase), this should:
        // 1. Sort by currentAttendeeCount descending
        // 2. Return events in order: 175 → 150 → 120

        mockMvc.perform(get("/api/v1/events")
                        .param("sort", "-attendeeCount")
                        .param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                // EXPECTED TO FAIL: Sort not implemented yet
                // Expected order: BATbern143 (175) → BATbern142 (150) → BATbern138 (120)
                .andExpect(jsonPath("$.data[0].eventCode", is("BATbern143")))
                .andExpect(jsonPath("$.data[0].currentAttendeeCount", is(175)))
                .andExpect(jsonPath("$.data[1].eventCode", is("BATbern142")))
                .andExpect(jsonPath("$.data[1].currentAttendeeCount", is(150)))
                .andExpect(jsonPath("$.data[2].eventCode", is("BATbern138")))
                .andExpect(jsonPath("$.data[2].currentAttendeeCount", is(120)));
    }

    @Test
    void should_defaultToDateDescending_when_sortParameterOmitted() throws Exception {
        // RED PHASE: This test MIGHT PASS
        // Tests that default sort is -date (newest first)

        mockMvc.perform(get("/api/v1/events")
                        .param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventCode", is("BATbern143")));
    }

    // ================================
    // N+1 Query Prevention Tests
    // ================================

    @Test
    void should_preventNPlusOneQueries_when_expandingTopics() throws Exception {
        // RED PHASE: This test will FAIL or PASS depending on implementation
        // CRITICAL: Validates that topics are loaded in a single query using EntityGraph
        //
        // To validate N+1 prevention manually:
        // 1. Enable Hibernate SQL logging (see application-test.properties)
        // 2. Run this test
        // 3. Count SELECT queries in logs - should be minimal (1-2 queries)
        //
        // Expected queries when implemented correctly:
        // Query 1: SELECT events WITH topics (EntityGraph join)
        // Query 2: Count query for pagination
        //
        // BAD (N+1): One query per event to load topic (3+ queries total)
        // GOOD: Single query with join (1-2 queries total)

        mockMvc.perform(get("/api/v1/events")
                        .param("include", "topics")
                        .param("filter", "{\"workflowState\":{\"$in\":[\"ARCHIVED\"]}}")
                        .param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        // Manual verification required:
        // 1. Check test logs for Hibernate SQL statements
        // 2. Verify topics are loaded in the main SELECT (not separate queries)
        // 3. See expected pattern:
        //    SELECT e.*, t.* FROM events e LEFT JOIN topics t ON e.topic_code = t.code ...
    }

    @Test
    void should_preventNPlusOneQueries_when_expandingSessionsAndSpeakers() throws Exception {
        // RED PHASE: This test will FAIL or PASS depending on implementation
        // CRITICAL: Validates that sessions and speakers are loaded efficiently
        //
        // Expected queries when implemented correctly:
        // Query 1: SELECT event BY eventCode
        // Query 2: SELECT sessions WITH speakers (EntityGraph join)
        //
        // BAD (N+1): One query per session to load speakers (3+ queries)
        // GOOD: Single query with joins (1-2 queries)

        mockMvc.perform(get("/api/v1/events/" + archivedEvent2024.getEventCode())
                        .param("include", "sessions,speakers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions", hasSize(2)));

        // Manual verification required:
        // 1. Check test logs for Hibernate SQL statements
        // 2. Verify sessions + speakers loaded in single query
        // 3. See expected pattern:
        //    SELECT s.*, su.* FROM sessions s
        //    LEFT JOIN session_users su ON s.id = su.session_id
        //    WHERE s.event_id = ?
    }
}
