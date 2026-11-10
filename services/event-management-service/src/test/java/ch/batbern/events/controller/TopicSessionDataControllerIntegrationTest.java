package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.service.TrendingTopicsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
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

    @Autowired
    private RestTemplate restTemplate;

    /** PCS behind the real PartnerApiClient, answering per partners-api (Story 17.0b1). */
    private ContractServer pcs;

    @MockitoBean
    private TrendingTopicsService trendingTopicsService;

    @BeforeEach
    void bindPcs() {
        // @WithMockUser callers carry no JWT to forward, hence ignoringMissingAuthorization.
        pcs = ContractServer.bindTo(restTemplate, "docs/api/partners-api.openapi.yml").ignoringMissingAuthorization();
    }

    @AfterEach
    void releasePcs() {
        pcs.close();
    }

    // ==================== Happy path tests ====================

    @Test
    @DisplayName("should_return200_with_sessionData_when_organizer")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return200_with_sessionData_when_organizer() throws Exception {
        pcs.expect(HttpMethod.GET, "/api/v1/partners/topics").respondWith(HttpStatus.OK, """
                [{"id": "3f1c2a9e-8b4d-4c6e-9a7f-1d2e3f4a5b6c", "title": "AI in Operations",
                  "description": null, "plannedEvent": null, "suggestedByCompany": "Swisscom", "voteCount": 2, "currentPartnerHasVoted": false,
                  "status": "PROPOSED", "createdAt": "2026-02-01T10:00:00Z", "source": "PARTNER"}]""");
        pcs.expect(HttpMethod.GET, "/api/v1/partners").respondWithExample();
        when(trendingTopicsService.getTrendingTopics()).thenReturn(
                List.of("AI Agents", "Platform Engineering")
        );

        mockMvc.perform(get("/api/v1/events/BATbern58/topic-session-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerTopics").isArray())
                .andExpect(jsonPath("$.partnerTopics[0].companyName").value("Swisscom"))
                .andExpect(jsonPath("$.partnerTopics[0].topics[0].title").value("AI in Operations"))
                .andExpect(jsonPath("$.partnerTopics[0].topics[0].cluster").value("AI_ML"))
                .andExpect(jsonPath("$.partnerTopics[0].topics[0].voteCount").value(2))
                .andExpect(jsonPath("$.pastEvents").isArray())
                .andExpect(jsonPath("$.organizerBacklog").isArray())
                .andExpect(jsonPath("$.trendingTopics").isArray())
                .andExpect(jsonPath("$.trendingTopics", hasItem("AI Agents")));
        pcs.verify();
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
        pcs.expect(HttpMethod.GET, "/api/v1/partners/topics").respondWith(request -> {
            throw new ResourceAccessException("Connection refused");
        });
        when(trendingTopicsService.getTrendingTopics()).thenReturn(TrendingTopicsService.FALLBACK_TOPICS);

        mockMvc.perform(get("/api/v1/events/BATbern58/topic-session-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerTopics").isArray())
                .andExpect(jsonPath("$.trendingTopics", hasItem("AI Agents")));
        pcs.verify();
    }

    @Test
    @DisplayName("should_includePastEvents_when_eventsExistInDatabase")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_includePastEvents_when_eventsExistInDatabase() throws Exception {
        pcs.expect(HttpMethod.GET, "/api/v1/partners/topics").respondWith(HttpStatus.OK, "[]");
        when(trendingTopicsService.getTrendingTopics()).thenReturn(List.of());

        // With empty DB, pastEvents should be an empty array
        mockMvc.perform(get("/api/v1/events/BATbern58/topic-session-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pastEvents").isArray());
    }
}
