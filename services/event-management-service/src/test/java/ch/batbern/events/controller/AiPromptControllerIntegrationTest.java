package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.AiPrompt;
import ch.batbern.events.repository.AiPromptRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI prompt management (Story 10.x), its first IT (Story 4.10b): the prompt an organizer edits is an
 * override of the default text, and {@code DELETE /ai-prompts/{promptKey}/override} removes it
 * (replaces {@code POST …/reset}). The prompts are seeded by V82; each test restores the text it
 * found, so no class-level {@code @Transactional} (Test fidelity rules).
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("AI prompts (Story 4.10b: DELETE …/override)")
class AiPromptControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String KEY = "event_description";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiPromptRepository aiPromptRepository;

    private String originalText;

    @BeforeEach
    void captureOriginal() {
        originalText = aiPromptRepository.findById(KEY).orElseThrow().getPromptText();
    }

    @AfterEach
    void restoreOriginal() {
        AiPrompt prompt = aiPromptRepository.findById(KEY).orElseThrow();
        prompt.setPromptText(originalText);
        aiPromptRepository.save(prompt);
    }

    @Test
    @DisplayName("should_restoreDefaultText_when_organizerDeletesOverride")
    void should_restoreDefaultText_when_organizerDeletesOverride() throws Exception {
        String defaultText = aiPromptRepository.findById(KEY).orElseThrow().getDefaultText();

        mockMvc.perform(put("/api/v1/ai-prompts/{key}", KEY)
                        .with(user("anna.organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptText\":\"Schreibe über Brünnimann’s Vortrag\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promptText").value("Schreibe über Brünnimann’s Vortrag"));
        // Positive control: the override is stored and differs from the default.
        assertThat(aiPromptRepository.findById(KEY).orElseThrow().getPromptText()).isNotEqualTo(defaultText);

        mockMvc.perform(delete("/api/v1/ai-prompts/{key}/override", KEY)
                        .with(user("anna.organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promptKey").value(KEY))
                .andExpect(jsonPath("$.promptText").value(defaultText))
                .andExpect(jsonPath("$.defaultText").value(defaultText));

        assertThat(aiPromptRepository.findById(KEY).orElseThrow().getPromptText()).isEqualTo(defaultText);

        // A read after the delete shows the default too.
        mockMvc.perform(get("/api/v1/ai-prompts/{key}", KEY).with(user("anna.organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promptText").value(defaultText));
    }

    @Test
    @DisplayName("should_return404_when_promptKeyIsUnknown")
    void should_return404_when_promptKeyIsUnknown() throws Exception {
        mockMvc.perform(delete("/api/v1/ai-prompts/{key}/override", "no_such_prompt")
                        .with(user("anna.organizer").roles("ORGANIZER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_return403AndKeepOverride_when_speakerDeletesOverride")
    void should_return403AndKeepOverride_when_speakerDeletesOverride() throws Exception {
        AiPrompt prompt = aiPromptRepository.findById(KEY).orElseThrow();
        prompt.setPromptText("Override von O’Neill");
        aiPromptRepository.save(prompt);

        mockMvc.perform(delete("/api/v1/ai-prompts/{key}/override", KEY)
                        .with(user("hongan.nguyen").roles("SPEAKER")))
                .andExpect(status().isForbidden());

        assertThat(aiPromptRepository.findById(KEY).orElseThrow().getPromptText()).isEqualTo("Override von O’Neill");
    }

    @Test
    @DisplayName("should_return401_when_anonymousDeletesOverride")
    void should_return401_when_anonymousDeletesOverride() throws Exception {
        mockMvc.perform(delete("/api/v1/ai-prompts/{key}/override", KEY)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should_answerNoEndpoint_when_oldResetPathIsCalled")
    void should_answerNoEndpoint_when_oldResetPathIsCalled() throws Exception {
        // Story 4.10b, no alias: the verb path has no handler any more.
        mockMvc.perform(post("/api/v1/ai-prompts/{key}/reset", KEY).with(user("anna.organizer").roles("ORGANIZER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(startsWith("No endpoint POST ")));
    }
}
