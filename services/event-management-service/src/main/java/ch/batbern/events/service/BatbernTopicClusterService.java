package ch.batbern.events.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static ch.batbern.events.service.BatbernCluster.AI_ML;
import static ch.batbern.events.service.BatbernCluster.ARCHITECTURE;
import static ch.batbern.events.service.BatbernCluster.BUSINESS_OTHER;
import static ch.batbern.events.service.BatbernCluster.CLOUD_INFRA;
import static ch.batbern.events.service.BatbernCluster.DATA;
import static ch.batbern.events.service.BatbernCluster.MOBILE;
import static ch.batbern.events.service.BatbernCluster.SECURITY;

/**
 * Provides cluster classification for BATbern past events and topic texts (Story 10.4).
 *
 * Two operations:
 * - getCluster(eventNumber) → looks up the hardcoded event→cluster map; defaults to BUSINESS_OTHER
 * - matchCluster(topicText)  → keyword-based classifier; falls back to BUSINESS_OTHER
 *
 * The cluster taxonomy drives the red-star repulsion logic in the blob topic selector canvas:
 * past events in the same cluster as a proposed topic "ignite" and push back.
 */
@Service
public class BatbernTopicClusterService {

    private static final Map<Integer, BatbernCluster> EVENT_CLUSTERS = Map.ofEntries(
            // AI / ML
            Map.entry(40, AI_ML), Map.entry(44, AI_ML), Map.entry(49, AI_ML),
            Map.entry(56, AI_ML), Map.entry(58, AI_ML),
            // Security
            Map.entry(16, SECURITY), Map.entry(27, SECURITY), Map.entry(38, SECURITY),
            Map.entry(48, SECURITY), Map.entry(57, SECURITY),
            // Architecture
            Map.entry(2, ARCHITECTURE), Map.entry(3, ARCHITECTURE), Map.entry(12, ARCHITECTURE),
            Map.entry(13, ARCHITECTURE), Map.entry(30, ARCHITECTURE), Map.entry(41, ARCHITECTURE),
            Map.entry(43, ARCHITECTURE), Map.entry(55, ARCHITECTURE),
            // Data
            Map.entry(15, DATA), Map.entry(18, DATA), Map.entry(29, DATA),
            Map.entry(33, DATA), Map.entry(52, DATA), Map.entry(53, DATA),
            // Cloud / Infra
            Map.entry(36, CLOUD_INFRA), Map.entry(39, CLOUD_INFRA),
            Map.entry(51, CLOUD_INFRA), Map.entry(54, CLOUD_INFRA),
            // Mobile
            Map.entry(22, MOBILE), Map.entry(26, MOBILE)
    );

    // Keywords per cluster (checked case-insensitively against topic text)
    private static final Map<BatbernCluster, List<String>> CLUSTER_KEYWORDS = Map.of(
            AI_ML, List.of(
                    "ai", "artificial intelligence", "machine learning", "ml", "deep learning",
                    "neural", "llm", "gpt", "nlp", "generative", "mlops", "agentic", "agents"
            ),
            SECURITY, List.of(
                    "security", "cybersecurity", "zero trust", "auth", "iam", "siem",
                    "vulnerability", "penetration", "pentest", "cyber", "devsecops", "soc"
            ),
            ARCHITECTURE, List.of(
                    "architecture", "microservices", "domain-driven", "ddd", "api design",
                    "event sourcing", "cqrs", "modular monolith", "hexagonal", "clean architecture",
                    "design patterns", "service mesh"
            ),
            DATA, List.of(
                    "data", "analytics", "pipeline", "warehouse", "lakehouse", "kafka",
                    "spark", "sql", "nosql", "database", "dbt", "etl", "elt", "streaming"
            ),
            CLOUD_INFRA, List.of(
                    "cloud", "kubernetes", "k8s", "docker", "container", "devops",
                    "infrastructure", "iac", "terraform", "finops", "sre", "platform engineering",
                    "gitops", "ci/cd", "observability"
            ),
            MOBILE, List.of(
                    "mobile", "ios", "android", "flutter", "react native", "swift",
                    "kotlin", "pwa", "app development"
            ),
            BUSINESS_OTHER, List.of()
    );

    /**
     * Returns the BATbern cluster for a known event number.
     * Events not in the hardcoded map default to BUSINESS_OTHER.
     *
     * @param eventNumber the BATbern event number (e.g. 57)
     * @return the cluster enum value
     */
    public BatbernCluster getCluster(int eventNumber) {
        return EVENT_CLUSTERS.getOrDefault(eventNumber, BUSINESS_OTHER);
    }

    /**
     * Returns all known BATbern event numbers that belong to the given cluster.
     * Used by TopicSimilarityService to populate relatedPastEventNumbers[].
     *
     * @param cluster the cluster to look up
     * @return sorted list of event numbers in that cluster; empty list for BUSINESS_OTHER
     */
    public List<Integer> getEventNumbersForCluster(BatbernCluster cluster) {
        if (cluster == BUSINESS_OTHER) {
            return List.of();
        }
        return EVENT_CLUSTERS.entrySet().stream()
                .filter(e -> e.getValue() == cluster)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /**
     * Classifies free-form topic text into a BATbern cluster using keyword matching.
     * Falls back to BUSINESS_OTHER when no keywords match.
     *
     * Match priority order: AI_ML → SECURITY → ARCHITECTURE → DATA → CLOUD_INFRA → MOBILE → BUSINESS_OTHER
     * (OpenAI embeddings integration is handled in TopicSimilarityService — Task 3)
     *
     * @param topicText the topic title or description (may be blank)
     * @return the closest matching cluster
     */
    public BatbernCluster matchCluster(String topicText) {
        if (topicText == null || topicText.isBlank()) {
            return BUSINESS_OTHER;
        }
        String lower = topicText.toLowerCase();

        for (BatbernCluster cluster : List.of(AI_ML, SECURITY, ARCHITECTURE, DATA, CLOUD_INFRA, MOBILE)) {
            List<String> keywords = CLUSTER_KEYWORDS.get(cluster);
            if (keywords != null && keywords.stream().anyMatch(kw -> containsWholeWord(lower, kw))) {
                return cluster;
            }
        }
        return BUSINESS_OTHER;
    }

    /**
     * Returns true if {@code keyword} appears as a whole word (word-boundary delimited) inside {@code text}.
     * This prevents short keywords like "ai" from matching substrings in "domain" or "blockchain".
     */
    private boolean containsWholeWord(String text, String keyword) {
        String escaped = Pattern.quote(keyword);
        return Pattern.compile("\\b" + escaped + "\\b").matcher(text).find();
    }
}
