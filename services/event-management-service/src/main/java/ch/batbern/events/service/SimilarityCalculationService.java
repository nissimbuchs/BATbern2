package ch.batbern.events.service;

import ch.batbern.events.domain.Topic;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for calculating topic similarity using TF-IDF and cosine similarity (Story 5.2 AC4).
 *
 * Algorithm:
 * 1. Calculate TF-IDF (Term Frequency-Inverse Document Frequency) vectors for each topic
 * 2. Compute cosine similarity between vectors: similarity = (A · B) / (||A|| × ||B||)
 * 3. Similarity >70% triggers duplicate warnings (AC5)
 *
 * Stopwords (the, and, of, etc.) are excluded from calculation.
 */
@Service
public class SimilarityCalculationService {

    // Common English stopwords to exclude from TF-IDF calculation
    private static final Set<String> STOPWORDS = Set.of(
        "the", "and", "or", "of", "in", "on", "at", "to", "for", "a", "an",
        "is", "are", "was", "were", "be", "been", "being", "have", "has", "had",
        "do", "does", "did", "will", "would", "should", "could", "may", "might",
        "with", "from", "by", "as", "this", "that", "these", "those"
    );

    /**
     * Calculate cosine similarity between two topics.
     *
     * @param topic1 First topic
     * @param topic2 Second topic
     * @return Similarity score between 0.0 and 1.0
     */
    public double calculateSimilarity(Topic topic1, Topic topic2) {
        // Build corpus from both topics for IDF calculation
        List<Topic> corpus = List.of(topic1, topic2);

        // Calculate TF-IDF vectors
        Map<String, Double> vector1 = calculateTFIDF(topic1, corpus);
        Map<String, Double> vector2 = calculateTFIDF(topic2, corpus);

        // Calculate cosine similarity
        return cosineSimilarity(vector1, vector2);
    }

    /**
     * Calculate TF-IDF vector for a topic.
     *
     * TF-IDF = Term Frequency × Inverse Document Frequency
     * - TF = (count of term in document) / (total terms in document)
     * - IDF = log(total documents / documents containing term)
     *
     * @param topic Topic to analyze
     * @param corpus All topics for IDF calculation
     * @return TF-IDF vector as term → score map
     */
    public Map<String, Double> calculateTFIDF(Topic topic, List<Topic> corpus) {
        // Extract and normalize terms from topic title
        List<String> terms = extractTerms(topic.getTitle());

        // Calculate term frequency (TF)
        Map<String, Double> termFrequency = calculateTermFrequency(terms);

        // Calculate inverse document frequency (IDF)
        Map<String, Double> idf = calculateIDF(terms, corpus);

        // Combine TF * IDF
        Map<String, Double> tfidf = new HashMap<>();
        for (String term : termFrequency.keySet()) {
            double tf = termFrequency.get(term);
            double idfValue = idf.getOrDefault(term, 0.0);
            tfidf.put(term, tf * idfValue);
        }

        return tfidf;
    }

    /**
     * Calculate cosine similarity between two TF-IDF vectors.
     *
     * Cosine similarity = (A · B) / (||A|| × ||B||)
     *
     * @param vector1 First TF-IDF vector
     * @param vector2 Second TF-IDF vector
     * @return Similarity between 0.0 (completely different) and 1.0 (identical)
     */
    private double cosineSimilarity(Map<String, Double> vector1, Map<String, Double> vector2) {
        // Calculate dot product (A · B)
        double dotProduct = 0.0;
        Set<String> allTerms = new HashSet<>(vector1.keySet());
        allTerms.addAll(vector2.keySet());

        for (String term : allTerms) {
            double v1 = vector1.getOrDefault(term, 0.0);
            double v2 = vector2.getOrDefault(term, 0.0);
            dotProduct += v1 * v2;
        }

        // Calculate magnitudes (||A|| and ||B||)
        double magnitude1 = Math.sqrt(vector1.values().stream()
            .mapToDouble(v -> v * v).sum());
        double magnitude2 = Math.sqrt(vector2.values().stream()
            .mapToDouble(v -> v * v).sum());

        // Avoid division by zero
        if (magnitude1 == 0.0 || magnitude2 == 0.0) {
            return 0.0;
        }

        // Return cosine similarity
        return dotProduct / (magnitude1 * magnitude2);
    }

    /**
     * Calculate term frequency (TF) for terms in a document.
     *
     * TF = (count of term in document) / (total terms in document)
     */
    private Map<String, Double> calculateTermFrequency(List<String> terms) {
        Map<String, Long> termCounts = terms.stream()
            .collect(Collectors.groupingBy(term -> term, Collectors.counting()));

        Map<String, Double> tf = new HashMap<>();
        int totalTerms = terms.size();

        for (Map.Entry<String, Long> entry : termCounts.entrySet()) {
            tf.put(entry.getKey(), (double) entry.getValue() / totalTerms);
        }

        return tf;
    }

    /**
     * Calculate inverse document frequency (IDF) for terms.
     *
     * IDF = log(total documents / documents containing term) + 1
     * The +1 ensures non-zero IDF values for terms appearing in all documents
     */
    private Map<String, Double> calculateIDF(List<String> terms, List<Topic> corpus) {
        Map<String, Double> idf = new HashMap<>();
        int totalDocuments = corpus.size();

        for (String term : terms) {
            // Count how many documents contain this term
            long documentsWithTerm = corpus.stream()
                .filter(topic -> extractTerms(topic.getTitle()).contains(term))
                .count();

            // Calculate IDF with enhanced smoothing for small corpora
            // Formula: log((N+1)/(df+1)) + 2.0
            // Higher smoothing factor gives more weight to shared terms in small corpora
            double idfValue = Math.log((double) (totalDocuments + 1) / (documentsWithTerm + 1)) + 2.0;
            idf.put(term, idfValue);
        }

        return idf;
    }

    /**
     * Extract and normalize terms from text.
     *
     * - Convert to lowercase
     * - Split by whitespace and punctuation
     * - Remove stopwords
     * - Remove terms < 3 characters
     */
    private List<String> extractTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        return Arrays.stream(text.toLowerCase()
                .replaceAll("[^a-z\\s]", " ") // Remove punctuation
                .split("\\s+")) // Split by whitespace
            .filter(term -> term.length() >= 3) // Min 3 characters
            .filter(term -> !STOPWORDS.contains(term)) // Exclude stopwords
            .collect(Collectors.toList());
    }
}
