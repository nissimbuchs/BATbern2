package ch.batbern.events.service;

import ch.batbern.events.domain.Topic;
import ch.batbern.events.repository.TopicUsageHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for calculating topic staleness scores (Story 5.2 AC6).
 *
 * Staleness Score (0-100):
 * - 100 = safe to reuse (>24 months since last use or never used)
 * - 0 = too recent (just used)
 * - Formula: min(100, (monthsSinceLastUse / 24) * 100)
 *
 * Window is 24 months because BATbern runs only 3 events per year;
 * a 12-month window only covered ~3 events, while 2 years covers ~6.
 *
 * Color-coded freshness zones (AC3):
 * - Red (<50):   Too recent — used within last 12 months
 * - Yellow (50-83): Caution — used 12-20 months ago
 * - Green (>83): Safe to reuse — used more than 20 months ago
 *
 * Staleness is always derived from topic_usage_history (the authoritative source).
 * The topics table no longer stores last_used_date or staleness_score.
 */
@Service
@RequiredArgsConstructor
public class StalenessScoreService {

    private final TopicUsageHistoryRepository topicUsageHistoryRepository;

    /**
     * Holds the computed staleness and last-used date for a topic.
     */
    public record StalenessData(int staleness, LocalDateTime lastUsedDate) {
        public static final StalenessData NEVER_USED = new StalenessData(100, null);
    }

    /**
     * Calculate staleness score for a single topic by querying the live usage history.
     *
     * @param topic Topic to calculate staleness for
     * @return StalenessData (score + lastUsedDate)
     */
    public StalenessData computeStalenessData(Topic topic) {
        LocalDateTime lastUsedDate = topicUsageHistoryRepository
                .findMaxUsedDateByTopicId(topic.getId())
                .orElse(null);
        return new StalenessData(calculateStaleness(lastUsedDate), lastUsedDate);
    }

    /**
     * Batch-compute staleness for a list of topics in a single query.
     *
     * @param topics Topics to compute staleness for
     * @return Map of topicId → StalenessData
     */
    public Map<UUID, StalenessData> computeStalenessDataBatch(List<Topic> topics) {
        if (topics.isEmpty()) {
            return Map.of();
        }

        List<UUID> topicIds = topics.stream().map(Topic::getId).collect(Collectors.toList());

        Map<UUID, LocalDateTime> maxDateByTopicId = topicUsageHistoryRepository
                .findMaxUsedDatesByTopicIds(topicIds)
                .stream()
                .collect(Collectors.toMap(
                        TopicUsageHistoryRepository.TopicMaxUsedDateProjection::getTopicId,
                        TopicUsageHistoryRepository.TopicMaxUsedDateProjection::getMaxUsedDate
                ));

        return topics.stream().collect(Collectors.toMap(
                Topic::getId,
                t -> {
                    LocalDateTime lastUsed = maxDateByTopicId.get(t.getId());
                    return new StalenessData(calculateStaleness(lastUsed), lastUsed);
                }
        ));
    }

    /**
     * Calculate staleness score from a known last-used date.
     * Pure calculation — no database access.
     *
     * @param lastUsedDate Date of last use, or null if never used
     * @return Staleness score (0-100)
     */
    public int calculateStaleness(LocalDateTime lastUsedDate) {
        if (lastUsedDate == null) {
            return 100;
        }

        long monthsSinceLastUse = ChronoUnit.MONTHS.between(lastUsedDate, LocalDateTime.now());

        // Apply formula: min(100, (months / 24) * 100)
        double stalenessDouble = ((double) monthsSinceLastUse / 24.0) * 100.0;
        int staleness = (int) Math.round(stalenessDouble);

        // Cap at 100 (topics >24 months old are maximally stale)
        return Math.min(100, Math.max(0, staleness));
    }

    /**
     * Get color zone for staleness score (for UI rendering).
     *
     * @param staleness Staleness score (0-100)
     * @return Color zone: "red", "yellow", or "green"
     */
    public String getColorZone(int staleness) {
        if (staleness < 50) {
            return "red";    // Too recent
        } else if (staleness <= 83) {
            return "yellow"; // Caution zone
        } else {
            return "green";  // Safe to reuse
        }
    }
}
