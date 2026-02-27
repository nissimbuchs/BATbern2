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
 * Staleness is now computed live from topic_usage_history — topics table no longer
 * stores staleness_score or last_used_date.
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
        topicUsageHistoryRepository.deleteAll();
        topicRepository.deleteAll();
        eventRepository.deleteAll();
    }

    // ==================== AC1 Tests: List all topics ====================

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_returnAllTopics_when_GETApiTopicsCalled() throws Exception {
        createTestTopic("Cloud Native Architecture", "technical");
        createTestTopic("Leadership Skills", "management");
        createTestTopic("AI/ML Fundamentals", "technical");

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

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_filterTopicsByCategory_when_categoryFilterProvided() throws Exception {
        createTestTopic("Cloud Native", "technical");
        createTestTopic("Leadership", "management");
        createTestTopic("Kubernetes", "technical");

        String filter = "{\"category\":\"technical\"}";

        mockMvc.perform(get("/api/v1/topics")
                .param("filter", filter)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].category", everyItem(equalTo("technical"))));
    }

    /**
     * Sort by staleness: topics with older usage history sort higher (desc = safest first).
     * Topic A: used 30 months ago → staleness 100
     * Topic B: used 6 months ago  → staleness 25
     * Topic C: used 15 months ago → staleness ~63
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_sortByStalenessScore_when_sortParameterProvided() throws Exception {
        Topic topicA = createTestTopic("Stale Topic", "technical");
        Topic topicB = createTestTopic("Recent Topic", "technical");
        Topic topicC = createTestTopic("Moderate Topic", "technical");

        Event eventA = createTestEvent("BATbern200", EventWorkflowState.ARCHIVED);
        Event eventB = createTestEvent("BATbern201", EventWorkflowState.ARCHIVED);
        Event eventC = createTestEvent("BATbern202", EventWorkflowState.ARCHIVED);

        createTopicUsageHistory(topicA.getId(), eventA.getId(), LocalDateTime.now().minusMonths(30), null, null);
        createTopicUsageHistory(topicB.getId(), eventB.getId(), LocalDateTime.now().minusMonths(6), null, null);
        createTopicUsageHistory(topicC.getId(), eventC.getId(), LocalDateTime.now().minusMonths(15), null, null);

        mockMvc.perform(get("/api/v1/topics")
                .param("sort", "-stalenessScore")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                // Descending: stale (100) → moderate (~63) → recent (~25)
                .andExpect(jsonPath("$.data[0].title").value("Stale Topic"))
                .andExpect(jsonPath("$.data[2].title").value("Recent Topic"))
                // All scores in valid range
                .andExpect(jsonPath("$.data[0].stalenessScore", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data[0].stalenessScore", lessThanOrEqualTo(100)));
    }

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_returnTopicWithDetails_when_getTopicByIdCalled() throws Exception {
        Topic topic = createTestTopic("Cloud Native", "technical");

        mockMvc.perform(get("/api/v1/topics/{topicCode}", topic.getTopicCode())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicCode").value(topic.getTopicCode()))
                .andExpect(jsonPath("$.title").value("Cloud Native"))
                .andExpect(jsonPath("$.category").value("technical"))
                // No usage history → staleness 100, status AVAILABLE
                .andExpect(jsonPath("$.stalenessScore").value(100))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    // ==================== AC4 Tests: Similarity Score ====================

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_calculateSimilarityScore_when_twoTopicsCompared() throws Exception {
        Topic topic1 = createTestTopic("Cloud Native Architecture Patterns", "technical");
        Topic topic2 = createTestTopic("Cloud Native Design Best Practices", "technical");

        mockMvc.perform(get("/api/v1/topics/{topicCode}", topic1.getTopicCode())
                .param("include", "similarity")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.similarityScores").isArray())
                .andExpect(jsonPath("$.similarityScores", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.similarityScores[0].score").isNumber())
                .andExpect(jsonPath("$.similarityScores[0].topicCode").value(topic2.getTopicCode()));
    }

    // ==================== AC6 Tests: Staleness Score from History ====================

    /**
     * Topics with recent usage history should have low staleness (red zone).
     * Topics with no usage history should have staleness 100 (green zone).
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_calculateStalenessScore_when_topicUsageHistoryProvided() throws Exception {
        Topic recentTopic = createTestTopic("Recent", "technical");
        Topic staleTopic = createTestTopic("Stale", "technical");

        Event recentEvent = createTestEvent("BATbern301", EventWorkflowState.ARCHIVED);
        createTopicUsageHistory(recentTopic.getId(), recentEvent.getId(),
                LocalDateTime.now().minusMonths(3), null, null);
        // staleTopic has no history → staleness 100

        // Recent topic: used 3 months ago → staleness ~13 (red)
        mockMvc.perform(get("/api/v1/topics/{topicCode}", recentTopic.getTopicCode())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stalenessScore", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.stalenessScore", lessThanOrEqualTo(49)))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));

        // Stale topic: never used → staleness 100
        mockMvc.perform(get("/api/v1/topics/{topicCode}", staleTopic.getTopicCode())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stalenessScore").value(100))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    // ==================== AC8 Tests: Topic Creation ====================

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_createNewTopic_when_validDataProvided() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("title", "AI-Powered DevOps");
        request.put("description", "Exploring AI applications in DevOps workflows");
        request.put("category", "technical");

        mockMvc.perform(post("/api/v1/topics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.topicCode").exists())
                .andExpect(jsonPath("$.title").value("AI-Powered DevOps"))
                .andExpect(jsonPath("$.category").value("technical"))
                // New topics have no history → staleness 100 (safe to use)
                .andExpect(jsonPath("$.stalenessScore").value(100))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return400_when_titleMissing() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("category", "technical");

        mockMvc.perform(post("/api/v1/topics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_persistTopicEntity_when_topicServiceCreateCalled() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("title", "Kubernetes Best Practices");
        request.put("description", "Deep dive into K8s patterns");
        request.put("category", "technical");

        String response = mockMvc.perform(post("/api/v1/topics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

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

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_returnUsageHistory_when_topicHasBeenUsed() throws Exception {
        Topic topic = createTestTopic("Cloud Architecture", "technical");
        Event event1 = createTestEvent("BATbern100", EventWorkflowState.CREATED);
        Event event2 = createTestEvent("BATbern101", EventWorkflowState.CREATED);

        createTopicUsageHistory(topic.getId(), event1.getId(),
                LocalDateTime.now().minusMonths(6), 150, 0.85);
        createTopicUsageHistory(topic.getId(), event2.getId(),
                LocalDateTime.now().minusMonths(12), 200, 0.92);

        mockMvc.perform(get("/api/v1/topics/{topicCode}/usage-history", topic.getTopicCode())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventNumber").exists())
                .andExpect(jsonPath("$[0].eventCode").exists())
                .andExpect(jsonPath("$[0].eventDate").exists())
                .andExpect(jsonPath("$[0].usedDate").exists())
                .andExpect(jsonPath("$[0].attendance").exists())
                .andExpect(jsonPath("$[0].engagementScore").exists())
                // Most recent first: 6 months ago (attendance=150) before 12 months ago
                .andExpect(jsonPath("$[0].attendance").value(150))
                .andExpect(jsonPath("$[1].attendance").value(200));
    }

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_returnEmptyArray_when_topicHasNoUsageHistory() throws Exception {
        Topic topic = createTestTopic("New Topic", "technical");

        mockMvc.perform(get("/api/v1/topics/{topicCode}/usage-history", topic.getTopicCode())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return404_when_topicNotFoundForUsageHistory() throws Exception {
        mockMvc.perform(get("/api/v1/topics/{id}/usage-history",
                        "123e4567-e89b-12d3-a456-426614174000")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ==================== GitHub Issue #379: Include History ====================

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_embedUsageHistory_when_includeHistoryParameter() throws Exception {
        Topic topic1 = createTestTopic("Cloud Architecture", "technical");
        Topic topic2 = createTestTopic("Leadership Skills", "management");

        Event event1 = createTestEvent("BATbern56", EventWorkflowState.ARCHIVED);
        Event event2 = createTestEvent("BATbern57", EventWorkflowState.ARCHIVED);

        createTopicUsageHistory(topic1.getId(), event1.getId(),
                LocalDateTime.now().minusMonths(6), 150, 0.85);
        createTopicUsageHistory(topic2.getId(), event2.getId(),
                LocalDateTime.now().minusMonths(3), 200, 0.92);

        mockMvc.perform(get("/api/v1/topics")
                .param("include", "history")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[?(@.title == 'Cloud Architecture')].usageHistory").exists())
                .andExpect(jsonPath("$.data[?(@.title == 'Cloud Architecture')].usageHistory", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.title == 'Cloud Architecture')].usageHistory[0].eventCode")
                        .value("BATbern56"))
                .andExpect(jsonPath("$.data[?(@.title == 'Cloud Architecture')].usageHistory[0].attendance")
                        .value(150))
                .andExpect(jsonPath("$.data[?(@.title == 'Leadership Skills')].usageHistory[0].eventCode")
                        .value("BATbern57"));
    }

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_notEmbedUsageHistory_when_includeParameterNotProvided() throws Exception {
        Topic topic = createTestTopic("Cloud Architecture", "technical");
        Event event = createTestEvent("BATbern56", EventWorkflowState.ARCHIVED);
        createTopicUsageHistory(topic.getId(), event.getId(),
                LocalDateTime.now().minusMonths(6), 150, 0.85);

        mockMvc.perform(get("/api/v1/topics")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].usageHistory").doesNotExist());
    }

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_returnEmptyUsageHistory_when_topicNeverUsed() throws Exception {
        createTestTopic("New Topic", "technical");

        mockMvc.perform(get("/api/v1/topics")
                .param("include", "history")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].usageHistory").isArray())
                .andExpect(jsonPath("$.data[0].usageHistory", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_supportCommaSeperatedIncludes_when_multipleIncludesProvided() throws Exception {
        Topic topic = createTestTopic("Cloud Architecture", "technical");
        Event event = createTestEvent("BATbern56", EventWorkflowState.ARCHIVED);
        createTopicUsageHistory(topic.getId(), event.getId(),
                LocalDateTime.now().minusMonths(6), 150, 0.85);

        mockMvc.perform(get("/api/v1/topics")
                .param("include", "history,similarity")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].usageHistory").exists())
                .andExpect(jsonPath("$.data[0].similarityScores").exists());
    }

    // ==================== AC7: Select topic with justification ====================

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_allowOverrideWithJustification_when_similarityScoreHigh() throws Exception {
        Event event = createTestEvent("BATbern999", EventWorkflowState.CREATED);
        Topic similarTopic = createTestTopic("Cloud Native Advanced", "technical");

        SelectTopicForEventRequest request = new SelectTopicForEventRequest()
            .topicCode(similarTopic.getTopicCode())
            .justification("Partner explicitly requested this specific topic");

        mockMvc.perform(post("/api/v1/events/{eventCode}/topics", event.getEventCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // ==================== Helper Methods ====================

    private Topic createTestTopic(String title, String category) {
        Topic topic = new Topic();
        topic.setTitle(title);
        topic.setDescription("Test description for " + title);
        topic.setCategory(category);
        topic.setActive(true);
        topic.setCreatedDate(LocalDateTime.now());
        return topicRepository.save(topic);
    }

    private Event createTestEvent(String eventCode, EventWorkflowState workflowState) {
        Event event = new Event();
        event.setEventCode(eventCode);
        int eventNumber = eventCode.matches(".*\\d+$")
            ? Integer.parseInt(eventCode.replaceAll("\\D+", ""))
            : 999;
        event.setEventNumber(eventNumber);
        event.setTitle("Test Event for Topic Selection");
        event.setDate(Instant.now().plusSeconds(90 * 24 * 3600));
        event.setRegistrationDeadline(Instant.now().plusSeconds(60 * 24 * 3600));
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
