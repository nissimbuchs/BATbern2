package ch.batbern.events.controller;

import ch.batbern.events.domain.Topic;
import ch.batbern.events.dto.OverrideStalenesRequest;
import ch.batbern.events.dto.TopicListResponse;
import ch.batbern.events.dto.TopicRequest;
import ch.batbern.events.dto.TopicResponse;
import ch.batbern.events.service.TopicService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    /**
     * List all topics with optional filters (AC1).
     *
     * @param filter Optional JSON filter string (e.g., {"category":"technical"})
     * @param page Page number (default 1)
     * @param limit Page size (default 50)
     * @return Paginated list of topics
     */
    @GetMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TopicListResponse> getAllTopics(
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer limit) {

        // Parse filter JSON if provided
        String category = null;
        if (filter != null && !filter.isBlank()) {
            // Simple JSON parsing for category filter
            // Expected format: {"category":"technical"}
            if (filter.contains("\"category\"")) {
                String[] parts = filter.split("\"category\"\\s*:\\s*\"");
                if (parts.length > 1) {
                    category = parts[1].split("\"")[0];
                }
            }
        }

        List<Topic> topics = topicService.getAllTopics(category);

        // Apply pagination (simple in-memory pagination for now)
        int start = (page - 1) * limit;
        int end = Math.min(start + limit, topics.size());
        List<Topic> paginatedTopics = topics.subList(
            Math.min(start, topics.size()),
            Math.min(end, topics.size())
        );

        // Convert to DTOs
        List<TopicResponse> topicResponses = paginatedTopics.stream()
                .map(TopicResponse::from)
                .collect(Collectors.toList());

        // Build response with pagination metadata
        TopicListResponse response = new TopicListResponse(
                topicResponses,
                new TopicListResponse.PaginationMetadata(page, limit, (long) topics.size())
        );

        return ResponseEntity.ok(response);
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
