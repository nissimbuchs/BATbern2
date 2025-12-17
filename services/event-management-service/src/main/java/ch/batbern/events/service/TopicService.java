package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.domain.TopicUsageHistory;
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

    public TopicService(
            TopicRepository topicRepository,
            TopicUsageHistoryRepository topicUsageHistoryRepository,
            StalenessScoreService stalenessScoreService,
            SimilarityCalculationService similarityCalculationService,
            EventRepository eventRepository,
            EventWorkflowStateMachine eventWorkflowStateMachine) {
        this.topicRepository = topicRepository;
        this.topicUsageHistoryRepository = topicUsageHistoryRepository;
        this.stalenessScoreService = stalenessScoreService;
        this.similarityCalculationService = similarityCalculationService;
        this.eventRepository = eventRepository;
        this.eventWorkflowStateMachine = eventWorkflowStateMachine;
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
     * Get topic by ID.
     *
     * @param id Topic ID
     * @return Optional containing topic if found
     */
    @Transactional(readOnly = true)
    public Optional<Topic> getTopicById(UUID id) {
        return topicRepository.findById(id);
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
     * Update existing topic (Story 5.2a - Edit Topic Feature).
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
     * Delete topic (Story 5.2a - Delete Topic Feature).
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
     * Override staleness score with justification (AC7).
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
     * Get similar topics with similarity >70% (duplicate detection, AC5).
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
     * Select a topic for an event (Story 5.2 AC14-16).
     *
     * This method:
     * 1. Validates event and topic exist
     * 2. Validates event is in valid state (CREATED or TOPIC_SELECTION)
     * 3. Assigns topic to event
     * 4. Transitions event workflow state to TOPIC_SELECTION
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
        // Allow topic assignment for CREATED, TOPIC_SELECTION, and ARCHIVED states
        // ARCHIVED state support added for Story 5.2a (historical event batch import)
        EventWorkflowState currentState = event.getWorkflowState();
        if (currentState != EventWorkflowState.CREATED
                && currentState != EventWorkflowState.TOPIC_SELECTION
                && currentState != EventWorkflowState.ARCHIVED) {
            throw new IllegalStateException(
                "Invalid state transition: Cannot select topic when event is in " + currentState + " state"
            );
        }

        // Transition workflow state FIRST (AC14) - UNLESS event is already ARCHIVED
        // For ARCHIVED events (historical imports), skip state transition to preserve archival status
        Event updatedEvent;
        if (currentState == EventWorkflowState.ARCHIVED) {
            // Skip state transition for archived events - just update topic directly
            updatedEvent = event;
        } else {
            // This will also publish EventWorkflowTransitionEvent
            updatedEvent = eventWorkflowStateMachine.transitionToState(
                eventCode,
                EventWorkflowState.TOPIC_SELECTION,
                organizerUsername
            );
        }

        // NOW assign topic to the event returned by state machine
        updatedEvent.setTopicId(topicId);
        updatedEvent.setUpdatedBy(organizerUsername);
        Event savedEvent = eventRepository.save(updatedEvent);

        // Mark topic as used with event date (Story 5.2a - Fix #4)
        markTopicAsUsed(topicId, savedEvent.getDate());

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
}
