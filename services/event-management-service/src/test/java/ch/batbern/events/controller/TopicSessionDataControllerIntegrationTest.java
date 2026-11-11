package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TopicSessionDataController (Story 10.4 Task 4).
 *
 * <p>Epic 17, entry 8: EMS no longer calls partner-coordination for the canvas; the partner topics
 * are loaded by the frontend, so the response carries no {@code partnerTopics}. The real
 * {@code TrendingTopicsService} runs: the test properties set no OpenAI key, so it answers
 * {@code FALLBACK_TOPICS} without leaving the process. Read-only, so no transaction.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
public class TopicSessionDataControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("should_return200_with_sessionData_withoutPartnerTopics_when_organizer")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return200_with_sessionData_when_organizer() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern58/topic-session-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerTopics").doesNotExist())
                .andExpect(jsonPath("$.pastEvents").isArray())
                .andExpect(jsonPath("$.organizerBacklog").isArray())
                .andExpect(jsonPath("$.trendingTopics").isArray())
                .andExpect(jsonPath("$.trendingTopics", hasItem("AI Agents")))
                .andExpect(jsonPath("$.trendingTopics", hasItem("Platform Engineering")));
    }

    @Test
    @DisplayName("should_return403_when_userIsNotOrganizer")
    @WithMockUser(username = "test.speaker", roles = {"SPEAKER"})
    void should_return403_when_userIsNotOrganizer() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern58/topic-session-data"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should_return401_when_anonymous")
    void should_return401_when_anonymous() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern58/topic-session-data"))
                .andExpect(status().isUnauthorized());
    }
}
