package ch.batbern.events.service;

import ch.batbern.events.domain.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StalenessScoreService (Story 5.2 AC6).
 *
 * Tests verify staleness score calculation (0-100 scale):
 * - 100 = safe to reuse (>12 months since last use)
 * - 0 = too recent (just used)
 * - Formula: min(100, (monthsSinceLastUse / 12) * 100)
 *
 * TDD RED PHASE: These tests should FAIL until StalenessScoreService is implemented.
 */
class StalenessScoreServiceTest {

    private StalenessScoreService stalenessScoreService;

    @BeforeEach
    void setUp() {
        stalenessScoreService = new StalenessScoreService();
    }

    // ==================== AC6 Tests: Staleness Score Calculation ====================

    /**
     * Test 2a.16: should_return100_when_topicNotUsedOver12Months
     * Verifies maximum staleness score for topics unused >12 months.
     * Story 5.2 AC6: Staleness 100 = safe to reuse
     */
    @Test
    void should_return100_when_topicNotUsedOver12Months() {
        // Given: Topic last used 18 months ago
        Topic topic = new Topic();
        topic.setLastUsedDate(LocalDateTime.now().minusMonths(18));

        // When: Calculate staleness
        int staleness = stalenessScoreService.calculateStaleness(topic);

        // Then: Score is 100 (capped at maximum)
        assertThat(staleness).isEqualTo(100);
    }

    /**
     * Test 2a.17: should_return0_when_topicUsedRecently
     * Verifies zero staleness for recently used topics.
     * Story 5.2 AC6: Staleness 0 = just used (too recent)
     */
    @Test
    void should_return0_when_topicUsedRecently() {
        // Given: Topic used today
        Topic topic = new Topic();
        topic.setLastUsedDate(LocalDateTime.now());

        // When: Calculate staleness
        int staleness = stalenessScoreService.calculateStaleness(topic);

        // Then: Score is 0 (too recent)
        assertThat(staleness).isEqualTo(0);
    }

    /**
     * Test 2a.18: should_return50_when_topicUsed6MonthsAgo
     * Verifies mid-range staleness for moderately old topics.
     * Story 5.2 AC6: 6 months = 50% staleness
     */
    @Test
    void should_return50_when_topicUsed6MonthsAgo() {
        // Given: Topic last used 6 months ago
        Topic topic = new Topic();
        topic.setLastUsedDate(LocalDateTime.now().minusMonths(6));

        // When: Calculate staleness
        int staleness = stalenessScoreService.calculateStaleness(topic);

        // Then: Score is approximately 50 (±1 for rounding)
        assertThat(staleness).isBetween(49, 51);
    }

    /**
     * Test 2a.19: should_return100_when_topicNeverUsed
     * Verifies new topics (never used) have maximum staleness.
     * Story 5.2 AC8: New topics have staleness 100
     */
    @Test
    void should_return100_when_topicNeverUsed() {
        // Given: Topic never used (lastUsedDate is null)
        Topic topic = new Topic();
        topic.setLastUsedDate(null);

        // When: Calculate staleness
        int staleness = stalenessScoreService.calculateStaleness(topic);

        // Then: Score is 100 (safe to use)
        assertThat(staleness).isEqualTo(100);
    }

    /**
     * Test 2a.20: should_returnLinearGrowth_when_calculateStaleness
     * Verifies staleness grows linearly from 0 to 100 over 12 months.
     * Story 5.2 AC6: Formula = min(100, (months / 12) * 100)
     */
    @Test
    void should_returnLinearGrowth_when_calculateStaleness() {
        // Test various time periods
        assertStaleness(0, 0);     // Today
        assertStaleness(1, 8);     // ~8% per month
        assertStaleness(3, 25);    // 3 months
        assertStaleness(6, 50);    // 6 months
        assertStaleness(9, 75);    // 9 months
        assertStaleness(12, 100);  // 12 months (capped)
        assertStaleness(24, 100);  // 24 months (still capped)
    }

    // ==================== AC3 Tests: Color-Coded Freshness ====================

    /**
     * Test 2a.21: should_identifyRedZone_when_staleness0To49
     * Verifies color zone identification for UI rendering.
     * Story 5.2 AC3: Red (<50) = too recent, Yellow (50-83) = caution, Green (>83) = safe
     */
    @Test
    void should_identifyRedZone_when_staleness0To49() {
        // Given: Topic with low staleness (recently used)
        Topic topic = new Topic();
        topic.setLastUsedDate(LocalDateTime.now().minusMonths(3));

        // When: Calculate staleness
        int staleness = stalenessScoreService.calculateStaleness(topic);

        // Then: Staleness in red zone (<50)
        assertThat(staleness).isLessThan(50);
    }

    /**
     * Test 2a.22: should_identifyYellowZone_when_staleness50To83
     * Verifies yellow zone (6-10 months).
     */
    @Test
    void should_identifyYellowZone_when_staleness50To83() {
        // Given: Topic with moderate staleness
        Topic topic = new Topic();
        topic.setLastUsedDate(LocalDateTime.now().minusMonths(8));

        // When: Calculate staleness
        int staleness = stalenessScoreService.calculateStaleness(topic);

        // Then: Staleness in yellow zone (50-83)
        assertThat(staleness).isBetween(50, 83);
    }

    /**
     * Test 2a.23: should_identifyGreenZone_when_stalenessOver83
     * Verifies green zone (>10 months = safe).
     */
    @Test
    void should_identifyGreenZone_when_stalenessOver83() {
        // Given: Topic with high staleness
        Topic topic = new Topic();
        topic.setLastUsedDate(LocalDateTime.now().minusMonths(11));

        // When: Calculate staleness
        int staleness = stalenessScoreService.calculateStaleness(topic);

        // Then: Staleness in green zone (>83)
        assertThat(staleness).isGreaterThan(83);
    }

    // ==================== Helper Methods ====================

    private void assertStaleness(int monthsAgo, int expectedStaleness) {
        Topic topic = new Topic();
        topic.setLastUsedDate(LocalDateTime.now().minusMonths(monthsAgo));

        int actual = stalenessScoreService.calculateStaleness(topic);

        // Allow ±2 tolerance for rounding differences
        assertThat(actual).isBetween(expectedStaleness - 2, expectedStaleness + 2);
    }
}
