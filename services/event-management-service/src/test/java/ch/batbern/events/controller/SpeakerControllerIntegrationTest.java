package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.domain.SpeakerAvailability;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.SpeakerRepository;
import ch.batbern.shared.types.SpeakerWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SpeakerController - Story 6.0.
 *
 * Tests REST endpoints against PostgreSQL via Testcontainers.
 * Validates ADR-003/ADR-004 compliance: username-based API, HTTP enrichment.
 */
@Transactional
class SpeakerControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpeakerRepository speakerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserApiClient userApiClient;

    private UserResponse mockUserResponse;

    @BeforeEach
    void setUp() {
        speakerRepository.deleteAll();

        // Mock UserApiClient for HTTP enrichment
        mockUserResponse = new UserResponse();
        mockUserResponse.setId("john.doe");
        mockUserResponse.setEmail("john.doe@example.com");
        mockUserResponse.setFirstName("John");
        mockUserResponse.setLastName("Doe");
        mockUserResponse.setBio("Expert architect");
        mockUserResponse.setCompanyId("GoogleZH");

        when(userApiClient.getUserByUsername(anyString())).thenReturn(mockUserResponse);
    }

    // GET /api/v1/speakers - List speakers

    @Test
    void should_listSpeakers_when_anonymousUser() throws Exception {
        // Given
        createTestSpeaker("speaker1.test");
        createTestSpeaker("speaker2.test");

        // When/Then - public endpoint, no auth required
        mockMvc.perform(get("/api/v1/speakers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.pagination.page", is(1)))
                .andExpect(jsonPath("$.pagination.totalItems", is(2)));
    }

    @Test
    void should_filterByAvailability_when_parameterProvided() throws Exception {
        // Given
        Speaker available = createTestSpeaker("available.speaker");
        available.setAvailability(SpeakerAvailability.AVAILABLE);
        speakerRepository.save(available);

        Speaker busy = createTestSpeaker("busy.speaker");
        busy.setAvailability(SpeakerAvailability.BUSY);
        speakerRepository.save(busy);

        // When/Then
        mockMvc.perform(get("/api/v1/speakers")
                        .param("availability", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].availability", is("AVAILABLE")));
    }

    @Test
    void should_paginateResults_when_pageSizeProvided() throws Exception {
        // Given - create 15 speakers
        for (int i = 1; i <= 15; i++) {
            createTestSpeaker("speaker" + String.format("%02d", i) + ".test");
        }

        // When/Then - request page 1, size 5
        mockMvc.perform(get("/api/v1/speakers")
                        .param("page", "1")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)))
                .andExpect(jsonPath("$.pagination.totalItems", is(15)))
                .andExpect(jsonPath("$.pagination.totalPages", is(3)));
    }

    // GET /api/v1/speakers/{username} - Get speaker

    @Test
    void should_getSpeaker_when_usernameExists() throws Exception {
        // Given
        createTestSpeaker("john.doe");

        // When/Then - ADR-003: username as identifier
        mockMvc.perform(get("/api/v1/speakers/john.doe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("john.doe")))
                .andExpect(jsonPath("$.email", is("john.doe@example.com")))
                .andExpect(jsonPath("$.firstName", is("John")))
                .andExpect(jsonPath("$.company", is("GoogleZH")));
    }

    @Test
    void should_return404_when_speakerNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/speakers/nonexistent.user"))
                .andExpect(status().isNotFound());
    }

    // POST /api/v1/speakers - Create speaker

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_createSpeaker_when_organizerRole() throws Exception {
        // Given
        String requestBody = """
            {
                "username": "new.speaker",
                "availability": "AVAILABLE",
                "expertiseAreas": ["Security", "Cloud"],
                "speakingTopics": ["AWS", "Azure"],
                "languages": ["de", "en"]
            }
            """;

        // When/Then
        mockMvc.perform(post("/api/v1/speakers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("john.doe"))) // from mock
                .andExpect(jsonPath("$.availability", is("AVAILABLE")));
    }

    @Test
    void should_return403_when_noAuthForCreate() throws Exception {
        String requestBody = """
            {
                "username": "new.speaker"
            }
            """;

        mockMvc.perform(post("/api/v1/speakers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return400_when_duplicateUsername() throws Exception {
        // Given
        createTestSpeaker("existing.speaker");

        String requestBody = """
            {
                "username": "existing.speaker"
            }
            """;

        // When/Then
        mockMvc.perform(post("/api/v1/speakers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    // PUT /api/v1/speakers/{username} - Update speaker

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_updateSpeaker_when_organizerRole() throws Exception {
        // Given
        createTestSpeaker("update.speaker");

        String requestBody = """
            {
                "username": "update.speaker",
                "availability": "BUSY",
                "expertiseAreas": ["AI/ML", "Data"]
            }
            """;

        // When/Then
        mockMvc.perform(put("/api/v1/speakers/update.speaker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability", is("BUSY")));
    }

    @Test
    void should_return403_when_noAuthForUpdate() throws Exception {
        createTestSpeaker("protected.speaker");

        String requestBody = """
            {
                "username": "protected.speaker",
                "availability": "BUSY"
            }
            """;

        mockMvc.perform(put("/api/v1/speakers/protected.speaker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    // DELETE /api/v1/speakers/{username} - Soft delete

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_deleteSpeaker_when_organizerRole() throws Exception {
        // Given
        createTestSpeaker("delete.speaker");

        // When/Then
        mockMvc.perform(delete("/api/v1/speakers/delete.speaker"))
                .andExpect(status().isNoContent());

        // Verify soft delete - speaker should not be found
        mockMvc.perform(get("/api/v1/speakers/delete.speaker"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return403_when_noAuthForDelete() throws Exception {
        createTestSpeaker("protected.speaker");

        mockMvc.perform(delete("/api/v1/speakers/protected.speaker"))
                .andExpect(status().isForbidden());
    }

    // GET /api/v1/speakers/{username}/exists - Check existence

    @Test
    void should_return200_when_speakerExists() throws Exception {
        createTestSpeaker("exists.speaker");

        mockMvc.perform(get("/api/v1/speakers/exists.speaker/exists"))
                .andExpect(status().isOk());
    }

    @Test
    void should_return404_when_speakerDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/speakers/nonexistent.speaker/exists"))
                .andExpect(status().isNotFound());
    }

    // Helper method

    private Speaker createTestSpeaker(String username) {
        Speaker speaker = Speaker.builder()
                .username(username)
                .availability(SpeakerAvailability.AVAILABLE)
                .workflowState(SpeakerWorkflowState.IDENTIFIED)
                .expertiseAreas(List.of("General"))
                .speakingTopics(List.of("Tech"))
                .languages(List.of("de", "en"))
                .build();
        return speakerRepository.save(speaker);
    }
}
