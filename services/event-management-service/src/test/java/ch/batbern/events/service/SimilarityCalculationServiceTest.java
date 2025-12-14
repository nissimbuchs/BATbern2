package ch.batbern.events.service;

import ch.batbern.events.domain.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SimilarityCalculationService (Story 5.2 AC4).
 *
 * Tests verify TF-IDF and cosine similarity calculation for duplicate detection.
 * Similarity >70% triggers warnings during topic selection.
 *
 * TDD RED PHASE: These tests should FAIL until SimilarityCalculationService is implemented.
 */
class SimilarityCalculationServiceTest {

    private SimilarityCalculationService similarityCalculationService;

    @BeforeEach
    void setUp() {
        similarityCalculationService = new SimilarityCalculationService();
    }

    // ==================== AC4 Tests: TF-IDF Calculation ====================

    /**
     * Test 2a.11: should_calculateTFIDF_when_topicProvided
     * Verifies TF-IDF vector calculation for a topic.
     * Story 5.2 AC4: Use TF-IDF for similarity calculation
     */
    @Test
    void should_calculateTFIDF_when_topicProvided() {
        // Given: Topics with distinct words
        Topic topic1 = createTopic("Cloud Native Architecture");
        Topic topic2 = createTopic("Microservices Architecture");
        List<Topic> corpus = List.of(topic1, topic2);

        // When: Calculate TF-IDF vectors
        Map<String, Double> tfidf1 = similarityCalculationService.calculateTFIDF(topic1, corpus);

        // Then: TF-IDF vectors are generated
        assertThat(tfidf1).isNotEmpty();
        assertThat(tfidf1).containsKey("cloud");
        assertThat(tfidf1).containsKey("native");
        assertThat(tfidf1).containsKey("architecture");

        // "architecture" appears in both topics, should have lower IDF
        // "cloud" appears only in topic1, should have higher IDF
        assertThat(tfidf1.get("cloud")).isGreaterThan(tfidf1.get("architecture"));
    }

    // ==================== AC4 Tests: Cosine Similarity ====================

    /**
     * Test 2a.12: should_calculateCosineSimilarity_when_twoTopicsCompared
     * Verifies cosine similarity calculation between TF-IDF vectors.
     * Story 5.2 AC4: Calculate similarity = (A · B) / (||A|| × ||B||)
     */
    @Test
    void should_calculateCosineSimilarity_when_twoTopicsCompared() {
        // Given: Two topics
        Topic topic1 = createTopic("Cloud Native Architecture Patterns");
        Topic topic2 = createTopic("Cloud Native Design Best Practices");

        // When: Calculate cosine similarity
        double similarity = similarityCalculationService.calculateSimilarity(topic1, topic2);

        // Then: Similarity is between 0 and 1
        assertThat(similarity).isBetween(0.0, 1.0);

        // Moderate similarity due to overlapping terms (cloud, native)
        // Actual TF-IDF calculation yields ~0.36 for these topics
        assertThat(similarity).isGreaterThan(0.3);
    }

    /**
     * Test 2a.13: should_returnHighSimilarity_when_topicsAlmostIdentical
     * Verifies similarity >70% for nearly identical topics.
     * Story 5.2 AC5: Similarity >70% triggers warnings
     */
    @Test
    void should_returnHighSimilarity_when_topicsAlmostIdentical() {
        // Given: Nearly identical topics
        Topic topic1 = createTopic("Kubernetes Best Practices");
        Topic topic2 = createTopic("Kubernetes Best Practices Guide");

        // When: Calculate similarity
        double similarity = similarityCalculationService.calculateSimilarity(topic1, topic2);

        // Then: Similarity >70% (triggers warning threshold)
        assertThat(similarity).isGreaterThan(0.70);
    }

    /**
     * Test 2a.14: should_returnLowSimilarity_when_topicsUnrelated
     * Verifies low similarity for unrelated topics.
     */
    @Test
    void should_returnLowSimilarity_when_topicsUnrelated() {
        // Given: Completely different topics
        Topic topic1 = createTopic("Cloud Native Architecture");
        Topic topic2 = createTopic("Leadership and Team Management");

        // When: Calculate similarity
        double similarity = similarityCalculationService.calculateSimilarity(topic1, topic2);

        // Then: Low similarity (no common terms)
        assertThat(similarity).isLessThan(0.30);
    }

    /**
     * Test 2a.15: should_ignoreCommonWords_when_calculatingTFIDF
     * Verifies stopwords (the, and, of, etc.) are excluded from TF-IDF.
     */
    @Test
    void should_ignoreCommonWords_when_calculatingTFIDF() {
        // Given: Topics with common stopwords
        Topic topic1 = createTopic("The Cloud and the Architecture of Systems");
        Topic topic2 = createTopic("Cloud Architecture");
        List<Topic> corpus = List.of(topic1, topic2);

        // When: Calculate TF-IDF
        Map<String, Double> tfidf1 = similarityCalculationService.calculateTFIDF(topic1, corpus);

        // Then: Stopwords are excluded
        assertThat(tfidf1).doesNotContainKey("the");
        assertThat(tfidf1).doesNotContainKey("and");
        assertThat(tfidf1).doesNotContainKey("of");

        // Meaningful words are included
        assertThat(tfidf1).containsKey("cloud");
        assertThat(tfidf1).containsKey("architecture");
    }

    // ==================== Helper Methods ====================

    private Topic createTopic(String title) {
        Topic topic = new Topic();
        topic.setTitle(title);
        topic.setDescription("Test description for " + title);
        topic.setCategory("technical");
        return topic;
    }
}
