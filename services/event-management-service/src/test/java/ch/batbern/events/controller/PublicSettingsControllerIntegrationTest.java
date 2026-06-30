package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the public feature-flags endpoint after its Phase-8 relocation from
 * {@code AiAssistController} to {@link ch.batbern.events.controller.PublicSettingsController}.
 * Path and public (no-auth) behaviour are preserved verbatim.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class PublicSettingsControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void getFeatureFlags_isPublic_returnsAiEnabledFlag() throws Exception {
        mockMvc.perform(get("/api/v1/public/settings/features"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aiContentEnabled").isBoolean());
    }
}
