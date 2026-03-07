package ch.batbern.events.service;

import ch.batbern.events.config.AiConfig;
import ch.batbern.events.repository.AiGenerationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatbernAiServiceTest {

    @Mock
    private AiConfig aiConfig;
    @Mock
    private AiGenerationLogRepository logRepository;
    @Mock
    private S3Client s3Client;
    @Mock
    private AiPromptService aiPromptService;

    private BatbernAiService aiService;

    @BeforeEach
    void setUp() {
        aiService = new BatbernAiService(aiConfig, logRepository, s3Client, aiPromptService);
    }

    @Nested
    @DisplayName("when AI is disabled")
    class WhenAiDisabled {

        @BeforeEach
        void setup() {
            when(aiConfig.isAiEnabled()).thenReturn(false);
        }

        @Test
        @DisplayName("generateEventDescription returns empty")
        void generateDescription_disabled_returnsEmpty() {
            Optional<String> result = aiService.generateEventDescription("BATbern99", "BATbern 99", "Cloud Native", "Topic desc", "DEVOPS", 99, "2026-04-04", "");
            assertThat(result).isEmpty();
            verifyNoInteractions(logRepository);
        }

        @Test
        @DisplayName("generateThemeImage returns empty")
        void generateThemeImage_disabled_returnsEmpty() {
            Optional<BatbernAiService.ThemeImageResult> result = aiService.generateThemeImage("BATbern99", "Cloud Native", "Topic desc", "DEVOPS", "BATbern 99", "", null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("analyzeAbstract returns empty")
        void analyzeAbstract_disabled_returnsEmpty() {
            Optional<BatbernAiService.AbstractAnalysisResult> result = aiService.analyzeAbstract("Alice", "My Session", "My abstract text");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("when AI is enabled but API key missing")
    class WhenApiKeyMissing {

        @BeforeEach
        void setup() {
            when(aiConfig.isAiEnabled()).thenReturn(true);
            when(aiConfig.getApiKey()).thenReturn("");
            // Call init() so @PostConstruct logic runs; getApiKey() is actually invoked
            // getBaseUrl() is NOT stubbed because init() short-circuits before calling it
            aiService.init();
        }

        @Test
        @DisplayName("generateEventDescription returns empty gracefully")
        void generateDescription_noApiKey_returnsEmpty() {
            Optional<String> result = aiService.generateEventDescription("BATbern99", "BATbern 99", "Cloud Native", "Topic desc", "DEVOPS", 99, "2026-04-04", "");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("generateThemeImage returns empty gracefully")
        void generateThemeImage_noApiKey_returnsEmpty() {
            Optional<BatbernAiService.ThemeImageResult> result = aiService.generateThemeImage("BATbern99", "Cloud Native", "Topic desc", "DEVOPS", "BATbern 99", "", null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("analyzeAbstract returns empty gracefully")
        void analyzeAbstract_noApiKey_returnsEmpty() {
            Optional<BatbernAiService.AbstractAnalysisResult> result = aiService.analyzeAbstract("Alice", "My Session", "My abstract text");
            assertThat(result).isEmpty();
        }
    }
}
