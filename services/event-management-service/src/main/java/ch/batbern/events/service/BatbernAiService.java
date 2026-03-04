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
    public Optional<String> generateEventDescription(String eventCode, String topicTitle, String topicCategory,
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
            logGeneration(eventCode, "description", cacheKey, null);
            return Optional.of(content);
        } catch (Exception e) {
            log.warn("AI description generation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Downloads DALL-E image and uploads to S3. Returns empty on any failure. */
    public Optional<ThemeImageResult> generateThemeImage(String eventCode, String topicTitle, String topicCategory,
                                                         String eventTitle, String eventDescription,
                                                         String seed) {
        if (!aiConfig.isAiEnabled() || openAiClient == null) {
            return Optional.empty();
        }

        String effectiveTitle = (eventTitle != null && !eventTitle.isBlank()) ? eventTitle : topicTitle;
        String cacheKey = "img:" + hash(effectiveTitle + topicCategory + (seed != null ? seed : ""));
        Object cached = resultCache.getIfPresent(cacheKey);
        if (cached instanceof ThemeImageResult r) {
            return Optional.of(r);
        }

        try {
            String contextLine = (eventDescription != null && !eventDescription.isBlank())
                ? "Context: " + eventDescription.substring(0, Math.min(eventDescription.length(), 300)) + " "
                : "";
            String dallePrompt = String.format(
                "Abstract digital artwork that visually represents the IT topic: \"%s\" (category: %s). "
                    + "%s"
                    + "Use abstract visual metaphors, symbols, and imagery that are directly related to "
                    + "this specific topic and category — not generic circuit boards. "
                    + "Style: dark midnight navy-to-black background, glowing neon cyan and electric blue "
                    + "abstract digital elements covering the full frame uniformly from corner to corner. "
                    + "Flat 2D digital illustration – no 3D perspective, no room, no floor, no staging, "
                    + "no display panel, no frame, no spotlights, no shadow on a surface. "
                    + "The image fills the entire 16:9 rectangle edge-to-edge. "
                    + "No text. No logos. No people.",
                effectiveTitle, topicCategory, contextLine);

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
                "Analyze this speaker abstract for BATbern – a Swiss IT architecture community event "
                    + "where practitioners share real-world experience and lessons learned "
                    + "(NOT product demos or service sales pitches). "
                    + "Speaker: %s. Abstract: \"%s\".\n\n"
                    + "Evaluate these two criteria, rate each from 1 to 10 "
                    + "(10 = perfectly aligned, 1 = completely misaligned):\n"
                    + "1. noPromotion: Does the abstract avoid promoting an IT product, tool, or service? "
                    + "(10 = purely about experience/knowledge; 1 = reads like a product advertisement)\n"
                    + "2. lessonsLearned: Does the abstract suggest the speaker will share practical "
                    + "lessons learned from real-world experience? "
                    + "(10 = clearly hands-on experience and lessons; 1 = no indication of practical experience)\n\n"
                    + "Also count the words in the abstract. "
                    + "If the word count exceeds 160, provide a shortened German version of maximum 150 words "
                    + "that preserves the key message. If 160 or fewer words, set shortenedAbstract to null.\n\n"
                    + "Return JSON only (no other text):\n"
                    + "{\"noPromotionScore\": <1-10>, "
                    + "\"noPromotionFeedback\": \"<brief German explanation, 1-2 sentences>\", "
                    + "\"lessonsLearnedScore\": <1-10>, "
                    + "\"lessonsLearnedFeedback\": \"<brief German explanation, 1-2 sentences>\", "
                    + "\"wordCount\": <number>, "
                    + "\"shortenedAbstract\": \"<shortened German text or null>\"}",
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

    private static boolean isAllowedImageUrl(String url) {
        if (url == null) return false;
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
