package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.TopicRepository;
import ch.batbern.shared.types.EventWorkflowState;
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
    private final StalenessScoreService stalenessScoreService;
    private final SimilarityCalculationService similarityCalculationService;
    private final EventRepository eventRepository;
    private final EventWorkflowStateMachine eventWorkflowStateMachine;

    public TopicService(
            TopicRepository topicRepository,
            StalenessScoreService stalenessScoreService,
            SimilarityCalculationService similarityCalculationService,
            EventRepository eventRepository,
            EventWorkflowStateMachine eventWorkflowStateMachine) {
        this.topicRepository = topicRepository;
        this.stalenessScoreService = stalenessScoreService;
        this.similarityCalculationService = similarityCalculationService;
        this.eventRepository = eventRepository;
        this.eventWorkflowStateMachine = eventWorkflowStateMachine;
    }

    /**
     * Get all active topics with optional category filter.
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
        EventWorkflowState currentState = event.getWorkflowState();
        if (currentState != EventWorkflowState.CREATED
                && currentState != EventWorkflowState.TOPIC_SELECTION) {
            throw new IllegalStateException(
                "Invalid state transition: Cannot select topic when event is in " + currentState + " state"
            );
        }

        // Transition workflow state FIRST (AC14)
        // This will also publish EventWorkflowTransitionEvent
        Event updatedEvent = eventWorkflowStateMachine.transitionToState(
            eventCode,
            EventWorkflowState.TOPIC_SELECTION,
            organizerUsername
        );

        // NOW assign topic to the event returned by state machine
        updatedEvent.setTopicId(topicId);
        updatedEvent.setUpdatedBy(organizerUsername);
        Event savedEvent = eventRepository.save(updatedEvent);

        // Mark topic as used
        markTopicAsUsed(topicId);

        return savedEvent;
    }
}
