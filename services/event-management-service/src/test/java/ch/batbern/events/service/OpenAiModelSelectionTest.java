package ch.batbern.events.service;

import ch.batbern.events.config.AiConfig;
import ch.batbern.events.repository.AiGenerationLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Which OpenAI model each AI call uses (2026-11-07). OpenAI retired {@code gpt-image-1}, the
 * second image model retirement in six months, and theme images failed with "model does not
 * exist". Owner decision: every call uses OpenAI's "latest" alias, set in {@link AiConfig}
 * ({@code chat-latest}, {@code chatgpt-image-latest}). {@code chat-latest} accepts only the default
 * temperature (measured: 0.3 and 0.7 return 400), so no request sends one.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiModelSelectionTest {

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String IMAGE_URL = "https://api.openai.com/v1/images/generations";

    @Mock
    private AiConfig aiConfig;
    @Mock
    private AiGenerationLogRepository logRepository;
    @Mock
    private S3Client s3Client;
    @Mock
    private AiPromptService aiPromptService;

    private final ObjectMapper mapper = new ObjectMapper();
    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        lenient().when(aiConfig.isAiEnabled()).thenReturn(true);
        lenient().when(aiConfig.getApiKey()).thenReturn("test-key");
        lenient().when(aiConfig.getBaseUrl()).thenReturn("https://api.openai.com/v1");
        lenient().when(aiConfig.getChatModel()).thenReturn("chat-latest");
        lenient().when(aiConfig.getImageModel()).thenReturn("chatgpt-image-latest");
    }

    private BatbernAiService aiService() {
        BatbernAiService service = new BatbernAiService(aiConfig, logRepository, s3Client, aiPromptService);
        service.useClientBuilder(builder);
        service.init();
        return service;
    }

    private String chatResponse(String content) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", content)))));
    }

    @Test
    @DisplayName("the theme image uses the configured image model with the unchanged size and quality")
    void should_useConfiguredImageModel_when_generatingThemeImage() throws Exception {
        when(aiPromptService.getPromptText("theme_image")).thenReturn("Image for {{TOPIC_TITLE}}");
        String png = Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        server.expect(requestTo(IMAGE_URL))
                .andExpect(jsonPath("$.model").value("chatgpt-image-latest"))
                .andExpect(jsonPath("$.size").value("1536x1024"))
                .andExpect(jsonPath("$.quality").value("high"))
                .andRespond(withSuccess(
                        mapper.writeValueAsString(Map.of("data", List.of(Map.of("b64_json", png)))),
                        MediaType.APPLICATION_JSON));

        Optional<BatbernAiService.ThemeImageResult> out = aiService().generateThemeImage(
                "BATbern61", Map.of("TOPIC_TITLE", "FinOps", "TOPIC_CATEGORY", "CLOUD"), null);

        assertThat(out).isPresent();
        server.verify();
    }

    @Test
    @DisplayName("the event description uses the configured chat model and sends no temperature")
    void should_useConfiguredChatModelWithoutTemperature_when_generatingDescription() throws Exception {
        when(aiPromptService.getPromptText("event_description")).thenReturn("Describe {{EVENT_TITLE}}");
        server.expect(requestTo(CHAT_URL))
                .andExpect(jsonPath("$.model").value("chat-latest"))
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andRespond(withSuccess(chatResponse("A description."), MediaType.APPLICATION_JSON));

        Optional<String> out = aiService().generateEventDescription("BATbern61",
                Map.of("EVENT_TITLE", "BATbern 61"));

        assertThat(out).contains("A description.");
        server.verify();
    }

    @Test
    @DisplayName("the abstract review uses the configured chat model, JSON mode and no temperature")
    void should_useConfiguredChatModelInJsonMode_when_analyzingAbstract() throws Exception {
        when(aiPromptService.getPromptText("abstract_quality")).thenReturn("Review {{ABSTRACT}}");
        server.expect(requestTo(CHAT_URL))
                .andExpect(jsonPath("$.model").value("chat-latest"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andRespond(withSuccess(chatResponse("{\"wordCount\": 120}"), MediaType.APPLICATION_JSON));

        Optional<BatbernAiService.AbstractAnalysisResult> out = aiService().analyzeAbstract(
                "Jane Doe", "A talk", "An abstract.");

        assertThat(out).isPresent();
        assertThat(out.get().wordCount()).isEqualTo(120);
        server.verify();
    }

    @Test
    @DisplayName("trending topics use the configured chat model, no temperature, and read a fenced JSON reply")
    void should_parseFencedReply_when_trendingTopicsModelWrapsJsonInCodeFence() throws Exception {
        // Production 2026-11: 7 fallbacks in a week, "Unexpected character ('`')": the model wraps
        // the array in a Markdown code fence.
        String fenced = "```json\n[\"AI Agents\", \"FinOps\"]\n```";
        server.expect(requestTo(CHAT_URL))
                .andExpect(jsonPath("$.model").value("chat-latest"))
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andRespond(withSuccess(chatResponse(fenced), MediaType.APPLICATION_JSON));

        TrendingTopicsService trending = new TrendingTopicsService(mapper, aiConfig);
        trending.useClientBuilder(builder);
        trending.initOpenAiClient();

        assertThat(trending.getTrendingTopics()).containsExactly("AI Agents", "FinOps");
        server.verify();
    }
}
