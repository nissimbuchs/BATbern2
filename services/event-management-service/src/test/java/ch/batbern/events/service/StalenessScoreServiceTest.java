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
 * - 100 = safe to reuse (>24 months since last use)
 * - 0 = too recent (just used)
 * - Formula: min(100, (monthsSinceLastUse / 24) * 100)
 * Window extended to 24 months because BATbern runs only 3 events/year.
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
     * Test 2a.16: should_return100_when_topicNotUsedOver24Months
     * Verifies maximum staleness score for topics unused >24 months.
     * Story 5.2 AC6: Staleness 100 = safe to reuse
     */
    @Test
    void should_return100_when_topicNotUsedOver24Months() {
        // Given: Topic last used 30 months ago
        Topic topic = new Topic();
        topic.setLastUsedDate(LocalDateTime.now().minusMonths(30));

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
     * Test 2a.18: should_return50_when_topicUsed12MonthsAgo
     * Verifies mid-range staleness for moderately old topics.
     * Story 5.2 AC6: 12 months = 50% staleness (24-month window)
     */
    @Test
    void should_return50_when_topicUsed12MonthsAgo() {
        // Given: Topic last used 12 months ago
        Topic topic = new Topic();
        topic.setLastUsedDate(LocalDateTime.now().minusMonths(12));

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
     * Verifies staleness grows linearly from 0 to 100 over 24 months.
     * Story 5.2 AC6: Formula = min(100, (months / 24) * 100)
     */
    @Test
    void should_returnLinearGrowth_when_calculateStaleness() {
        // Test various time periods (~4% per month)
        assertStaleness(0, 0);     // Today
        assertStaleness(6, 25);    // 6 months  → 25%
        assertStaleness(12, 50);   // 12 months → 50%
        assertStaleness(18, 75);   // 18 months → 75%
        assertStaleness(24, 100);  // 24 months → 100% (capped)
        assertStaleness(30, 100);  // 30 months → still capped
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
     * Verifies yellow zone (12-20 months with 24-month window).
     */
    @Test
    void should_identifyYellowZone_when_staleness50To83() {
        // Given: Topic with moderate staleness (15 months → 62%)
        Topic topic = new Topic();
        topic.setLastUsedDate(LocalDateTime.now().minusMonths(15));

        // When: Calculate staleness
        int staleness = stalenessScoreService.calculateStaleness(topic);

        // Then: Staleness in yellow zone (50-83)
        assertThat(staleness).isBetween(50, 83);
    }

    /**
     * Test 2a.23: should_identifyGreenZone_when_stalenessOver83
     * Verifies green zone (>20 months = safe with 24-month window).
     */
    @Test
    void should_identifyGreenZone_when_stalenessOver83() {
        // Given: Topic with high staleness (22 months → 92%)
        Topic topic = new Topic();
        topic.setLastUsedDate(LocalDateTime.now().minusMonths(22));

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
