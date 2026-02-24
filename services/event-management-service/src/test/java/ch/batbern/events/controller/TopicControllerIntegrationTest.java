package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.domain.TopicUsageHistory;
import ch.batbern.events.dto.generated.topics.SelectTopicForEventRequest;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.TopicRepository;
import ch.batbern.events.repository.TopicUsageHistoryRepository;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Topic Management API (Story 5.2).
 *
 * Tests verify:
 * - GET /api/v1/topics - List all topics with filtering and sorting (AC1)
 * - GET /api/v1/topics/{id} - Get specific topic with includes (AC1)
 * - POST /api/v1/topics - Create new topic (AC8)
 * - Similarity score calculation (AC4)
 * - Staleness score calculation (AC6)
 * - Override with justification (AC7)
 *
 * TDD RED PHASE: These tests should FAIL until TopicController is implemented.
 *
 * Uses PostgreSQL via Testcontainers for production parity (never H2).
 */
@Transactional
class TopicControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TopicUsageHistoryRepository topicUsageHistoryRepository;

    @BeforeEach
    void setUp() {
        // Clean up topics, events, and usage history before each test
        topicUsageHistoryRepository.deleteAll();
        topicRepository.deleteAll();
        eventRepository.deleteAll();
    }

    // ==================== AC1 Tests: List all topics ====================

    /**
     * Test 2a.1: should_returnAllTopics_when_GETApiTopicsCalled
     * Verifies GET /api/v1/topics returns all available topics with pagination.
     * Story 5.2 AC1: Display searchable list of all available topics
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_returnAllTopics_when_GETApiTopicsCalled() throws Exception {
        // Given: Three topics exist in database
        createTestTopic("Cloud Native Architecture", "technical", 85);
        createTestTopic("Leadership Skills", "management", 50);
        createTestTopic("AI/ML Fundamentals", "technical", 100);

        // When & Then: GET /api/v1/topics returns all topics with pagination
        mockMvc.perform(get("/api/v1/topics")
                .param("page", "1")
                .param("limit", "50")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].title", containsInAnyOrder(
                    "Cloud Native Architecture",
                    "Leadership Skills",
                    "AI/ML Fundamentals")))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.limit").value(50))
                .andExpect(jsonPath("$.pagination.totalItems").value(3));
    }

    /**
     * Test 2a.2: should_filterTopicsByCategory_when_categoryFilterProvided
     * Verifies filtering by category parameter.
     * Story 5.2 AC1: Searchable and filterable topic list
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_filterTopicsByCategory_when_categoryFilterProvided() throws Exception {
        // Given: Topics in different categories
        createTestTopic("Cloud Native", "technical", 85);
        createTestTopic("Leadership", "management", 50);
        createTestTopic("Kubernetes", "technical", 90);

        // When: Filter by technical category
        String filter = "{\"category\":\"technical\"}";

        // Then: Only technical topics returned
        mockMvc.perform(get("/api/v1/topics")
                .param("filter", filter)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].category", everyItem(equalTo("technical"))));
    }

    /**
     * Test 2a.3: should_sortByStalenesScore_when_sortParameterProvided
     * Verifies sorting by staleness score descending (highest first = safest to reuse).
     * Story 5.2 AC6: Display 0-100 staleness score
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_sortByStalenessScore_when_sortParameterProvided() throws Exception {
        // Given: Topics with different staleness scores
        createTestTopic("Recent Topic", "technical", 25);  // Recently used
        createTestTopic("Moderate Topic", "technical", 60); // Moderately stale
        createTestTopic("Stale Topic", "technical", 95);    // Very stale (safe)

        // When: Sort by staleness descending
        mockMvc.perform(get("/api/v1/topics")
                .param("sort", "-stalenessScore")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                // Verify descending order: 95, 60, 25
                .andExpect(jsonPath("$.data[0].stalenessScore").value(95))
                .andExpect(jsonPath("$.data[1].stalenessScore").value(60))
                .andExpect(jsonPath("$.data[2].stalenessScore").value(25));
    }

    /**
     * Test 2a.4: should_returnTopicWithDetails_when_getTopicByIdCalled
     * Verifies GET /api/v1/topics/{id} returns complete topic details.
     * Story 5.2 AC1: Topic details display
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_returnTopicWithDetails_when_getTopicByIdCalled() throws Exception {
        // Given: Topic exists
        Topic topic = createTestTopic("Cloud Native", "technical", 85);

        // When & Then: GET /api/v1/topics/{topicCode} returns topic details
        mockMvc.perform(get("/api/v1/topics/{topicCode}", topic.getTopicCode())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicCode").value(topic.getTopicCode()))
                .andExpect(jsonPath("$.title").value("Cloud Native"))
                .andExpect(jsonPath("$.category").value("technical"))
                .andExpect(jsonPath("$.stalenessScore").value(85))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    // ==================== AC4 Tests: Similarity Score Calculation ====================

    /**
     * Test 2a.5: should_calculateSimilarityScore_when_twoTopicsCompared
     * Verifies TF-IDF and cosine similarity calculation between topics.
     * Story 5.2 AC4: Calculate similarity scores using TF-IDF and cosine similarity
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_calculateSimilarityScore_when_twoTopicsCompared() throws Exception {
        // Given: Two similar topics exist
        Topic topic1 = createTestTopic(
            "Cloud Native Architecture Patterns",
            "technical",
            100
        );
        Topic topic2 = createTestTopic(
            "Cloud Native Design Best Practices",
            "technical",
            100
        );

        // When: Get topic with similarity scores
        mockMvc.perform(get("/api/v1/topics/{topicCode}", topic1.getTopicCode())
                .param("include", "similarity")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Then: Similarity scores are calculated
                .andExpect(jsonPath("$.similarityScores").isArray())
                .andExpect(jsonPath("$.similarityScores", hasSize(greaterThan(0))))
                // Verify high similarity due to overlapping terms (cloud, native)
                .andExpect(jsonPath("$.similarityScores[0].score").isNumber())
                .andExpect(jsonPath("$.similarityScores[0].topicCode").value(topic2.getTopicCode()));
    }

    // ==================== AC6 Tests: Staleness Score Calculation ====================

    /**
     * Test 2a.6: should_calculateStalenessScore_when_topicUsageHistoryProvided
     * Verifies staleness score calculation: 0-100 where 100 = safe to reuse (>12 months).
     * Story 5.2 AC6: Display 0-100 staleness score
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_calculateStalenessScore_when_topicUsageHistoryProvided() throws Exception {
        // Given: Topics with different usage patterns
        Topic recentTopic = createTestTopic("Recent", "technical", 10);
        Topic moderateTopic = createTestTopic("Moderate", "technical", 50);
        Topic staleTopic = createTestTopic("Stale", "technical", 100);

        // When & Then: Verify staleness scores are in valid range
        mockMvc.perform(get("/api/v1/topics/{topicCode}", recentTopic.getTopicCode())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stalenessScore").value(10))
                .andExpect(jsonPath("$.stalenessScore", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.stalenessScore", lessThanOrEqualTo(100)));

        mockMvc.perform(get("/api/v1/topics/{topicCode}", staleTopic.getTopicCode())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stalenessScore").value(100));
    }

    // ==================== AC7 Tests: Override with Justification ====================

    /**
     * Test 2a.7: should_allowOverrideWithJustification_when_similarityScoreHigh
     * Verifies override capability when similarity >70% with justification.
     * Story 5.2 AC7: Allow organizers to override warnings with justification
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_allowOverrideWithJustification_when_similarityScoreHigh() throws Exception {
        // Given: Event in CREATED state and topic with high similarity to recent topic
        Event event = createTestEvent("BATbern999", EventWorkflowState.CREATED);
        Topic similarTopic = createTestTopic("Cloud Native Advanced", "technical", 40);

        // When: Select topic with justification override
        SelectTopicForEventRequest request = new SelectTopicForEventRequest()
            .topicCode(similarTopic.getTopicCode())
            .justification("Partner explicitly requested this specific topic");

        // Then: Override is accepted with justification
        // Note: This test will be fully validated in Task 3a (Workflow Integration)
        // For now, we verify the endpoint accepts the request structure
        mockMvc.perform(post("/api/v1/events/{eventCode}/topics", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // ==================== AC8 Tests: Topic Creation ====================

    /**
     * Test 2a.8: should_createNewTopic_when_validDataProvided
     * Verifies POST /api/v1/topics creates new topic with initial staleness 100.
     * Story 5.2 AC8: Allow new topic creation with description inline
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_createNewTopic_when_validDataProvided() throws Exception {
        // Given: Valid topic creation request
        Map<String, Object> request = new HashMap<>();
        request.put("title", "AI-Powered DevOps");
        request.put("description", "Exploring AI applications in DevOps workflows");
        request.put("category", "technical");

        // When: POST /api/v1/topics
        mockMvc.perform(post("/api/v1/topics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                // Then: Topic is created with 201 status
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.topicCode").exists())
                .andExpect(jsonPath("$.title").value("AI-Powered DevOps"))
                .andExpect(jsonPath("$.category").value("technical"))
                // AC8: New topics have max staleness score (100 = safe to use)
                .andExpect(jsonPath("$.stalenessScore").value(100))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    /**
     * Test 2a.9: should_return400_when_titleMissing
     * Verifies validation error when required title is missing.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return400_when_titleMissing() throws Exception {
        // Given: Request without title
        Map<String, Object> request = new HashMap<>();
        request.put("category", "technical");

        // When & Then: Returns 400 validation error
        mockMvc.perform(post("/api/v1/topics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ==================== AC17 Tests: Topic Entity Persistence ====================

    /**
     * Test 2a.10: should_persistTopicEntity_when_topicServiceCreateCalled
     * Verifies Topic entity is correctly persisted with all fields.
     * Story 5.2 AC17: Topic Entity Enhancement
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_persistTopicEntity_when_topicServiceCreateCalled() throws Exception {
        // Given: Valid topic data
        Map<String, Object> request = new HashMap<>();
        request.put("title", "Kubernetes Best Practices");
        request.put("description", "Deep dive into K8s patterns");
        request.put("category", "technical");

        // When: Create topic via API
        String response = mockMvc.perform(post("/api/v1/topics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Then: Verify topic is persisted in database
        Map<String, Object> createdTopic = objectMapper.readValue(response, Map.class);
        String topicCode = (String) createdTopic.get("topicCode");

        mockMvc.perform(get("/api/v1/topics/{topicCode}", topicCode)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Kubernetes Best Practices"))
                .andExpect(jsonPath("$.stalenessScore").value(100))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    // ==================== AC2 Tests: Usage History ====================

    /**
     * Test: should_returnUsageHistory_when_topicHasBeenUsed
     * Verifies GET /api/v1/topics/{id}/usage-history returns historical usage data.
     * Story 5.2 AC2: Heat map visualization of topic usage over 24 months
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_returnUsageHistory_when_topicHasBeenUsed() throws Exception {
        // Given: Topic with usage history from two different events
        Topic topic = createTestTopic("Cloud Architecture", "technical", 85);
        Event event1 = createTestEvent("BATbern100", EventWorkflowState.CREATED);
        Event event2 = createTestEvent("BATbern101", EventWorkflowState.CREATED);

        createTopicUsageHistory(
                topic.getId(),
                event1.getId(),
                LocalDateTime.now().minusMonths(6),
                150,
                0.85
        );

        createTopicUsageHistory(
                topic.getId(),
                event2.getId(),
                LocalDateTime.now().minusMonths(12),
                200,
                0.92
        );

        // When: GET usage history
        mockMvc.perform(get("/api/v1/topics/{topicCode}/usage-history", topic.getTopicCode())
                .contentType(MediaType.APPLICATION_JSON))
                // Then: Returns usage history with eventNumber (no UUIDs in API)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventNumber").exists())
                .andExpect(jsonPath("$[0].eventCode").exists())
                .andExpect(jsonPath("$[0].eventDate").exists())
                .andExpect(jsonPath("$[0].usedDate").exists())
                .andExpect(jsonPath("$[0].attendance").exists())
                .andExpect(jsonPath("$[0].engagementScore").exists())
                // Verify sorted by date descending (most recent first)
                .andExpect(jsonPath("$[0].attendance").value(150))
                .andExpect(jsonPath("$[1].attendance").value(200));
    }

    /**
     * Test: should_returnEmptyArray_when_topicHasNoUsageHistory
     * Verifies endpoint returns empty array for topics without usage history.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_returnEmptyArray_when_topicHasNoUsageHistory() throws Exception {
        // Given: Topic with no usage history
        Topic topic = createTestTopic("New Topic", "technical", 100);

        // When: GET usage history
        mockMvc.perform(get("/api/v1/topics/{topicCode}/usage-history", topic.getTopicCode())
                .contentType(MediaType.APPLICATION_JSON))
                // Then: Returns empty array
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /**
     * Test: should_return404_when_topicNotFound
     * Verifies endpoint returns 404 for non-existent topics.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return404_when_topicNotFoundForUsageHistory() throws Exception {
        // Given: Non-existent topic ID
        String nonExistentId = "123e4567-e89b-12d3-a456-426614174000";

        // When: GET usage history for non-existent topic
        mockMvc.perform(get("/api/v1/topics/{id}/usage-history", nonExistentId)
                .contentType(MediaType.APPLICATION_JSON))
                // Then: Returns 404
                .andExpect(status().isNotFound());
    }

    // ==================== GitHub Issue #379: Include History Parameter Tests ====================

    /**
     * Test: should_embedUsageHistory_when_includeHistoryParameter
     * Verifies GET /api/v1/topics?include=history embeds usage history in each topic.
     * GitHub Issue #379: Backend support for multi-topic heatmap visualization
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_embedUsageHistory_when_includeHistoryParameter() throws Exception {
        // Given: Two topics with usage history
        Topic topic1 = createTestTopic("Cloud Architecture", "technical", 85);
        Topic topic2 = createTestTopic("Leadership Skills", "management", 50);

        Event event1 = createTestEvent("BATbern56", EventWorkflowState.ARCHIVED);
        Event event2 = createTestEvent("BATbern57", EventWorkflowState.ARCHIVED);

        createTopicUsageHistory(
                topic1.getId(),
                event1.getId(),
                LocalDateTime.now().minusMonths(6),
                150,
                0.85
        );

        createTopicUsageHistory(
                topic2.getId(),
                event2.getId(),
                LocalDateTime.now().minusMonths(3),
                200,
                0.92
        );

        // When: GET /api/v1/topics with include=history
        mockMvc.perform(get("/api/v1/topics")
                .param("include", "history")
                .contentType(MediaType.APPLICATION_JSON))
                // Then: Returns topics with embedded usage history
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                // Topic 1 should have usageHistory array
                .andExpect(jsonPath("$.data[?(@.title == 'Cloud Architecture')].usageHistory").exists())
                .andExpect(jsonPath("$.data[?(@.title == 'Cloud Architecture')].usageHistory", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.title == 'Cloud Architecture')].usageHistory[0].eventCode").value("BATbern56"))
                .andExpect(jsonPath("$.data[?(@.title == 'Cloud Architecture')].usageHistory[0].usedDate").exists())
                .andExpect(jsonPath("$.data[?(@.title == 'Cloud Architecture')].usageHistory[0].attendance").value(150))
                .andExpect(jsonPath("$.data[?(@.title == 'Cloud Architecture')].usageHistory[0].engagementScore").value(0.85))
                // Topic 2 should have usageHistory array
                .andExpect(jsonPath("$.data[?(@.title == 'Leadership Skills')].usageHistory").exists())
                .andExpect(jsonPath("$.data[?(@.title == 'Leadership Skills')].usageHistory", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.title == 'Leadership Skills')].usageHistory[0].eventCode").value("BATbern57"));
    }

    /**
     * Test: should_notEmbedUsageHistory_when_includeParameterNotProvided
     * Verifies GET /api/v1/topics without include parameter does not embed history.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_notEmbedUsageHistory_when_includeParameterNotProvided() throws Exception {
        // Given: Topic with usage history
        Topic topic = createTestTopic("Cloud Architecture", "technical", 85);
        Event event = createTestEvent("BATbern56", EventWorkflowState.ARCHIVED);
        createTopicUsageHistory(
                topic.getId(),
                event.getId(),
                LocalDateTime.now().minusMonths(6),
                150,
                0.85
        );

        // When: GET /api/v1/topics without include parameter
        mockMvc.perform(get("/api/v1/topics")
                .contentType(MediaType.APPLICATION_JSON))
                // Then: Returns topics without usageHistory field
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].usageHistory").doesNotExist());
    }

    /**
     * Test: should_returnEmptyUsageHistory_when_topicNeverUsed
     * Verifies topics without usage history return empty array when include=history.
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_returnEmptyUsageHistory_when_topicNeverUsed() throws Exception {
        // Given: Topic without usage history
        createTestTopic("New Topic", "technical", 100);

        // When: GET /api/v1/topics with include=history
        mockMvc.perform(get("/api/v1/topics")
                .param("include", "history")
                .contentType(MediaType.APPLICATION_JSON))
                // Then: Returns topic with empty usageHistory array
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].usageHistory").isArray())
                .andExpect(jsonPath("$.data[0].usageHistory", hasSize(0)));
    }

    /**
     * Test: should_supportCommaSeperatedIncludes_when_multipleIncludesProvided
     * Verifies include parameter supports comma-separated values (e.g., "history,similarity").
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_supportCommaSeperatedIncludes_when_multipleIncludesProvided() throws Exception {
        // Given: Topic with usage history
        Topic topic = createTestTopic("Cloud Architecture", "technical", 85);
        Event event = createTestEvent("BATbern56", EventWorkflowState.ARCHIVED);
        createTopicUsageHistory(
                topic.getId(),
                event.getId(),
                LocalDateTime.now().minusMonths(6),
                150,
                0.85
        );

        // When: GET /api/v1/topics with include=history,similarity
        mockMvc.perform(get("/api/v1/topics")
                .param("include", "history,similarity")
                .contentType(MediaType.APPLICATION_JSON))
                // Then: Returns topics with both usageHistory and updated similarity scores
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].usageHistory").exists())
                .andExpect(jsonPath("$.data[0].similarityScores").exists());
    }

    // ==================== Helper Methods ====================

    private Topic createTestTopic(String title, String category, int stalenessScore) {
        Topic topic = new Topic();
        topic.setTitle(title);
        topic.setDescription("Test description for " + title);
        topic.setCategory(category);
        topic.setStalenessScore(stalenessScore);
        topic.setUsageCount(0);
        topic.setActive(true);
        topic.setCreatedDate(LocalDateTime.now());
        return topicRepository.save(topic);
    }

    private Event createTestEvent(String eventCode, EventWorkflowState workflowState) {
        Event event = new Event();
        event.setEventCode(eventCode);
        // Extract number from event code (e.g., "BATbern100" -> 100) or use default
        int eventNumber = eventCode.matches(".*\\d+$")
            ? Integer.parseInt(eventCode.replaceAll("\\D+", ""))
            : 999;
        event.setEventNumber(eventNumber);
        event.setTitle("Test Event for Topic Selection");
        event.setDate(Instant.now().plusSeconds(90 * 24 * 3600)); // 90 days from now
        event.setRegistrationDeadline(Instant.now().plusSeconds(60 * 24 * 3600)); // 60 days from now
        event.setVenueName("Test Venue");
        event.setVenueAddress("Test Address");
        event.setVenueCapacity(200);
        event.setOrganizerUsername("john.doe");
        event.setEventType(ch.batbern.events.dto.generated.EventType.FULL_DAY);
        event.setWorkflowState(workflowState);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        return eventRepository.save(event);
    }

    private TopicUsageHistory createTopicUsageHistory(
            java.util.UUID topicId,
            java.util.UUID eventId,
            LocalDateTime usedDate,
            Integer attendance,
            Double engagementScore) {
        TopicUsageHistory history = new TopicUsageHistory();
        history.setTopicId(topicId);
        history.setEventId(eventId);
        history.setUsedDate(usedDate);
        history.setAttendeeCount(attendance);
        history.setEngagementScore(engagementScore);
        return topicUsageHistoryRepository.save(history);
    }
}
