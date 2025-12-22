package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.domain.TopicUsageHistory;
import ch.batbern.events.dto.TopicUsageHistoryWithEventDetails;
import ch.batbern.events.mapper.TopicMapper;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.TopicRepository;
import ch.batbern.events.repository.TopicUsageHistoryRepository;
import ch.batbern.shared.types.EventWorkflowState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for topic management and similarity calculation (Story 5.2).
 *
 * Responsibilities:
 * - Topic CRUD operations
 * - Staleness score calculation and updates
 * - Similarity calculation using TF-IDF and cosine similarity
 * - Duplicate detection (>70% similarity threshold)
 * - Topic selection for events with workflow integration (Story 5.2 AC14-16)
 */
@Service
@Transactional
public class TopicService {

    private final TopicRepository topicRepository;
    private final TopicUsageHistoryRepository topicUsageHistoryRepository;
    private final StalenessScoreService stalenessScoreService;
    private final SimilarityCalculationService similarityCalculationService;
    private final EventRepository eventRepository;
    private final EventWorkflowStateMachine eventWorkflowStateMachine;
    private final TopicMapper topicMapper;

    public TopicService(
            TopicRepository topicRepository,
            TopicUsageHistoryRepository topicUsageHistoryRepository,
            StalenessScoreService stalenessScoreService,
            SimilarityCalculationService similarityCalculationService,
            EventRepository eventRepository,
            EventWorkflowStateMachine eventWorkflowStateMachine,
            TopicMapper topicMapper) {
        this.topicRepository = topicRepository;
        this.topicUsageHistoryRepository = topicUsageHistoryRepository;
        this.stalenessScoreService = stalenessScoreService;
        this.similarityCalculationService = similarityCalculationService;
        this.eventRepository = eventRepository;
        this.eventWorkflowStateMachine = eventWorkflowStateMachine;
        this.topicMapper = topicMapper;
    }

    /**
     * Get all topics with optional category and status filters, with pagination support.
     *
     * @param category Optional category filter
     * @param status Optional status filter:
     *               - "active"/"inactive" for active flag (legacy)
     *               - "available" for staleness >= 70 (green zone)
     *               - "caution" for staleness 40-69 (yellow zone)
     *               - "unavailable" for staleness < 40 (red zone)
     * @param pageable Pagination and sort parameters
     * @return Page of topics matching filters
     */
    @Transactional(readOnly = true)
    public Page<Topic> getAllTopics(String category, String status, Pageable pageable) {
        // Check if status is staleness-based filter (Story 5.2a - Fix #5)
        if (status != null && !status.isBlank()) {
            if (status.equalsIgnoreCase("available")) {
                // Green zone: staleness >= 70 (safe to use)
                return topicRepository.findByCategoryAndStalenessRange(
                    category, 70, 100, pageable
                );
            } else if (status.equalsIgnoreCase("caution")) {
                // Yellow zone: staleness 40-69 (use with caution)
                return topicRepository.findByCategoryAndStalenessRange(
                    category, 40, 69, pageable
                );
            } else if (status.equalsIgnoreCase("unavailable")) {
                // Red zone: staleness < 40 (too recent)
                return topicRepository.findByCategoryAndStalenessRange(
                    category, 0, 39, pageable
                );
            }
        }

        // Legacy: Convert status string to Boolean for active flag
        Boolean active = null;
        if (status != null && !status.isBlank()) {
            if (status.equalsIgnoreCase("active")) {
                active = true;
            } else if (status.equalsIgnoreCase("inactive")) {
                active = false;
            }
        }

        // Use repository method with database-level filtering and pagination
        return topicRepository.findByFilters(category, active, pageable);
    }

    /**
     * Get all active topics (legacy method for backward compatibility).
     *
     * @param category Optional category filter
     * @return List of topics sorted by staleness score (descending)
     */
    @Transactional(readOnly = true)
    public List<Topic> getAllTopics(String category) {
        if (category != null && !category.isBlank()) {
            return topicRepository.findByCategory(category);
        }
        return topicRepository.findAllActive();
    }

