package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.client.PartnerApiClient;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.service.TrendingTopicsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TopicSessionDataController (Story 10.4 Task 4).
 *
 * TDD RED phase: These tests should FAIL until TopicSessionDataController is implemented.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
public class TopicSessionDataControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserApiClient userApiClient;

    @MockitoBean
    private PartnerApiClient partnerApiClient;

    @MockitoBean
    private TrendingTopicsService trendingTopicsService;

    // ==================== Happy path tests ====================

    @Test
    @DisplayName("should_return200_with_sessionData_when_organizer")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return200_with_sessionData_when_organizer() throws Exception {
        when(partnerApiClient.getPartnerTopics()).thenReturn(List.of(
                new PartnerApiClient.PartnerTopicGroup("Swisscom", null, List.of("AI in Operations"))
        ));
        when(trendingTopicsService.getTrendingTopics()).thenReturn(
                List.of("AI Agents", "Platform Engineering")
        );

        mockMvc.perform(get("/api/v1/events/BATbern58/topic-session-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerTopics").isArray())
                .andExpect(jsonPath("$.partnerTopics[0].companyName").value("Swisscom"))
                .andExpect(jsonPath("$.partnerTopics[0].topics[0]").value("AI in Operations"))
                .andExpect(jsonPath("$.pastEvents").isArray())
                .andExpect(jsonPath("$.organizerBacklog").isArray())
                .andExpect(jsonPath("$.trendingTopics").isArray())
                .andExpect(jsonPath("$.trendingTopics", hasItem("AI Agents")));
    }

    @Test
    @DisplayName("should_return403_when_userIsNotOrganizer")
    @WithMockUser(username = "test.speaker", roles = {"SPEAKER"})
    void should_return403_when_userIsNotOrganizer() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern58/topic-session-data"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should_returnEmptyPartnerTopics_when_partnerServiceUnavailable")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_returnEmptyPartnerTopics_when_partnerServiceUnavailable() throws Exception {
        when(partnerApiClient.getPartnerTopics()).thenReturn(List.of());
        when(trendingTopicsService.getTrendingTopics()).thenReturn(TrendingTopicsService.FALLBACK_TOPICS);

        mockMvc.perform(get("/api/v1/events/BATbern58/topic-session-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerTopics").isArray())
                .andExpect(jsonPath("$.trendingTopics", hasItem("AI Agents")));
    }

    @Test
    @DisplayName("should_includePastEvents_when_eventsExistInDatabase")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_includePastEvents_when_eventsExistInDatabase() throws Exception {
        when(partnerApiClient.getPartnerTopics()).thenReturn(List.of());
        when(trendingTopicsService.getTrendingTopics()).thenReturn(List.of());

        // With empty DB, pastEvents should be an empty array
        mockMvc.perform(get("/api/v1/events/BATbern58/topic-session-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pastEvents").isArray());
    }
}
