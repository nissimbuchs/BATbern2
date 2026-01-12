package ch.batbern.events.controller;

import ch.batbern.events.domain.Topic;
import ch.batbern.events.dto.generated.topics.CreateTopicRequest;
import ch.batbern.events.dto.generated.topics.OverrideStalenessRequest;
import ch.batbern.events.dto.generated.topics.TopicListResponse;
import ch.batbern.events.dto.TopicFilterRequest;
import ch.batbern.events.mapper.TopicMapper;
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
import java.util.stream.Collectors;

/**
 * REST controller for topic management (Story 5.2).
 *
 * ADR-003: All endpoints use topicCode (slug-format) as the external identifier.
 *
 * Endpoints:
 * - GET /api/v1/topics - List all topics with filters
 * - GET /api/v1/topics/{topicCode} - Get topic by code
 * - POST /api/v1/topics - Create new topic
 * - PUT /api/v1/topics/{topicCode}/override-staleness - Override staleness score
 * - GET /api/v1/topics/{topicCode}/similar - Get similar topics
 */
@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService topicService;
    private final TopicMapper topicMapper;
    private final ObjectMapper objectMapper;

    public TopicController(TopicService topicService, TopicMapper topicMapper, ObjectMapper objectMapper) {
        this.topicService = topicService;
        this.topicMapper = topicMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * List all topics with optional filters (AC1).
     *
     * Story 4.2 (BAT-109): Made public for archive filtering.
     * - Anonymous users: Get active topics only
     * - Organizers: Get all topics (active and inactive)
     *
     * @param filter Optional JSON filter string (e.g., {"category":"technical"})
     * @param page Page number (default 0 for Spring Data, but 1 for API consistency)
     * @param limit Page size (default 50)
     * @param sort Optional sort parameter (e.g., "stalenessScore,desc")
     * @param include Optional comma-separated includes (e.g., "history,similarity") - GitHub Issue #379
     * @return Paginated list of topics
     */
    @GetMapping
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

        // Get the topic list for processing
        final List<Topic> baseTopics = topicPage.getContent();

        // If include=similarity, recalculate similarity scores on-demand
        final List<Topic> topics;
        if (includeSimilarity) {
            topics = baseTopics.stream()
                    .map(topic -> {
                        topicService.calculateSimilarityForTopic(topic);
                        // Refresh topic from database to get updated similarity scores
                        return topicService.getTopicById(topic.getId()).orElse(topic);
                    })
                    .collect(Collectors.toList());
        } else {
            topics = baseTopics;
        }

        // Convert to DTOs (with optional history and similarity enrichment)
        List<ch.batbern.events.dto.generated.topics.Topic> topicDtos;
        if (includeHistory) {
            // Fetch and attach usage history for all topics (GitHub Issue #379)
            topicDtos = topicService.enrichTopicsWithUsageHistory(topics);
        } else if (includeSimilarity) {
            // Convert topics with similarity scores
            topicDtos = topics.stream()
                    .map(topic -> {
                        // Convert similarity scores from UUID to topicCode
                        var similarityScores = topicService.convertSimilarityScoresToDtos(
                                topic.getSimilarityScores()
                        );
                        return topicMapper.toDtoWithSimilarityScores(topic, similarityScores);
                    })
                    .collect(Collectors.toList());
        } else {
            // Simple conversion without history or similarity
            topicDtos = topics.stream()
                    .map(topicMapper::toDto)
                    .collect(Collectors.toList());
        }

        // Build response with pagination metadata (1-based for API)
        int totalPages = (int) Math.ceil((double) topicPage.getTotalElements() / limit);
        ch.batbern.shared.api.PaginationMetadata pagination = ch.batbern.shared.api.PaginationMetadata.builder()
                .page(page)
                .limit(limit)
                .totalItems(topicPage.getTotalElements())
                .totalPages(totalPages)
                .hasNext(page < totalPages)
                .hasPrev(page > 1)
                .build();
        TopicListResponse response = new TopicListResponse(topicDtos, pagination);

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
     * Get topic by code (ADR-003).
     *
     * @param topicCode Topic code (slug-format identifier)
     * @param include Optional comma-separated list of fields to include (e.g., "similarity")
     * @return Topic details
     */
    @GetMapping("/{topicCode}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ch.batbern.events.dto.generated.topics.Topic> getTopicByCode(
            @PathVariable String topicCode,
            @RequestParam(required = false) String include) {

        Optional<Topic> topicOpt = topicService.getTopicByCode(topicCode);
        if (topicOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Topic topic = topicOpt.get();

        // If include=similarity, recalculate similarity scores on-demand
        boolean includeSimilarity = include != null && include.contains("similarity");
        if (includeSimilarity) {
            topicService.calculateSimilarityForTopic(topic);
            // Refresh topic from database to get updated similarity scores
            topic = topicService.getTopicByCode(topicCode).orElse(topic);
        }

        // Convert to DTO (with similarity scores if requested)
        ch.batbern.events.dto.generated.topics.Topic dto;
        if (includeSimilarity) {
            var similarityScores = topicService.convertSimilarityScoresToDtos(topic.getSimilarityScores());
            dto = topicMapper.toDtoWithSimilarityScores(topic, similarityScores);
        } else {
            dto = topicMapper.toDto(topic);
        }

        return ResponseEntity.ok(dto);
    }

    /**
     * Create new topic (AC8).
     *
     * @param request Topic creation request
     * @return Created topic
     */
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ch.batbern.events.dto.generated.topics.Topic> createTopic(
            @Valid @RequestBody CreateTopicRequest request) {
        Topic topic = topicService.createTopic(
                request.getTitle(),
                request.getDescription(),
                request.getCategory()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(topicMapper.toDto(topic));
    }

    /**
     * Update existing topic (Story 5.2a - Edit Topic Feature).
     *
     * @param topicCode Topic code (slug-format identifier, ADR-003)
     * @param request Topic update request
     * @return Updated topic
     */
    @PutMapping("/{topicCode}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ch.batbern.events.dto.generated.topics.Topic> updateTopic(
            @PathVariable String topicCode,
            @Valid @RequestBody CreateTopicRequest request) {

        Topic topic = topicService.updateTopicByCode(
                topicCode,
                request.getTitle(),
                request.getDescription(),
                request.getCategory()
        );

        return ResponseEntity.ok(topicMapper.toDto(topic));
    }

    /**
     * Delete topic (Story 5.2a - Delete Topic Feature).
     * Only allowed if topic has never been used (no events attached).
     *
     * @param topicCode Topic code (slug-format identifier, ADR-003)
     * @return 204 No Content on success
     * @throws IllegalStateException if topic has been used
     */
    @DeleteMapping("/{topicCode}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteTopic(@PathVariable String topicCode) {
        topicService.deleteTopicByCode(topicCode);
        return ResponseEntity.noContent().build();
    }

    /**
     * Override staleness score with justification (AC7).
     *
     * @param topicCode Topic code (slug-format identifier, ADR-003)
     * @param request Override request with staleness score and justification
     * @return Updated topic
     */
    @PutMapping("/{topicCode}/override-staleness")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ch.batbern.events.dto.generated.topics.Topic> overrideStaleness(
            @PathVariable String topicCode,
            @Valid @RequestBody OverrideStalenessRequest request) {

        Topic topic = topicService.overrideStalenessByCode(
                topicCode,
                request.getStalenessScore(),
                request.getJustification()
        );

        return ResponseEntity.ok(topicMapper.toDto(topic));
    }

    /**
     * Get similar topics (>70% similarity) for duplicate detection (AC5).
     *
     * @param topicCode Topic code (slug-format identifier, ADR-003)
     * @return List of similar topics
     */
    @GetMapping("/{topicCode}/similar")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<ch.batbern.events.dto.generated.topics.Topic>> getSimilarTopics(
            @PathVariable String topicCode) {
        List<Topic> similarTopics = topicService.getSimilarTopicsByCode(topicCode);

        List<ch.batbern.events.dto.generated.topics.Topic> response = similarTopics.stream()
                .map(topicMapper::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get usage history for a topic (AC2).
     * Returns historical usage data for heat map visualization.
     *
     * @param topicCode Topic code (slug-format identifier, ADR-003)
     * @return List of usage history records
     */
    @GetMapping("/{topicCode}/usage-history")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<ch.batbern.events.dto.generated.topics.TopicUsageHistory>> getUsageHistory(
            @PathVariable String topicCode) {
        // Verify topic exists
        Optional<Topic> topic = topicService.getTopicByCode(topicCode);
        if (topic.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Fetch usage history with event details (GitHub Issue #379: returns eventNumber, no UUIDs)
        List<ch.batbern.events.dto.generated.topics.TopicUsageHistory> response =
                topicService.getUsageHistoryWithEventDetailsByCode(topicCode);

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
