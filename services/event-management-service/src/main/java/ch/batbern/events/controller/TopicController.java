package ch.batbern.events.controller;

import ch.batbern.events.domain.Topic;
import ch.batbern.events.dto.OverrideStalenesRequest;
import ch.batbern.events.dto.TopicFilterRequest;
import ch.batbern.events.dto.TopicListResponse;
import ch.batbern.events.dto.TopicRequest;
import ch.batbern.events.dto.TopicResponse;
import ch.batbern.events.dto.TopicUsageHistoryResponse;
import ch.batbern.events.service.TopicService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for topic management (Story 5.2).
 *
 * Endpoints:
 * - GET /api/v1/topics - List all topics with filters
 * - GET /api/v1/topics/{id} - Get topic by ID
 * - POST /api/v1/topics - Create new topic
 * - PUT /api/v1/topics/{id}/override-staleness - Override staleness score
 * - GET /api/v1/topics/{id}/similar - Get similar topics
 */
@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService topicService;
    private final ObjectMapper objectMapper;

    public TopicController(TopicService topicService, ObjectMapper objectMapper) {
        this.topicService = topicService;
        this.objectMapper = objectMapper;
    }

    /**
     * List all topics with optional filters (AC1).
     *
     * @param filter Optional JSON filter string (e.g., {"category":"technical"})
     * @param page Page number (default 0 for Spring Data, but 1 for API consistency)
     * @param limit Page size (default 50)
     * @param sort Optional sort parameter (e.g., "stalenessScore,desc")
     * @param include Optional comma-separated includes (e.g., "history,similarity") - GitHub Issue #379
     * @return Paginated list of topics
     */
    @GetMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TopicListResponse> getAllTopics(
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String include) {

        // Parse filter JSON using Jackson ObjectMapper
        String category = null;
        String status = null;
        if (filter != null && !filter.isBlank()) {
            try {
                TopicFilterRequest filterRequest = objectMapper.readValue(filter, TopicFilterRequest.class);
                category = filterRequest.getCategory();
                status = filterRequest.getStatus();
            } catch (JsonProcessingException e) {
                // Log warning and proceed with null filters
                System.err.println("Invalid filter JSON: " + e.getMessage());
            }
        }

        // Parse include parameter (comma-separated values)
        boolean includeHistory = include != null && include.contains("history");
        boolean includeSimilarity = include != null && include.contains("similarity");

        // Create Pageable for database-level pagination
        // Convert 1-based page to 0-based for Spring Data
        Pageable pageable = createPageable(page - 1, limit, sort);

        // Fetch paginated topics from service
        Page<Topic> topicPage = topicService.getAllTopics(category, status, pageable);

        // Convert to DTOs
        List<TopicResponse> topicResponses = topicPage.getContent().stream()
                .map(topic -> {
                    // If include=similarity, recalculate similarity scores on-demand
                    if (includeSimilarity) {
                        topicService.calculateSimilarityForTopic(topic);
                        // Refresh topic from database to get updated similarity scores
                        topic = topicService.getTopicById(topic.getId()).orElse(topic);
                    }
                    return TopicResponse.from(topic);
                })
                .collect(Collectors.toList());

        // If include=history, fetch and attach usage history for all topics (GitHub Issue #379)
        if (includeHistory) {
            topicResponses = topicService.enrichTopicsWithUsageHistory(topicResponses);
        }

        // Build response with pagination metadata (1-based for API)
        TopicListResponse response = new TopicListResponse(
                topicResponses,
                new TopicListResponse.PaginationMetadata(page, limit, topicPage.getTotalElements())
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Create Pageable from page, limit, and sort parameters.
     *
     * @param page Zero-based page number
     * @param limit Page size
     * @param sort Optional sort string (e.g., "stalenessScore,desc" or "-stalenessScore")
     * @return Pageable for database query
     */
    private Pageable createPageable(int page, int limit, String sort) {
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(page, limit);
        }

        // Handle both formats: "stalenessScore,desc" and "-stalenessScore"
        Sort.Direction direction = Sort.Direction.ASC;
        String property = sort;

        if (sort.startsWith("-")) {
            direction = Sort.Direction.DESC;
            property = sort.substring(1);
        } else if (sort.contains(",")) {
            String[] parts = sort.split(",");
            property = parts[0];
            if (parts.length > 1 && parts[1].equalsIgnoreCase("desc")) {
                direction = Sort.Direction.DESC;
            }
        }

        return PageRequest.of(page, limit, Sort.by(direction, property));
    }

    /**
     * Get topic by ID.
     *
     * @param id Topic ID
     * @param include Optional comma-separated list of fields to include (e.g., "similarity")
     * @return Topic details
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TopicResponse> getTopicById(
            @PathVariable UUID id,
            @RequestParam(required = false) String include) {

        Optional<Topic> topicOpt = topicService.getTopicById(id);
        if (topicOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Topic topic = topicOpt.get();

        // If include=similarity, recalculate similarity scores on-demand
        if (include != null && include.contains("similarity")) {
            topicService.calculateSimilarityForTopic(topic);
            // Refresh topic from database to get updated similarity scores
            topic = topicService.getTopicById(id).orElse(topic);
        }

        return ResponseEntity.ok(TopicResponse.from(topic));
    }

    /**
     * Create new topic (AC8).
     *
     * @param request Topic creation request
     * @return Created topic
     */
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TopicResponse> createTopic(@Valid @RequestBody TopicRequest request) {
        Topic topic = topicService.createTopic(
                request.getTitle(),
                request.getDescription(),
                request.getCategory()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TopicResponse.from(topic));
    }

    /**
     * Update existing topic (Story 5.2a - Edit Topic Feature).
     *
     * @param id Topic ID
     * @param request Topic update request
     * @return Updated topic
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TopicResponse> updateTopic(
            @PathVariable UUID id,
            @Valid @RequestBody TopicRequest request) {

        Topic topic = topicService.updateTopic(
                id,
                request.getTitle(),
                request.getDescription(),
                request.getCategory()
        );

        return ResponseEntity.ok(TopicResponse.from(topic));
    }

    /**
     * Delete topic (Story 5.2a - Delete Topic Feature).
     * Only allowed if topic has never been used (no events attached).
     *
     * @param id Topic ID
     * @return 204 No Content on success
     * @throws IllegalStateException if topic has been used
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteTopic(@PathVariable UUID id) {
        topicService.deleteTopic(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Override staleness score with justification (AC7).
     *
     * @param id Topic ID
     * @param request Override request with staleness score and justification
     * @return Updated topic
     */
    @PutMapping("/{id}/override-staleness")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TopicResponse> overrideStaleness(
            @PathVariable UUID id,
            @Valid @RequestBody OverrideStalenesRequest request) {

        Topic topic = topicService.overrideStaleness(
                id,
                request.getStalenessScore(),
                request.getJustification()
        );

        return ResponseEntity.ok(TopicResponse.from(topic));
    }

    /**
     * Get similar topics (>70% similarity) for duplicate detection (AC5).
     *
     * @param id Topic ID
     * @return List of similar topics
     */
    @GetMapping("/{id}/similar")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<TopicResponse>> getSimilarTopics(@PathVariable UUID id) {
        List<Topic> similarTopics = topicService.getSimilarTopics(id);

        List<TopicResponse> response = similarTopics.stream()
                .map(TopicResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get usage history for a topic (AC2).
     * Returns historical usage data for heat map visualization.
     *
     * @param id Topic ID
     * @return List of usage history records
     */
    @GetMapping("/{id}/usage-history")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<TopicUsageHistoryResponse>> getUsageHistory(@PathVariable UUID id) {
        // Verify topic exists
        Optional<Topic> topic = topicService.getTopicById(id);
        if (topic.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Fetch usage history with event details (GitHub Issue #379: returns eventNumber, no UUIDs)
        List<TopicUsageHistoryResponse> response = topicService.getUsageHistoryWithEventDetails(id);

        return ResponseEntity.ok(response);
    }

    /**
     * Update staleness scores for all topics (maintenance endpoint).
     *
     * @return Success message
     */
    @PostMapping("/recalculate-staleness")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<String> recalculateStaleness() {
        // Get all topics and update their staleness scores
        List<Topic> topics = topicService.getAllTopics(null);
        topics.forEach(topic -> topicService.updateTopicStaleness(topic.getId()));

        return ResponseEntity.ok("Staleness scores recalculated for " + topics.size() + " topics");
    }

    /**
     * Calculate similarity scores for all topics (maintenance endpoint, AC4).
     *
     * @return Success message
     */
    @PostMapping("/calculate-similarities")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<String> calculateSimilarities() {
        topicService.calculateAllSimilarities();
        return ResponseEntity.ok("Similarity scores calculated for all topics");
    }
}
