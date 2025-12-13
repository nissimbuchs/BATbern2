package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.repository.TopicRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
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

    @BeforeEach
    void setUp() {
        // Clean up topics before each test
        topicRepository.deleteAll();
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
                .andExpect(jsonPath("$.pagination.total").value(3));
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

        // When & Then: GET /api/v1/topics/{id} returns topic details
        mockMvc.perform(get("/api/v1/topics/{id}", topic.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(topic.getId().toString()))
                .andExpect(jsonPath("$.title").value("Cloud Native"))
                .andExpect(jsonPath("$.category").value("technical"))
                .andExpect(jsonPath("$.stalenessScore").value(85))
                .andExpect(jsonPath("$.status").value("available"));
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
        mockMvc.perform(get("/api/v1/topics/{id}", topic1.getId())
                .param("include", "similarity")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Then: Similarity scores are calculated
                .andExpect(jsonPath("$.similarityScores").isArray())
                .andExpect(jsonPath("$.similarityScores", hasSize(greaterThan(0))))
                // Verify high similarity due to overlapping terms (cloud, native)
                .andExpect(jsonPath("$.similarityScores[?(@.topicId == '" + topic2.getId() + "')].score")
                    .value(hasItem(greaterThan(0.5)))); // >50% similar
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
        mockMvc.perform(get("/api/v1/topics/{id}", recentTopic.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stalenessScore").value(10))
                .andExpect(jsonPath("$.stalenessScore", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.stalenessScore", lessThanOrEqualTo(100)));

        mockMvc.perform(get("/api/v1/topics/{id}", staleTopic.getId())
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
        // Given: Topic with high similarity to recent topic (would trigger warning)
        Topic similarTopic = createTestTopic("Cloud Native Advanced", "technical", 40);

        // When: Select topic with justification override
        Map<String, Object> request = new HashMap<>();
        request.put("topicId", similarTopic.getId().toString());
        request.put("justification", "Partner explicitly requested this specific topic");

        // Then: Override is accepted with justification
        // Note: This test will be fully validated in Task 3a (Workflow Integration)
        // For now, we verify the endpoint accepts the request structure
        mockMvc.perform(post("/api/v1/events/{eventCode}/topics", "BATbern999")
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
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("AI-Powered DevOps"))
                .andExpect(jsonPath("$.category").value("technical"))
                // AC8: New topics have max staleness score (100 = safe to use)
                .andExpect(jsonPath("$.stalenessScore").value(100))
                .andExpect(jsonPath("$.status").value("available"));
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
        String topicId = (String) createdTopic.get("id");

        mockMvc.perform(get("/api/v1/topics/{id}", topicId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Kubernetes Best Practices"))
                .andExpect(jsonPath("$.stalenessScore").value(100))
                .andExpect(jsonPath("$.status").value("available"));
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
}