    /**
     * Get topics with staleness score above threshold.
     *
     * @param threshold Minimum staleness score (0-100)
     * @return List of topics meeting threshold
     */
    @Transactional(readOnly = true)
    public List<Topic> getTopicsByStalenessThreshold(int threshold) {
        return topicRepository.findByStalenessScoreGreaterThanEqual(threshold);
    }

    /**
     * Get topic by ID (internal use).
     *
     * @param id Topic ID
     * @return Optional containing topic if found
     */
    @Transactional(readOnly = true)
    public Optional<Topic> getTopicById(UUID id) {
        return topicRepository.findById(id);
    }

    /**
     * Get topic by topicCode (ADR-003 external identifier).
     *
     * @param topicCode Topic code (slug-format)
     * @return Optional containing topic if found
     */
    @Transactional(readOnly = true)
    public Optional<Topic> getTopicByCode(String topicCode) {
        return topicRepository.findByTopicCode(topicCode);
    }

    /**
     * Get usage history for a specific topic.
     * Used for heat map visualization (AC2) and usage pattern analysis.
     *
     * @param topicId Topic ID
     * @return List of usage history records, ordered by usage date descending
     */
    @Transactional(readOnly = true)
    public List<TopicUsageHistory> getUsageHistory(UUID topicId) {
        return topicUsageHistoryRepository.findByTopicIdOrderByUsedDateDesc(topicId);
    }

    /**
     * Get usage history for a specific topic with event details - by ID.
     * GitHub Issue #379: Returns eventNumber instead of UUID per architectural requirement.
     *
     * @param topicId Topic ID
     * @return List of usage history with event details (eventNumber, eventCode, eventDate)
     */
    @Transactional(readOnly = true)
    public List<ch.batbern.events.dto.generated.topics.TopicUsageHistory> getUsageHistoryWithEventDetails(UUID topicId) {
        // Use the efficient single-query method
        List<TopicUsageHistoryWithEventDetails> history =
                topicUsageHistoryRepository.findUsageHistoryWithEventDetailsByTopicIds(List.of(topicId));

        // Convert to generated DTOs
        return history.stream()
                .map(h -> {
                    ch.batbern.events.dto.generated.topics.TopicUsageHistory dto =
                            new ch.batbern.events.dto.generated.topics.TopicUsageHistory();
                    dto.setEventNumber(h.getEventNumber());
                    dto.setEventCode(h.getEventCode());
                    dto.setEventDate(h.getEventDate() != null ?
                            java.time.LocalDate.ofInstant(h.getEventDate(), java.time.ZoneId.systemDefault()) : null);
                    dto.setUsedDate(h.getUsedDate() != null ?
                            h.getUsedDate().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime() : null);
                    dto.setAttendance(h.getAttendeeCount());
                    dto.setEngagementScore(h.getEngagementScore() != null ?
                            h.getEngagementScore().floatValue() : null);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get usage history for a specific topic with event details - by code (ADR-003).
     * GitHub Issue #379: Returns eventNumber instead of UUID per architectural requirement.
     *
     * @param topicCode Topic code (slug-format)
     * @return List of usage history with event details (eventNumber, eventCode, eventDate)
     */
    @Transactional(readOnly = true)
    public List<ch.batbern.events.dto.generated.topics.TopicUsageHistory> getUsageHistoryWithEventDetailsByCode(String topicCode) {
        Topic topic = topicRepository.findByTopicCode(topicCode)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicCode));

        // Use the efficient single-query method
        List<TopicUsageHistoryWithEventDetails> history =
                topicUsageHistoryRepository.findUsageHistoryWithEventDetailsByTopicIds(List.of(topic.getId()));

        // Convert to generated DTOs
        return history.stream()
                .map(h -> {
                    ch.batbern.events.dto.generated.topics.TopicUsageHistory dto =
                            new ch.batbern.events.dto.generated.topics.TopicUsageHistory();
                    dto.setEventNumber(h.getEventNumber());
                    dto.setEventCode(h.getEventCode());
                    dto.setEventDate(h.getEventDate() != null ?
                            java.time.LocalDate.ofInstant(h.getEventDate(), java.time.ZoneId.systemDefault()) : null);
                    dto.setUsedDate(h.getUsedDate() != null ?
                            h.getUsedDate().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime() : null);
                    dto.setAttendance(h.getAttendeeCount());
                    dto.setEngagementScore(h.getEngagementScore() != null ?
                            h.getEngagementScore().floatValue() : null);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Create new topic.
     *
     * @param title Topic title
     * @param description Topic description
     * @param category Topic category
     * @return Created topic
     */
    public Topic createTopic(String title, String description, String category) {
        Topic topic = new Topic();
        topic.setTitle(title);
        topic.setDescription(description);
        topic.setCategory(category);
        topic.setCreatedDate(LocalDateTime.now());
        topic.setUsageCount(0);
        topic.setStalenessScore(100); // New topics have max staleness (safe to use)
        topic.setActive(true);

        Topic savedTopic = topicRepository.save(topic);

        // Calculate similarity scores against all existing topics
        calculateSimilarityScoresForTopic(savedTopic);

        return savedTopic;
    }

    /**
     * Update existing topic by ID (internal use).
     *
     * @param topicId Topic ID
     * @param title Updated title
     * @param description Updated description
     * @param category Updated category
     * @return Updated topic
     */
    public Topic updateTopic(UUID topicId, String title, String description, String category) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));

        topic.setTitle(title);
        topic.setDescription(description);
        topic.setCategory(category);

        Topic savedTopic = topicRepository.save(topic);

        // Recalculate similarity scores after update
        calculateSimilarityScoresForTopic(savedTopic);

        return savedTopic;
    }

    /**
     * Update existing topic by code (ADR-003).
     *
     * @param topicCode Topic code (slug-format)
     * @param title Updated title
     * @param description Updated description
     * @param category Updated category
     * @return Updated topic
     */
    public Topic updateTopicByCode(String topicCode, String title, String description, String category) {
        Topic topic = topicRepository.findByTopicCode(topicCode)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicCode));

