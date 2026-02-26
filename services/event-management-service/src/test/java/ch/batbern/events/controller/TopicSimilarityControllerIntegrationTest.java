package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TopicSimilarityController (Story 10.4 Task 3).
 *
 * TDD RED phase: These tests should FAIL until TopicSimilarityController is implemented.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
public class TopicSimilarityControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserApiClient userApiClient;

    // ==================== Happy path tests ====================

    @Test
    @DisplayName("should_returnAiMlCluster_when_topicIsAiRelated")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_returnAiMlCluster_when_topicIsAiRelated() throws Exception {
        String body = """
                { "topic": "AI Agents in Enterprise" }
                """;

        mockMvc.perform(post("/api/v1/events/BATbern58/topic-similarity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cluster").value("AI_ML"))
                .andExpect(jsonPath("$.similarityScore").value(0.85))
                .andExpect(jsonPath("$.relatedPastEventNumbers").isArray())
                .andExpect(jsonPath("$.relatedPastEventNumbers", hasItem(40)))
                .andExpect(jsonPath("$.relatedPastEventNumbers", hasItem(44)))
                .andExpect(jsonPath("$.relatedPastEventNumbers", hasItem(49)))
                .andExpect(jsonPath("$.relatedPastEventNumbers", hasItem(56)))
                .andExpect(jsonPath("$.relatedPastEventNumbers", hasItem(58)));
    }

    @Test
    @DisplayName("should_returnSecurityCluster_when_topicIsSecurityRelated")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_returnSecurityCluster_when_topicIsSecurityRelated() throws Exception {
        String body = """
                { "topic": "Zero Trust Architecture" }
                """;

        mockMvc.perform(post("/api/v1/events/BATbern57/topic-similarity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cluster").value("SECURITY"))
                .andExpect(jsonPath("$.similarityScore").value(0.85))
                .andExpect(jsonPath("$.relatedPastEventNumbers", hasItem(57)))
                .andExpect(jsonPath("$.relatedPastEventNumbers", hasItem(16)));
    }

    @Test
    @DisplayName("should_returnBusinessOther_when_topicIsUnknown")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_returnBusinessOther_when_topicIsUnknown() throws Exception {
        String body = """
                { "topic": "Workplace Psychology and Team Dynamics" }
                """;

        mockMvc.perform(post("/api/v1/events/BATbern58/topic-similarity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cluster").value("BUSINESS_OTHER"))
                .andExpect(jsonPath("$.similarityScore").value(0.50))
                .andExpect(jsonPath("$.relatedPastEventNumbers").isArray())
                .andExpect(jsonPath("$.relatedPastEventNumbers", hasSize(0)));
    }

    @Test
    @DisplayName("should_return400_when_topicIsBlank")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return400_when_topicIsBlank() throws Exception {
        String body = """
                { "topic": "" }
                """;

        mockMvc.perform(post("/api/v1/events/BATbern58/topic-similarity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return403_when_userIsNotOrganizer")
    @WithMockUser(username = "test.speaker", roles = {"SPEAKER"})
    void should_return403_when_userIsNotOrganizer() throws Exception {
        String body = """
                { "topic": "Kubernetes" }
                """;

        mockMvc.perform(post("/api/v1/events/BATbern58/topic-similarity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should_returnCloudInfraCluster_when_topicIsKubernetes")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_returnCloudInfraCluster_when_topicIsKubernetes() throws Exception {
        String body = """
                { "topic": "Kubernetes Platform Engineering" }
                """;

        mockMvc.perform(post("/api/v1/events/BATbern58/topic-similarity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cluster").value("CLOUD_INFRA"))
                .andExpect(jsonPath("$.relatedPastEventNumbers", hasItem(54)))
                .andExpect(jsonPath("$.relatedPastEventNumbers", hasItem(51)));
    }
}
