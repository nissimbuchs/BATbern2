package ch.batbern.events.service;

import ch.batbern.events.domain.Topic;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Service for calculating topic staleness scores (Story 5.2 AC6).
 *
 * Staleness Score (0-100):
 * - 100 = safe to reuse (>12 months since last use or never used)
 * - 0 = too recent (just used)
 * - Formula: min(100, (monthsSinceLastUse / 12) * 100)
 *
 * Color-coded freshness zones (AC3):
 * - Red (<50): Too recent to reuse
 * - Yellow (50-83): Caution zone
 * - Green (>83): Safe to reuse
 */
@Service
public class StalenessScoreService {

    /**
     * Calculate staleness score for a topic.
     *
     * @param topic Topic to calculate staleness for
     * @return Staleness score (0-100)
     */
    public int calculateStaleness(Topic topic) {
        LocalDateTime lastUsedDate = topic.getLastUsedDate();

        // Never used topics have maximum staleness (safe to use)
        if (lastUsedDate == null) {
            return 100;
        }

        // Calculate months since last use
        long monthsSinceLastUse = ChronoUnit.MONTHS.between(lastUsedDate, LocalDateTime.now());

        // Apply formula: min(100, (months / 12) * 100)
        // Cast to double for precise calculation, then round to int
        double stalenessDouble = ((double) monthsSinceLastUse / 12.0) * 100.0;
        int staleness = (int) Math.round(stalenessDouble);

        // Cap at 100 (topics >12 months old are maximally stale)
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
