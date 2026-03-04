package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.service.BatbernAiService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestSecurityConfig.class, TestAwsConfig.class})
class AiAssistControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    BatbernAiService aiService;

    @Test
    void getFeatureFlags_isPublic_returnsAiEnabledFlag() throws Exception {
        // Public endpoint — no auth required
        mockMvc.perform(get("/api/v1/public/settings/features"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aiContentEnabled").isBoolean());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateDescription_whenAiReturnsEmpty_returns503() throws Exception {
        when(aiService.generateEventDescription(anyString(), anyString(), anyString(), anyInt(), any(), any()))
            .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/events/BATbern99/ai/description")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"topicTitle\":\"Cloud Native\",\"topicCategory\":\"DEVOPS\"}"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateDescription_whenAiReturnsResult_returns200() throws Exception {
        when(aiService.generateEventDescription(anyString(), anyString(), anyString(), anyInt(), any(), any()))
            .thenReturn(Optional.of("BATbern#99 widmet sich dem Thema Cloud Native..."));

        mockMvc.perform(post("/api/v1/events/BATbern99/ai/description")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"topicTitle\":\"Cloud Native\",\"topicCategory\":\"DEVOPS\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateThemeImage_whenAiReturnsEmpty_returns503() throws Exception {
        when(aiService.generateThemeImage(anyString(), anyString(), anyString(), any(), any(), any()))
            .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/events/BATbern99/ai/theme-image")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"topicTitle\":\"Cloud Native\",\"topicCategory\":\"DEVOPS\"}"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateThemeImage_whenAiReturnsResult_returns200() throws Exception {
        var result = new BatbernAiService.ThemeImageResult(
            "https://cdn.batbern.ch/ai-themes/abc.png", "ai-themes/abc.png");
        when(aiService.generateThemeImage(anyString(), anyString(), anyString(), any(), any(), any()))
            .thenReturn(Optional.of(result));

        mockMvc.perform(post("/api/v1/events/BATbern99/ai/theme-image")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"topicTitle\":\"Cloud Native\",\"topicCategory\":\"DEVOPS\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imageUrl").isNotEmpty())
            .andExpect(jsonPath("$.s3Key").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void analyzeAbstract_whenAiReturnsResult_returns200() throws Exception {
        var result = new BatbernAiService.AbstractAnalysisResult(
            8, "Kein Produktmarketing erkennbar.", 9, "Klare Praxisreferenz vorhanden.", 120, null);
        when(aiService.analyzeAbstract(anyString(), any())).thenReturn(Optional.of(result));

        mockMvc.perform(post("/api/v1/speakers/some-speaker-id/ai/analyze-abstract")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"abstract\":\"My speaker abstract\",\"speakerName\":\"Alice\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.noPromotionScore").value(8))
            .andExpect(jsonPath("$.lessonsLearnedScore").value(9))
            .andExpect(jsonPath("$.wordCount").value(120));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void analyzeAbstract_whenAiReturnsEmpty_returns503() throws Exception {
        when(aiService.analyzeAbstract(anyString(), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/speakers/some-speaker-id/ai/analyze-abstract")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"abstract\":\"My speaker abstract\",\"speakerName\":\"Alice\"}"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void generateDescription_withoutAuth_returns401or403() throws Exception {
        mockMvc.perform(post("/api/v1/events/BATbern99/ai/description")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"topicTitle\":\"Cloud Native\",\"topicCategory\":\"DEVOPS\"}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void applyThemeImage_whenEventNotFound_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/events/NON_EXISTENT/ai/theme-image/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imageUrl\":\"https://cdn.batbern.ch/ai-themes/abc.png\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void applyThemeImage_withoutAuth_returns401or403() throws Exception {
        mockMvc.perform(post("/api/v1/events/BATbern99/ai/theme-image/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imageUrl\":\"https://cdn.batbern.ch/ai-themes/abc.png\"}"))
            .andExpect(status().is4xxClientError());
    }
}