        topic.setTitle(title);
        topic.setDescription(description);
        topic.setCategory(category);
        // Note: topicCode could be regenerated if title changes significantly
        // For now, we keep the original topicCode for URL stability

        Topic savedTopic = topicRepository.save(topic);

        // Recalculate similarity scores after update
        calculateSimilarityScoresForTopic(savedTopic);

        return savedTopic;
    }

    /**
     * Delete topic by ID (internal use).
     * Only allowed if topic has never been used (no events attached).
     *
     * @param topicId Topic ID
     * @throws IllegalArgumentException if topic not found
     * @throws IllegalStateException if topic has been used (usageCount > 0 or lastUsedDate != null)
     */
    public void deleteTopic(UUID topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));

        // Safety check: prevent deletion if topic has been used
        if (topic.getUsageCount() > 0 || topic.getLastUsedDate() != null) {
            throw new IllegalStateException(
                "Cannot delete topic that has been used in events. "
                + "Topic has been used " + topic.getUsageCount() + " time(s)."
            );
        }

        topicRepository.delete(topic);
    }

    /**
     * Delete topic by code (ADR-003).
     * Only allowed if topic has never been used (no events attached).
     *
     * @param topicCode Topic code (slug-format)
     * @throws IllegalArgumentException if topic not found
     * @throws IllegalStateException if topic has been used
     */
    public void deleteTopicByCode(String topicCode) {
        Topic topic = topicRepository.findByTopicCode(topicCode)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicCode));

        // Safety check: prevent deletion if topic has been used
        if (topic.getUsageCount() > 0 || topic.getLastUsedDate() != null) {
            throw new IllegalStateException(
                "Cannot delete topic that has been used in events. "
                + "Topic has been used " + topic.getUsageCount() + " time(s)."
            );
        }

        topicRepository.delete(topic);
    }

    /**
     * Update topic staleness score.
     *
     * @param topicId Topic ID
     * @return Updated topic
     */
    public Topic updateTopicStaleness(UUID topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));

        int newStaleness = stalenessScoreService.calculateStaleness(topic);
        topic.setStalenessScore(newStaleness);

        return topicRepository.save(topic);
    }

    /**
     * Override staleness score with justification (AC7) - by ID.
     *
     * @param topicId Topic ID
     * @param overrideStaleness New staleness score
     * @param justification Justification for override
     * @return Updated topic
     */
    public Topic overrideStaleness(UUID topicId, int overrideStaleness, String justification) {
        if (overrideStaleness < 0 || overrideStaleness > 100) {
            throw new IllegalArgumentException("Staleness score must be between 0 and 100");
        }

        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("Justification is required for staleness override");
        }

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));

        topic.setStalenessScore(overrideStaleness);
        // Note: In a full implementation, we'd track the override in an audit log
        // For now, we're just updating the score

        return topicRepository.save(topic);
    }

    /**
     * Override staleness score with justification (AC7) - by code (ADR-003).
     *
     * @param topicCode Topic code (slug-format)
     * @param overrideStaleness New staleness score
     * @param justification Justification for override
     * @return Updated topic
     */
    public Topic overrideStalenessByCode(String topicCode, int overrideStaleness, String justification) {
        if (overrideStaleness < 0 || overrideStaleness > 100) {
            throw new IllegalArgumentException("Staleness score must be between 0 and 100");
        }

        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("Justification is required for staleness override");
        }

        Topic topic = topicRepository.findByTopicCode(topicCode)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicCode));

        topic.setStalenessScore(overrideStaleness);

        return topicRepository.save(topic);
    }

    /**
     * Mark topic as used (updates lastUsedDate and usageCount).
     *
     * @param topicId Topic ID
     * @return Updated topic
     */
    public Topic markTopicAsUsed(UUID topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));

        topic.setLastUsedDate(LocalDateTime.now());
        topic.setUsageCount(topic.getUsageCount() + 1);
        topic.setStalenessScore(0); // Reset staleness to 0 when used

        return topicRepository.save(topic);
    }

    /**
     * Calculate similarity scores for all active topics (AC4).
     * Uses TF-IDF and cosine similarity.
     */
    public void calculateAllSimilarities() {
        List<Topic> allTopics = topicRepository.findAllForSimilarityCalculation();

        for (Topic topic : allTopics) {
            calculateSimilarityScoresForTopic(topic);
        }
    }

    /**
     * Calculate similarity scores for a specific topic (public wrapper).
     *
     * @param topic Topic to calculate similarities for
     */
    public void calculateSimilarityForTopic(Topic topic) {
        calculateSimilarityScoresForTopic(topic);
    }

    /**
     * Calculate similarity scores for a single topic against all others.
     *
     * @param topic Topic to calculate similarities for
     */
    private void calculateSimilarityScoresForTopic(Topic topic) {
        List<Topic> allTopics = topicRepository.findAllForSimilarityCalculation();
        List<Topic.SimilarityScore> similarityScores = new ArrayList<>();

        for (Topic otherTopic : allTopics) {
            // Skip comparing topic to itself
            if (otherTopic.getId().equals(topic.getId())) {
                continue;
            }

            // Calculate similarity using TF-IDF and cosine similarity
            double similarity = similarityCalculationService.calculateSimilarity(topic, otherTopic);

            // Only store similarity scores >0.3 (30%) to reduce storage
            if (similarity > 0.3) {
                similarityScores.add(new Topic.SimilarityScore(otherTopic.getId(), similarity));
            }
        }

        // Sort by similarity score (descending)
        similarityScores.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        topic.setSimilarityScores(similarityScores);
        topicRepository.save(topic);
    }

    /**
     * Get similar topics with similarity >70% (duplicate detection, AC5) - by ID.
     *
     * @param topicId Topic ID
     * @return List of similar topics
     */
    @Transactional(readOnly = true)
    public List<Topic> getSimilarTopics(UUID topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));

        // Filter similarity scores >70% (duplicate threshold)
        List<UUID> similarTopicIds = topic.getSimilarityScores().stream()
                .filter(score -> score.getScore() > 0.70)
                .map(Topic.SimilarityScore::getTopicId)
                .collect(Collectors.toList());

        // Fetch and return similar topics
        return topicRepository.findAllById(similarTopicIds);
    }

    /**
     * Get similar topics with similarity >70% (duplicate detection, AC5) - by code (ADR-003).
     *
     * @param topicCode Topic code (slug-format)
     * @return List of similar topics
     */
    @Transactional(readOnly = true)
    public List<Topic> getSimilarTopicsByCode(String topicCode) {
        Topic topic = topicRepository.findByTopicCode(topicCode)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicCode));

        // Filter similarity scores >70% (duplicate threshold)
        List<UUID> similarTopicIds = topic.getSimilarityScores().stream()
                .filter(score -> score.getScore() > 0.70)
                .map(Topic.SimilarityScore::getTopicId)
                .collect(Collectors.toList());

        // Fetch and return similar topics
        return topicRepository.findAllById(similarTopicIds);
    }

    /**
     * Select a topic for an event (Story 5.2 AC14-16).
     *
     * This method:
     * 1. Validates event and topic exist
     * 2. Validates event is in valid state (CREATED or TOPIC_SELECTION)
     * 3. Assigns topic to event
     * 4. Transitions event workflow state to SPEAKER_BRAINSTORMING (topic selection complete)
     * 5. Publishes EventWorkflowTransitionEvent
     *
     * @param eventCode Event code (e.g., "BATbern56")
     * @param topicId Topic ID to select
     * @param organizerUsername Username of organizer selecting the topic
     * @return Updated event with selected topic
     * @throws IllegalArgumentException if event or topic not found
     * @throws IllegalStateException if event is not in valid state for topic selection
     */
    public Event selectTopicForEvent(String eventCode, UUID topicId, String organizerUsername) {
        // Validate event exists
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventCode));

        // Validate topic exists
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));

        // Validate event state (AC16)
        // Allow topic assignment/update for CREATED, TOPIC_SELECTION, SPEAKER_BRAINSTORMING, and ARCHIVED states
        // SPEAKER_BRAINSTORMING allows topic updates after initial selection (before speaker outreach starts)
        // ARCHIVED state support added for Story 5.2a (historical event batch import)
        EventWorkflowState currentState = event.getWorkflowState();
        if (currentState != EventWorkflowState.CREATED
                && currentState != EventWorkflowState.TOPIC_SELECTION
                && currentState != EventWorkflowState.SPEAKER_BRAINSTORMING
                && currentState != EventWorkflowState.ARCHIVED) {
            throw new IllegalStateException(
                "Invalid state transition: Cannot select topic when event is in " + currentState + " state"
            );
        }

        // Transition workflow state FIRST (AC14) - UNLESS event is already ARCHIVED or SPEAKER_BRAINSTORMING
        // For ARCHIVED events (historical imports), skip state transition to preserve archival workflowState
        // For SPEAKER_BRAINSTORMING events, skip transition (already there, just updating topic)
        Event updatedEvent;
        if (currentState == EventWorkflowState.ARCHIVED
                || currentState == EventWorkflowState.SPEAKER_BRAINSTORMING) {
            // Skip state transition - either already in correct state or preserving archival workflowState
            // IMPORTANT: Reload the event to ensure we have a properly managed entity for update
            // Without this, entity state management can cause save() to not persist changes
            updatedEvent = eventRepository.findByEventCode(eventCode)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventCode));
        } else {
            // Transition to SPEAKER_BRAINSTORMING because topic selection is now complete
            // Can transition from both CREATED and TOPIC_SELECTION states
            // This will also publish EventWorkflowTransitionEvent
            updatedEvent = eventWorkflowStateMachine.transitionToState(
                eventCode,
                EventWorkflowState.SPEAKER_BRAINSTORMING,
                organizerUsername
            );
        }

        // NOW assign topic to the event returned by state machine
        updatedEvent.setTopicId(topicId);
        updatedEvent.setUpdatedBy(organizerUsername);
        Event savedEvent = eventRepository.save(updatedEvent);

        // Mark topic as used with event date (Story 5.2a - Fix #4)
        markTopicAsUsed(topicId, savedEvent.getDate());

        // Create usage history record for heatmap visualization (GitHub Issue #379)
        createUsageHistoryRecord(topicId, savedEvent.getId(), savedEvent.getDate());

        return savedEvent;
    }

    /**
     * Mark topic as used with specific date (overloaded for backward compatibility).
     *
     * @param topicId Topic ID
     * @param eventDate Event date to set as lastUsedDate
     * @return Updated topic
     */
    public Topic markTopicAsUsed(UUID topicId, java.time.Instant eventDate) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));

        // Convert Instant to LocalDateTime in system timezone
        topic.setLastUsedDate(LocalDateTime.ofInstant(eventDate, java.time.ZoneId.systemDefault()));
        topic.setUsageCount(topic.getUsageCount() + 1);
        topic.setStalenessScore(0); // Reset staleness to 0 when used

        return topicRepository.save(topic);
    }

    /**
     * Create usage history record for topic (GitHub Issue #379).
     * This creates a record in the topic_usage_history table that tracks when
     * a topic was used for a specific event, enabling heatmap visualization.
     *
     * @param topicId Topic ID
     * @param eventId Event ID
     * @param eventDate Event date
     */
    private void createUsageHistoryRecord(UUID topicId, UUID eventId, java.time.Instant eventDate) {
        TopicUsageHistory history = new TopicUsageHistory();
        history.setTopicId(topicId);
        history.setEventId(eventId);
        history.setUsedDate(LocalDateTime.ofInstant(eventDate, java.time.ZoneId.systemDefault()));
        // Note: attendeeCount and engagementScore are populated later after the event completes
        topicUsageHistoryRepository.save(history);
    }

    /**
     * Enrich topic entities with usage history and convert to responses (GitHub Issue #379).
     * Uses a single JOIN query to fetch all usage history with event details efficiently.
     *
     * ADR-003: Takes Topic entities (with UUID) and returns TopicResponses (with topicCode).
     *
     * Performance: Single SQL query with JOIN instead of N+1 queries.
     * For 100 topics with average 3 history records each:
     * - Old approach: 1 + 100 + 300 = 401 queries
     * - New approach: 1 query
     *
     * @param topics List of Topic entities to enrich
     * @return List of topic responses with usageHistory populated
     */
    @Transactional(readOnly = true)
    public List<ch.batbern.events.dto.generated.topics.Topic> enrichTopicsWithUsageHistory(List<Topic> topics) {
        if (topics.isEmpty()) {
            return List.of();
        }

        // Extract all topic IDs (internal UUIDs for database query)
        List<UUID> topicIds = topics.stream()
                .map(Topic::getId)
                .collect(Collectors.toList());

        // Fetch ALL usage history for ALL topics in ONE query with JOIN
        List<ch.batbern.events.dto.TopicUsageHistoryWithEventDetails> allHistories =
                topicUsageHistoryRepository.findUsageHistoryWithEventDetailsByTopicIds(topicIds);

        // Group histories by topicId (UUID) and convert to generated DTOs
        java.util.Map<UUID, List<ch.batbern.events.dto.generated.topics.TopicUsageHistory>> historyByTopicId =
                allHistories.stream()
                        .collect(Collectors.groupingBy(
                                ch.batbern.events.dto.TopicUsageHistoryWithEventDetails::getTopicId,
                                Collectors.mapping(
                                        h -> {
                                            ch.batbern.events.dto.generated.topics.TopicUsageHistory dto =
                                                    new ch.batbern.events.dto.generated.topics.TopicUsageHistory();
                                            dto.setEventNumber(h.getEventNumber());
                                            dto.setEventCode(h.getEventCode());
                                            dto.setEventDate(h.getEventDate() != null ?
                                                    java.time.LocalDate.ofInstant(h.getEventDate(), java.time.ZoneId.systemDefault()) : null);
                                            dto.setUsedDate(h.getUsedDate() != null ?
                                                    h.getUsedDate().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime() : null);
                                            dto.setAttendance(h.getAttendeeCount());
                                            dto.setEngagementScore(h.getEngagementScore() != null ?
                                                    h.getEngagementScore().floatValue() : null);
                                            return dto;
                                        },
                                        Collectors.toList()
                                )
                        ));

        // Convert to generated DTOs using mapper and attach usage history
        return topics.stream()
                .map(topic -> topicMapper.toDtoWithUsageHistory(
                        topic,
                        historyByTopicId.getOrDefault(topic.getId(), List.of())
                ))
                .collect(Collectors.toList());
    }

    /**
     * Convert similarity scores from entity (UUID-based) to DTOs (topicCode-based).
     * Performs batch lookup to avoid N+1 queries.
     *
     * @param entityScores List of similarity scores from entity
     * @return List of similarity score DTOs with topicCode instead of UUID
     */
    public List<ch.batbern.events.dto.generated.topics.SimilarityScore> convertSimilarityScoresToDtos(
            List<Topic.SimilarityScore> entityScores) {
        if (entityScores == null || entityScores.isEmpty()) {
            return List.of();
        }

        // Batch fetch all topics by UUIDs to avoid N+1 queries
        List<UUID> topicIds = entityScores.stream()
                .map(Topic.SimilarityScore::getTopicId)
                .collect(Collectors.toList());

        List<Topic> topics = topicRepository.findAllById(topicIds);

        // Create a map of UUID → topicCode for quick lookup
        java.util.Map<UUID, String> uuidToCodeMap = topics.stream()
                .collect(Collectors.toMap(Topic::getId, Topic::getTopicCode));

        // Map similarity scores, converting UUID to topicCode
        return entityScores.stream()
                .map(entityScore -> {
                    String topicCode = uuidToCodeMap.get(entityScore.getTopicId());
                    if (topicCode == null) {
                        // Topic was deleted or not found - skip this similarity score
                        return null;
                    }
                    ch.batbern.events.dto.generated.topics.SimilarityScore dto =
                            new ch.batbern.events.dto.generated.topics.SimilarityScore();
                    dto.setTopicCode(topicCode);
                    dto.setScore(entityScore.getScore() != null ? entityScore.getScore().floatValue() : null);
                    return dto;
                })
                .filter(dto -> dto != null) // Filter out null entries for deleted topics
                .collect(Collectors.toList());
    }

    /**
     * Calculate color zone based on staleness score.
     * Business logic:
     * - null → GRAY
     * - < 50 → RED (too recent)
     * - 50-83 → YELLOW (caution)
     * - >= 83 → GREEN (safe to reuse)
     *
     * @param staleness Staleness score (0-100)
     * @return Color zone enum
     */
    public static ch.batbern.events.dto.generated.topics.TopicColorZone calculateColorZone(Integer staleness) {
        if (staleness == null) {
            return ch.batbern.events.dto.generated.topics.TopicColorZone.GRAY;
        }
        if (staleness < 50) {
            return ch.batbern.events.dto.generated.topics.TopicColorZone.RED;
        } else if (staleness <= 83) {
            return ch.batbern.events.dto.generated.topics.TopicColorZone.YELLOW;
        } else {
            return ch.batbern.events.dto.generated.topics.TopicColorZone.GREEN;
        }
    }

    /**
     * Calculate status based on staleness score.
     * Business logic:
     * - null or >= 83 → AVAILABLE (green zone)
     * - 50-82 → CAUTION (yellow zone)
     * - < 50 → UNAVAILABLE (red zone)
     *
     * @param staleness Staleness score (0-100)
     * @return Status enum
     */
    public static ch.batbern.events.dto.generated.topics.TopicStatus calculateStatus(Integer staleness) {
        if (staleness == null || staleness >= 83) {
            return ch.batbern.events.dto.generated.topics.TopicStatus.AVAILABLE;
        } else if (staleness >= 50) {
            return ch.batbern.events.dto.generated.topics.TopicStatus.CAUTION;
        } else {
            return ch.batbern.events.dto.generated.topics.TopicStatus.UNAVAILABLE;
        }
    }
}
