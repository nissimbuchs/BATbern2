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
import java.util.LinkedHashMap;
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
    private final AiPromptService aiPromptService;

    @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}")
    private String cloudfrontDomain;

    @Value("${aws.s3.bucket-name:batbern-development-company-logos}")
    private String s3BucketName;

    @Autowired
    public BatbernAiService(AiConfig aiConfig,
                            AiGenerationLogRepository logRepository,
                            S3Client s3Client,
                            ObjectMapper objectMapper,
                            AiPromptService aiPromptService) {
        this.aiConfig = aiConfig;
        this.logRepository = logRepository;
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.aiPromptService = aiPromptService;
    }

    /** Constructor for unit tests — no ObjectMapper needed since no JSON parsing exercised. */
    BatbernAiService(AiConfig aiConfig,
                     AiGenerationLogRepository logRepository,
                     S3Client s3Client,
                     AiPromptService aiPromptService) {
        this(aiConfig, logRepository, s3Client, new ObjectMapper(), aiPromptService);
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
    public Optional<String> generateEventDescription(String eventCode, String eventTitle, String topicTitle,
                                                     String topicDescription, String topicCategory,
                                                     int eventNumber, String eventDate, String eventDescription) {
        if (!aiConfig.isAiEnabled() || openAiClient == null) {
            return Optional.empty();
        }

        String cacheKey = "desc:" + hash(eventCode + eventTitle + topicTitle + topicCategory + eventNumber + eventDate);
        Object cached = resultCache.getIfPresent(cacheKey);
        if (cached instanceof String s) {
            return Optional.of(s);
        }

        try {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("EVENT_NR", String.valueOf(eventNumber));
            vars.put("EVENT_TITLE", eventTitle);
            vars.put("TOPIC_TITLE", topicTitle);
            vars.put("TOPIC_DESCRIPTION", topicDescription);
            vars.put("TOPIC_CATEGORY", topicCategory);
            vars.put("EVENT_DATE", eventDate);
            vars.put("EVENT_DESCRIPTION", eventDescription);
            String prompt = applyVariables(aiPromptService.getPromptText("event_description"), vars);

            String content = callChatCompletions("gpt-4o", prompt);
            if (content == null) {
                return Optional.empty();
            }

            resultCache.put(cacheKey, content);
            logGeneration(eventCode, "description", cacheKey, null);
            return Optional.of(content);
        } catch (Exception e) {
            log.warn("AI description generation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Downloads DALL-E image and uploads to S3. Returns empty on any failure. */
    public Optional<ThemeImageResult> generateThemeImage(String eventCode, String topicTitle, String topicDescription,
                                                         String topicCategory, String eventTitle,
                                                         String eventDescription, String seed) {
        if (!aiConfig.isAiEnabled() || openAiClient == null) {
            return Optional.empty();
        }

        String cacheKey = "img:" + hash(eventCode + topicTitle + topicCategory + (seed != null ? seed : ""));
        Object cached = resultCache.getIfPresent(cacheKey);
        if (cached instanceof ThemeImageResult r) {
            return Optional.of(r);
        }

        try {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("TOPIC_TITLE", topicTitle);
            vars.put("TOPIC_DESCRIPTION", topicDescription);
            vars.put("TOPIC_CATEGORY", topicCategory);
            vars.put("EVENT_TITLE", eventTitle);
            vars.put("EVENT_DESCRIPTION", eventDescription);
            String dallePrompt = applyVariables(aiPromptService.getPromptText("theme_image"), vars);

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
            logGeneration(eventCode, "theme_image", cacheKey, null);
            return Optional.of(result);
        } catch (Exception e) {
            log.warn("AI theme image generation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Returns empty if AI disabled, API key absent, or call fails. */
    public Optional<AbstractAnalysisResult> analyzeAbstract(String speakerName, String sessionTitle,
                                                             String abstractText) {
        if (!aiConfig.isAiEnabled() || openAiClient == null) {
            return Optional.empty();
        }

        String cacheKey = "abs:" + hash(abstractText);
        Object cached = resultCache.getIfPresent(cacheKey);
        if (cached instanceof AbstractAnalysisResult r) {
            return Optional.of(r);
        }

        try {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("SPEAKER_NAME", speakerName != null ? speakerName : "Unknown");
            vars.put("SESSION_TITLE", sessionTitle != null ? sessionTitle : "");
            vars.put("ABSTRACT", abstractText);
            String prompt = applyVariables(aiPromptService.getPromptText("abstract_quality"), vars);

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

    /**
     * Replaces all {{VAR_NAME}} placeholders in template with values from vars map.
     * Missing or null values are replaced with an empty string.
     */
    static String applyVariables(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

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

    private static boolean isAllowedImageUrl(String url) {
        if (url == null) {
            return false;
        }
        return url.startsWith("https://oaidalleapiprodscus.blob.core.windows.net/")
            || url.startsWith("https://dalleprodsec.blob.core.windows.net/")
            || url.contains(".openai.com/");
    }

    private byte[] downloadBytes(String url) {
        try {
            if (!isAllowedImageUrl(url)) {
                log.warn("Refusing to download from untrusted URL: {}", url);
                return null;
            }
            // Use HttpURLConnection (not HttpClient / URI.create) because DALL-E returns
            // Azure Blob SAS URLs whose signature contains base64 '+' characters.
            // URI.create() normalises those, corrupting the signature and causing
            // Azure to reject with 403 AuthenticationFailed / Signature not well formed.
            // HttpURLConnection accepts the raw URL string without any URI parsing.
            @SuppressWarnings("deprecation")
            var conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            int status = conn.getResponseCode();
            if (status >= 400) {
                log.warn("Failed to download DALL-E image: HTTP {}", status);
                return null;
            }
            try (var in = conn.getInputStream()) {
                return in.readAllBytes();
            }
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
            int noPromotionScore = node.path("noPromotionScore").asInt(5);
            String noPromotionFeedback = node.path("noPromotionFeedback").asText("");
            int lessonsLearnedScore = node.path("lessonsLearnedScore").asInt(5);
            String lessonsLearnedFeedback = node.path("lessonsLearnedFeedback").asText("");
            int wordCount = node.path("wordCount").asInt(0);
            String shortenedAbstract = node.path("shortenedAbstract").isNull()
                ? null : node.path("shortenedAbstract").asText(null);
            return new AbstractAnalysisResult(
                noPromotionScore, noPromotionFeedback,
                lessonsLearnedScore, lessonsLearnedFeedback,
                wordCount, shortenedAbstract);
        } catch (Exception e) {
            log.warn("Failed to parse abstract analysis JSON: {}", e.getMessage());
            return new AbstractAnalysisResult(5, "", 5, "", 0, null);
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
        int noPromotionScore,
        String noPromotionFeedback,
        int lessonsLearnedScore,
        String lessonsLearnedFeedback,
        int wordCount,
        String shortenedAbstract
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
