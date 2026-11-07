package ch.batbern.events.service;

import ch.batbern.events.config.AiConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches AI-generated trending IT topics via OpenAI Chat Completions (Story 10.4 Task 4.6).
 *
 * Caches the result for 1 hour in-process using a ConcurrentHashMap to avoid
 * repeated API calls during a blob selector session.
 *
 * Always returns a non-error response: falls back to a hardcoded list if OpenAI is
 * unavailable or the API key is not configured. This is per AC 9: trending topics are
 * optional enhancements — the endpoint must never fail because of them.
 */
@Service
@Slf4j
public class TrendingTopicsService {

    public static final List<String> FALLBACK_TOPICS = List.of(
            "AI Agents", "Platform Engineering", "FinOps", "Rust Language",
            "WebAssembly", "Cybersecurity Mesh", "Sovereign Cloud",
            "Digital Twin", "Edge AI", "Developer Experience"
    );

    private static final String PROMPT = """
            List exactly 10 currently trending IT architecture and software engineering topics \
            relevant to Swiss enterprise teams. Return ONLY a JSON array of short topic strings, \
            max 4 words each. Example: ["AI Agents","Platform Engineering"]""";

    private static final long CACHE_TTL_MS = 60 * 60 * 1000L; // 1 hour
    private static final String CACHE_KEY = "trending";

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    private final AiConfig aiConfig;

    /** Singleton RestClient, initialized via @PostConstruct. Null when API key absent. */
    private RestClient openAiClient;

    /** Test seam: when set, {@link #initOpenAiClient()} builds from it (MockRestServiceServer). */
    private RestClient.Builder clientBuilder;

    public TrendingTopicsService(ObjectMapper objectMapper, AiConfig aiConfig) {
        this.objectMapper = objectMapper;
        this.aiConfig = aiConfig;
    }

    /** Test-only hook; call before {@link #initOpenAiClient()}. */
    void useClientBuilder(RestClient.Builder builder) {
        this.clientBuilder = builder;
    }

    @PostConstruct
    void initOpenAiClient() {
        String apiKey = aiConfig.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            this.openAiClient = (clientBuilder != null ? clientBuilder : RestClient.builder())
                    .baseUrl(aiConfig.getBaseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        }
    }

    /**
     * Returns a list of 10 trending IT topics.
     * Uses cached result if younger than 1 hour; otherwise calls OpenAI (when API key present).
     * Falls back to hardcoded list on any failure.
     *
     * @return non-null, non-empty list of topic strings
     */
    public List<String> getTrendingTopics() {
        CacheEntry cached = cache.get(CACHE_KEY);
        if (cached != null && !cached.isExpired()) {
            return cached.topics();
        }

        List<String> fresh = fetchFromOpenAi();
        cache.put(CACHE_KEY, new CacheEntry(fresh, Instant.now().toEpochMilli() + CACHE_TTL_MS));
        return fresh;
    }

    private List<String> fetchFromOpenAi() {
        if (openAiClient == null) {
            log.debug("OpenAI API key not configured — returning fallback trending topics");
            return FALLBACK_TOPICS;
        }

        try {
            // Story 15.9: route through the shared SYSTEM_GUARD so EVERY chat-completions call
            // carries the privileged guard message — even though this prompt is currently static
            // (no untrusted interpolation), this keeps the contract true and future-proofs the path.
            Map<String, Object> requestBody = Map.of(
                    // chat-latest accepts only the default temperature, so none is sent
                    "model", aiConfig.getChatModel(),
                    "messages", BatbernAiService.buildMessages(PROMPT)
            );

            OpenAiChatResponse response = openAiClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(OpenAiChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                log.warn("OpenAI returned empty response — using fallback");
                return FALLBACK_TOPICS;
            }

            String content = stripCodeFence(response.choices().get(0).message().content());
            List<String> topics = objectMapper.readValue(content, new TypeReference<List<String>>() {});
            log.debug("Fetched {} trending topics from OpenAI", topics.size());
            return topics;

        } catch (Exception e) {
            log.warn("OpenAI trending topics call failed — using fallback: {}", e.getMessage());
            return FALLBACK_TOPICS;
        }
    }

    /**
     * The model may wrap the array in a Markdown code fence ("```json ... ```"); 7 fallbacks in
     * production in the week to 2026-11-07. Returns the text between the fences, or the input.
     */
    static String stripCodeFence(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int closing = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || closing <= firstLineEnd) {
            return trimmed;
        }
        return trimmed.substring(firstLineEnd + 1, closing).strip();
    }

    // ==================== Internal cache + OpenAI response DTOs ====================

    private record CacheEntry(List<String> topics, long expiresAtMs) {
        boolean isExpired() {
            return Instant.now().toEpochMilli() > expiresAtMs;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiChatResponse(List<Choice> choices) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Choice(Message message) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            record Message(String content) {}
        }
    }
}
