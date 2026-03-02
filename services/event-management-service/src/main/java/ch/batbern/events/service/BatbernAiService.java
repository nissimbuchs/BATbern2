package ch.batbern.events.service;

import ch.batbern.events.config.AiConfig;
import ch.batbern.events.domain.AiGenerationLog;
import ch.batbern.events.repository.AiGenerationLogRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class BatbernAiService {

    private final AiConfig aiConfig;
    private final AiGenerationLogRepository logRepository;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}")
    private String cloudfrontDomain;

    @Value("${aws.s3.bucket-name:batbern-development-company-logos}")
    private String s3BucketName;

    @Autowired
    public BatbernAiService(AiConfig aiConfig,
                            AiGenerationLogRepository logRepository,
                            S3Client s3Client,
                            ObjectMapper objectMapper) {
        this.aiConfig = aiConfig;
        this.logRepository = logRepository;
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
    }

    /** Constructor for unit tests — no ObjectMapper needed since no JSON parsing exercised. */
    BatbernAiService(AiConfig aiConfig,
                     AiGenerationLogRepository logRepository,
                     S3Client s3Client) {
        this(aiConfig, logRepository, s3Client, new ObjectMapper());
    }

    // Caffeine cache: 1-hour TTL, max 500 entries
    private Cache<String, Object> resultCache;
    private RestClient openAiClient;

    @PostConstruct
    void init() {
        resultCache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(500)
                .build();

        if (aiConfig.isAiEnabled() && apiKey() != null && !apiKey().isBlank()) {
            openAiClient = RestClient.builder()
                    .baseUrl(aiConfig.getBaseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        }
    }

    private String apiKey() {
        return aiConfig.getApiKey();
    }

    /** Returns empty if AI disabled, API key absent, or call fails. */
    public Optional<String> generateEventDescription(String topicTitle, String topicCategory,
                                                     int eventNumber,
                                                     String eventTitle, String eventDate) {
        if (!aiConfig.isAiEnabled() || openAiClient == null) {
            return Optional.empty();
        }

        String cacheKey = "desc:" + hash(topicTitle + topicCategory + eventNumber
                + (eventTitle != null ? eventTitle : "") + (eventDate != null ? eventDate : ""));
        Object cached = resultCache.getIfPresent(cacheKey);
        if (cached instanceof String s) {
            return Optional.of(s);
        }

        try {
            String eventLabel = (eventTitle != null && !eventTitle.isBlank()) ? eventTitle : topicTitle;
            String dateLine = (eventDate != null && !eventDate.isBlank())
                ? "Event date: " + eventDate + "."
                : "";
            String prompt = String.format(
                "Write a German event description for BATbern#%d, the Berner Architekten Treffen – "
                    + "a community evening event in Bern where local IT professionals and companies "
                    + "share hands-on experience with current hot topics in software architecture and engineering.\n\n"
                    + "This BATbern event is entirely dedicated to the topic: \"%s\" (category: %s).\n"
                    + "%s\n\n"
                    + "Structure (2-3 paragraphs, 120-160 words total, in professional German):\n"
                    + "1. Set the industry context: what is happening in the field, what trends/challenges/tools "
                    + "   are relevant to this topic right now.\n"
                    + "2. Describe what will happen at THIS BATbern: local companies and speakers present their "
                    + "   real-world approaches, practical experience, and lessons learned – "
                    + "   not academic talks, but practitioner insights.\n"
                    + "3. End with a sentence in this style (adapt to the topic): "
                    + "   'An diesem BAT stellen unsere Referenten ihre Ansätze und Lessons Learned vor.'\n\n"
                    + "Important: use only the exact date provided (do not invent or omit dates). "
                    + "The event is a single evening, not a multi-day conference. "
                    + "Do not say 'Konferenz' or 'Session' – say 'Veranstaltung' or 'BAT'.",
                eventNumber, eventLabel, topicCategory, dateLine);

            String content = callChatCompletions("gpt-4o", prompt);
            if (content == null) {
                return Optional.empty();
            }

            resultCache.put(cacheKey, content);
            logGeneration(null, "description", cacheKey, null);
            return Optional.of(content);
        } catch (Exception e) {
            log.warn("AI description generation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Downloads DALL-E image and uploads to S3. Returns empty on any failure. */
    public Optional<ThemeImageResult> generateThemeImage(String topicTitle, String topicCategory,
                                                         String eventTitle) {
        if (!aiConfig.isAiEnabled() || openAiClient == null) {
            return Optional.empty();
        }

        String effectiveTitle = (eventTitle != null && !eventTitle.isBlank()) ? eventTitle : topicTitle;
        String cacheKey = "img:" + hash(effectiveTitle + topicCategory);
        Object cached = resultCache.getIfPresent(cacheKey);
        if (cached instanceof ThemeImageResult r) {
            return Optional.of(r);
        }

        try {
            String dallePrompt = String.format(
                "Ultra-wide banner illustration for BATbern – the Berner Architekten Treffen, "
                    + "a Swiss IT architecture community event in Bern. "
                    + "Topic: \"%s\" (category: %s). "
                    + "Style: deep midnight navy/black background, glowing neon cyan and electric blue "
                    + "abstract digital elements, circuit board traces, network connection nodes, "
                    + "central symbolic motif strongly representing the topic theme, "
                    + "volumetric atmospheric glow, dramatic cinematic lighting, "
                    + "ultra-detailed photorealistic digital illustration, dark futuristic tech aesthetic, "
                    + "16:9 landscape format. No text. No logos. No people.",
                effectiveTitle, topicCategory);

            String dalleImageUrl = callImageGeneration(dallePrompt);
            if (dalleImageUrl == null) {
                return Optional.empty();
            }

            byte[] imageBytes = downloadBytes(dalleImageUrl);
            if (imageBytes == null) {
                return Optional.empty();
            }

            String s3Key = "ai-themes/" + cacheKey + ".png";
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType("image/png")
                    .build(),
                RequestBody.fromBytes(imageBytes)
            );

            String imageUrl = cloudfrontDomain + "/" + s3Key;
            ThemeImageResult result = new ThemeImageResult(imageUrl, s3Key);
            resultCache.put(cacheKey, result);
            logGeneration(null, "theme_image", cacheKey, null);
            return Optional.of(result);
        } catch (Exception e) {
            log.warn("AI theme image generation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Returns empty if AI disabled, API key absent, or call fails. */
    public Optional<AbstractAnalysisResult> analyzeAbstract(String abstractText, String speakerName) {
        if (!aiConfig.isAiEnabled() || openAiClient == null) {
            return Optional.empty();
        }

        String cacheKey = "abs:" + hash(abstractText);
        Object cached = resultCache.getIfPresent(cacheKey);
        if (cached instanceof AbstractAnalysisResult r) {
            return Optional.of(r);
        }

        try {
            String prompt = String.format(
                "Analyze this speaker abstract for BATbern (Swiss software architecture conference). "
                    + "Speaker: %s. Abstract: \"%s\". "
                    + "Return JSON: {\"qualityScore\": 0-10, \"suggestion\": \"string\", "
                    + "\"improvedAbstract\": \"string\", \"keyThemes\": [\"string\"]}",
                speakerName != null ? speakerName : "Unknown", abstractText);

            String content = callChatCompletionsJson("gpt-4o", prompt);
            if (content == null) {
                return Optional.empty();
            }

            AbstractAnalysisResult result = parseAbstractAnalysis(content);
            resultCache.put(cacheKey, result);
            logGeneration(null, "abstract_analysis", cacheKey, null);
            return Optional.of(result);
        } catch (Exception e) {
            log.warn("AI abstract analysis failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ==================== Private helpers ====================

    private String callChatCompletions(String model, String prompt) {
        Map<String, Object> body = Map.of(
            "model", model,
            "temperature", 0.7,
            "messages", List.of(Map.of("role", "user", "content", prompt))
        );
        OpenAiChatResponse resp = openAiClient.post()
            .uri("/chat/completions")
            .body(body)
            .retrieve()
            .body(OpenAiChatResponse.class);
        if (resp == null || resp.choices() == null || resp.choices().isEmpty()) {
            return null;
        }
        return resp.choices().get(0).message().content();
    }

    private String callChatCompletionsJson(String model, String prompt) {
        Map<String, Object> body = Map.of(
            "model", model,
            "temperature", 0.3,
            "response_format", Map.of("type", "json_object"),
            "messages", List.of(Map.of("role", "user", "content", prompt))
        );
        OpenAiChatResponse resp = openAiClient.post()
            .uri("/chat/completions")
            .body(body)
            .retrieve()
            .body(OpenAiChatResponse.class);
        if (resp == null || resp.choices() == null || resp.choices().isEmpty()) {
            return null;
        }
        return resp.choices().get(0).message().content();
    }

    private String callImageGeneration(String prompt) {
        Map<String, Object> body = Map.of(
            "model", "dall-e-3",
            "prompt", prompt,
            "n", 1,
            "size", "1792x1024",
            "quality", "standard"
        );
        OpenAiImageResponse resp = openAiClient.post()
            .uri("/images/generations")
            .body(body)
            .retrieve()
            .body(OpenAiImageResponse.class);
        if (resp == null || resp.data() == null || resp.data().isEmpty()) {
            return null;
        }
        return resp.data().get(0).url();
    }

    private byte[] downloadBytes(String url) {
        try {
            // Use Java's native HttpClient to avoid RestClient URI template expansion
            // which re-encodes the SAS token signature in Azure Blob URLs returned by DALL-E.
            var client = java.net.http.HttpClient.newHttpClient();
            var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .GET()
                .build();
            var response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                log.warn("Failed to download DALL-E image: HTTP {}", response.statusCode());
                return null;
            }
            return response.body();
        } catch (Exception e) {
            log.warn("Failed to download DALL-E image: {}", e.getMessage());
            return null;
        }
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashed = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    private AbstractAnalysisResult parseAbstractAnalysis(String json) {
        try {
            var node = objectMapper.readTree(json);
            int qualityScore = node.path("qualityScore").asInt(5);
            String suggestion = node.path("suggestion").asText("");
            String improvedAbstract = node.path("improvedAbstract").asText("");
            List<String> keyThemes = objectMapper.convertValue(
                node.path("keyThemes"), objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
            return new AbstractAnalysisResult(qualityScore, suggestion, improvedAbstract, keyThemes);
        } catch (Exception e) {
            log.warn("Failed to parse abstract analysis JSON: {}", e.getMessage());
            return new AbstractAnalysisResult(5, "Unable to parse AI response", json, List.of());
        }
    }

    private void logGeneration(String eventCode, String type, String inputHash, Integer tokensUsed) {
        try {
            AiGenerationLog entry = new AiGenerationLog();
            entry.setId(UUID.randomUUID());
            entry.setEventCode(eventCode);
            entry.setType(type);
            entry.setInputHash(inputHash);
            entry.setGeneratedAt(Instant.now());
            entry.setTokensUsed(tokensUsed);
            entry.setWasAccepted(null);
            logRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to persist ai_generation_log: {}", e.getMessage());
        }
    }

    // ==================== Internal types ====================

    public record ThemeImageResult(String imageUrl, String s3Key) {}

    public record AbstractAnalysisResult(
        int qualityScore,
        String suggestion,
        String improvedAbstract,
        List<String> keyThemes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiChatResponse(List<Choice> choices) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Choice(Message message) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            record Message(String content) {}
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiImageResponse(List<ImageData> data) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record ImageData(String url) {}
    }
}
