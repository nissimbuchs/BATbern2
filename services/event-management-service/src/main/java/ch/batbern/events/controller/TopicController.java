package ch.batbern.events.controller;

import ch.batbern.events.domain.Topic;
import ch.batbern.events.dto.generated.topics.CreateTopicRequest;
import ch.batbern.events.dto.generated.topics.TopicListResponse;
import ch.batbern.events.dto.TopicFilterRequest;
import ch.batbern.events.mapper.TopicMapper;
import ch.batbern.events.service.StalenessScoreService;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
 * - PUT /api/v1/topics/{topicCode} - Update topic
 * - DELETE /api/v1/topics/{topicCode} - Delete topic
 * - GET /api/v1/topics/{topicCode}/similar - Get similar topics
 * - GET /api/v1/topics/{topicCode}/usage-history - Get usage history
 */
@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService topicService;
    private final TopicMapper topicMapper;
    private final StalenessScoreService stalenessScoreService;
    private final ObjectMapper objectMapper;

    public TopicController(
            TopicService topicService,
            TopicMapper topicMapper,
            StalenessScoreService stalenessScoreService,
            ObjectMapper objectMapper) {
        this.topicService = topicService;
        this.topicMapper = topicMapper;
        this.stalenessScoreService = stalenessScoreService;
        this.objectMapper = objectMapper;
    }

    /**
     * List all topics with optional filters (AC1).
     *
     * Story 4.2 (BAT-109): Made public for archive filtering.
     * - Anonymous users: Get active topics only
     * - Organizers: Get all topics (active and inactive)
     *
     * @param filter  Optional JSON filter string (e.g., {"category":"technical"})
     * @param page    Page number (1-based for API)
     * @param limit   Page size (default 50)
     * @param sort    Optional sort parameter (e.g., "stalenessScore,desc")
     * @param include Optional comma-separated includes (e.g., "history,similarity")
     * @return Paginated list of topics
     */
    @GetMapping
    public ResponseEntity<TopicListResponse> getAllTopics(
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String include) {

        // Parse filter JSON
        String category = null;
        String status = null;
        if (filter != null && !filter.isBlank()) {
            try {
                TopicFilterRequest filterRequest = objectMapper.readValue(filter, TopicFilterRequest.class);
                category = filterRequest.getCategory();
                status = filterRequest.getStatus();
            } catch (JsonProcessingException e) {
                System.err.println("Invalid filter JSON: " + e.getMessage());
            }
        }

        boolean includeHistory = include != null && include.contains("history");
        boolean includeSimilarity = include != null && include.contains("similarity");

        // Staleness sort is handled in-memory; pass null sort to DB for that case
        boolean sortByStaleness = sort != null && sort.contains("stalenessScore");
        Pageable pageable = createPageable(page - 1, limit, sortByStaleness ? null : sort);

        Page<Topic> topicPage = topicService.getAllTopics(category, status, pageable);

        final List<Topic> baseTopics = topicPage.getContent();

        // Recalculate similarity on-demand if requested
        final List<Topic> topics;
        if (includeSimilarity) {
            topics = baseTopics.stream()
                    .map(topic -> {
                        topicService.calculateSimilarityForTopic(topic);
                        return topicService.getTopicById(topic.getId()).orElse(topic);
                    })
                    .collect(Collectors.toList());
        } else {
            topics = baseTopics;
        }

        // Compute staleness for all topics in one batch query
        Map<UUID, StalenessScoreService.StalenessData> stalenessMap =
                stalenessScoreService.computeStalenessDataBatch(topics);

        // Apply in-memory staleness sort if requested
        List<Topic> sortedTopics = topics;
        if (sortByStaleness) {
            boolean desc = sort == null || !sort.endsWith("asc");
            sortedTopics = topics.stream()
                    .sorted((a, b) -> {
                        int sa = stalenessMap.getOrDefault(a.getId(),
                                StalenessScoreService.StalenessData.NEVER_USED).staleness();
                        int sb = stalenessMap.getOrDefault(b.getId(),
                                StalenessScoreService.StalenessData.NEVER_USED).staleness();
                        return desc ? Integer.compare(sb, sa) : Integer.compare(sa, sb);
                    })
                    .collect(Collectors.toList());
        }

        // Convert to DTOs
        List<ch.batbern.events.dto.generated.topics.Topic> topicDtos;
        if (includeHistory) {
            // enrichTopicsWithUsageHistory computes staleness from already-fetched history
            topicDtos = topicService.enrichTopicsWithUsageHistory(sortedTopics);
        } else if (includeSimilarity) {
            topicDtos = sortedTopics.stream()
                    .map(topic -> {
                        var sd = stalenessMap.getOrDefault(topic.getId(),
                                StalenessScoreService.StalenessData.NEVER_USED);
                        var similarityScores = topicService.convertSimilarityScoresToDtos(
                                topic.getSimilarityScores());
                        return topicMapper.toDtoWithSimilarityScores(
                                topic, similarityScores, sd.staleness(), sd.lastUsedDate());
                    })
                    .collect(Collectors.toList());
        } else {
            topicDtos = sortedTopics.stream()
                    .map(topic -> {
                        var sd = stalenessMap.getOrDefault(topic.getId(),
                                StalenessScoreService.StalenessData.NEVER_USED);
                        return topicMapper.toDto(topic, sd.staleness(), sd.lastUsedDate());
                    })
                    .collect(Collectors.toList());
        }

        // Build pagination response
        int totalPages = (int) Math.ceil((double) topicPage.getTotalElements() / limit);
        ch.batbern.shared.api.PaginationMetadata pagination = ch.batbern.shared.api.PaginationMetadata.builder()
                .page(page)
                .limit(limit)
                .totalItems(topicPage.getTotalElements())
                .totalPages(totalPages)
                .hasNext(page < totalPages)
                .hasPrev(page > 1)
                .build();

        return ResponseEntity.ok(new TopicListResponse(topicDtos, pagination));
    }

    private Pageable createPageable(int page, int limit, String sort) {
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(page, limit);
        }
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

        boolean includeSimilarity = include != null && include.contains("similarity");
        if (includeSimilarity) {
            topicService.calculateSimilarityForTopic(topic);
            topic = topicService.getTopicByCode(topicCode).orElse(topic);
        }

        StalenessScoreService.StalenessData sd = stalenessScoreService.computeStalenessData(topic);

        ch.batbern.events.dto.generated.topics.Topic dto;
        if (includeSimilarity) {
            var similarityScores = topicService.convertSimilarityScoresToDtos(topic.getSimilarityScores());
            dto = topicMapper.toDtoWithSimilarityScores(
                    topic, similarityScores, sd.staleness(), sd.lastUsedDate());
        } else {
            dto = topicMapper.toDto(topic, sd.staleness(), sd.lastUsedDate());
        }

        return ResponseEntity.ok(dto);
    }

    /**
     * Create new topic (AC8).
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
        // New topic has no usage history → staleness = 100
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(topicMapper.toDto(topic, 100, null));
    }

    /**
     * Update existing topic (Story 5.2a).
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
        StalenessScoreService.StalenessData sd = stalenessScoreService.computeStalenessData(topic);
        return ResponseEntity.ok(topicMapper.toDto(topic, sd.staleness(), sd.lastUsedDate()));
    }

    /**
     * Delete topic (Story 5.2a).
     */
    @DeleteMapping("/{topicCode}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteTopic(@PathVariable String topicCode) {
        topicService.deleteTopicByCode(topicCode);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get similar topics (>70% similarity) for duplicate detection (AC5).
     */
    @GetMapping("/{topicCode}/similar")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<ch.batbern.events.dto.generated.topics.Topic>> getSimilarTopics(
            @PathVariable String topicCode) {
        List<Topic> similarTopics = topicService.getSimilarTopicsByCode(topicCode);
        Map<UUID, StalenessScoreService.StalenessData> stalenessMap =
                stalenessScoreService.computeStalenessDataBatch(similarTopics);

        List<ch.batbern.events.dto.generated.topics.Topic> response = similarTopics.stream()
                .map(t -> {
                    var sd = stalenessMap.getOrDefault(t.getId(),
                            StalenessScoreService.StalenessData.NEVER_USED);
                    return topicMapper.toDto(t, sd.staleness(), sd.lastUsedDate());
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get usage history for a topic (AC2).
     */
    @GetMapping("/{topicCode}/usage-history")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<ch.batbern.events.dto.generated.topics.TopicUsageHistory>> getUsageHistory(
            @PathVariable String topicCode) {
        Optional<Topic> topic = topicService.getTopicByCode(topicCode);
        if (topic.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(
                topicService.getUsageHistoryWithEventDetailsByCode(topicCode));
    }

    /**
     * Calculate similarity scores for all topics (maintenance endpoint, AC4).
     */
    @PostMapping("/calculate-similarities")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<String> calculateSimilarities() {
        topicService.calculateAllSimilarities();
        return ResponseEntity.ok("Similarity scores calculated for all topics");
    }
}
