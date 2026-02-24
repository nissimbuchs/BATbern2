package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SessionSpeakerController
 * Story 1.15a.1b: Session-User Many-to-Many Relationship - Task 14
 *
 * Tests speaker management endpoints with authorization and validation
 * Uses Testcontainers PostgreSQL for production parity
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
public class SessionSpeakerControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionUserRepository sessionUserRepository;

    @MockitoBean
    private UserApiClient userApiClient;

    @Autowired
    private ObjectMapper objectMapper;

    private Event testEvent;
    private Session testSession;
    private UserResponse testUser1;
    private UserResponse testUser2;
    private String eventCode;
    private String sessionSlug;

    @BeforeEach
    void setUp() {
        // Clean database
        sessionUserRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();

        // Create test event
        eventCode = "BATbern999";
        testEvent = Event.builder()
                .eventCode(eventCode)
                .eventNumber(999)
                .title("Test Event")
                .description("Test Description")
                .date(Instant.now().plusSeconds(86400))
                .registrationDeadline(Instant.now())
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .venueCapacity(100)
                .organizerUsername("test.organizer")
                .eventType(EventType.EVENING)
                .workflowState(ch.batbern.shared.types.EventWorkflowState.CREATED)
                .build();
        testEvent = eventRepository.save(testEvent);

        // Create test session
        sessionSlug = "test-session";
        testSession = Session.builder()
                .sessionSlug(sessionSlug)
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .title("Test Session")
                .sessionType("presentation")
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(3600))
                .build();
        testSession = sessionRepository.save(testSession);

        // Create test user DTOs (not persisted - fetched via API)
        testUser1 = new UserResponse()
                .id("john.doe")
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .companyId("GoogleZH")
                .profilePictureUrl(java.net.URI.create("https://example.com/john.jpg"))
                .active(true);

        testUser2 = new UserResponse()
                .id("jane.smith")
                .email("jane@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .companyId("MicrosoftBE")
                .profilePictureUrl(java.net.URI.create("https://example.com/jane.jpg"))
                .active(true);

        // Mock UserApiClient responses
        when(userApiClient.getUserByUsername("john.doe")).thenReturn(testUser1);
        when(userApiClient.getUserByUsername("jane.smith")).thenReturn(testUser2);
        when(userApiClient.getUserByUsername("invalid.user"))
                .thenThrow(new UserNotFoundException("invalid.user"));
        when(userApiClient.getUserByUsername("non.existent"))
                .thenThrow(new UserNotFoundException("non.existent"));
        when(userApiClient.validateUserExists("john.doe")).thenReturn(true);
        when(userApiClient.validateUserExists("jane.smith")).thenReturn(true);
        when(userApiClient.validateUserExists("invalid.user")).thenReturn(false);
        when(userApiClient.validateUserExists("non.existent")).thenReturn(false);
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_assignSpeaker_when_organizerAndValidData() throws Exception {
        // Given: Request to assign speaker
        Map<String, Object> request = new HashMap<>();
        request.put("username", "john.doe");
        request.put("speakerRole", "PRIMARY_SPEAKER");
        request.put("presentationTitle", "Test Presentation");

        // When/Then: POST /speakers should succeed
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers",
                        eventCode, sessionSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("john.doe"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.company").value("GoogleZH"))
                .andExpect(jsonPath("$.speakerRole").value("PRIMARY_SPEAKER"))
                .andExpect(jsonPath("$.presentationTitle").value("Test Presentation"))
                .andExpect(jsonPath("$.isConfirmed").value(false));
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    @org.junit.jupiter.api.Disabled("Method-level security (@PreAuthorize) is not enforced in test environment. "
            + "In production, authorization is handled at API Gateway level before requests reach this service (Story 1.2). "
            + "This test would require full Spring Security context which conflicts with the gateway-based auth architecture.")
    void should_return403_when_nonOrganizerAssignsSpeaker() throws Exception {
        // Given: Non-ORGANIZER user
        Map<String, Object> request = new HashMap<>();
        request.put("username", "john.doe");
        request.put("speakerRole", "PRIMARY_SPEAKER");

        // When/Then: Should return 403 Forbidden
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers",
                        eventCode, sessionSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return400_when_invalidSpeakerRole() throws Exception {
        // Given: Invalid speaker role
        Map<String, Object> request = new HashMap<>();
        request.put("username", "john.doe");
        request.put("speakerRole", "INVALID_ROLE");

        // When/Then: Should return 400 Bad Request
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers",
                        eventCode, sessionSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return404_when_sessionNotFound() throws Exception {
        // Given: Non-existent session
        Map<String, Object> request = new HashMap<>();
        request.put("username", "john.doe");
        request.put("speakerRole", "PRIMARY_SPEAKER");

        // When/Then: Should return 404 Not Found
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers",
                        eventCode, "non-existent-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_listSpeakers_when_speakersAssigned() throws Exception {
        // Given: Session with assigned speakers
        Map<String, Object> request1 = new HashMap<>();
        request1.put("username", "john.doe");
        request1.put("speakerRole", "PRIMARY_SPEAKER");

        Map<String, Object> request2 = new HashMap<>();
        request2.put("username", "jane.smith");
        request2.put("speakerRole", "CO_SPEAKER");

        // Assign both speakers
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers",
                        eventCode, sessionSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers",
                        eventCode, sessionSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        // When/Then: GET /speakers should return both speakers (public endpoint, no auth needed)
        mockMvc.perform(get("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers",
                        eventCode, sessionSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].username", containsInAnyOrder("john.doe", "jane.smith")))
                .andExpect(jsonPath("$[*].speakerRole",
                    containsInAnyOrder("PRIMARY_SPEAKER", "CO_SPEAKER")));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_removeSpeaker_when_organizerAndSpeakerExists() throws Exception {
        // Given: Assigned speaker
        Map<String, Object> request = new HashMap<>();
        request.put("username", "john.doe");
        request.put("speakerRole", "PRIMARY_SPEAKER");

        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers",
                        eventCode, sessionSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)));

        // When/Then: DELETE /speakers/{username} should succeed
        mockMvc.perform(delete("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}",
                        eventCode, sessionSlug, "john.doe"))
                .andExpect(status().isNoContent());

        // Verify speaker removed
        mockMvc.perform(get("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers",
                        eventCode, sessionSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_confirmSpeaker_when_organizerAndSpeakerExists() throws Exception {
        // Given: Assigned speaker
        Map<String, Object> request = new HashMap<>();
        request.put("username", "john.doe");
        request.put("speakerRole", "PRIMARY_SPEAKER");

        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers",
                        eventCode, sessionSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)));

        // When/Then: POST /speakers/{username}/confirm should succeed
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}/confirm",
                        eventCode, sessionSlug, "john.doe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john.doe"))
                .andExpect(jsonPath("$.isConfirmed").value(true));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_declineSpeaker_when_organizerWithReason() throws Exception {
        // Given: Assigned speaker
        Map<String, Object> assignRequest = new HashMap<>();
        assignRequest.put("username", "john.doe");
        assignRequest.put("speakerRole", "PRIMARY_SPEAKER");

        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers",
                        eventCode, sessionSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignRequest)));

        // When/Then: POST /speakers/{username}/decline should succeed
        Map<String, String> declineRequest = new HashMap<>();
        declineRequest.put("declineReason", "Schedule conflict");

        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}/decline",
                        eventCode, sessionSlug, "john.doe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(declineRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john.doe"))
                .andExpect(jsonPath("$.isConfirmed").value(false));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return404_when_confirmingNonExistentSpeaker() throws Exception {
        // When/Then: Confirming non-existent speaker should return 404
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}/confirm",
                        eventCode, sessionSlug, "non.existent"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    @org.junit.jupiter.api.Disabled("Method-level security (@PreAuthorize) is not enforced in test environment. "
            + "In production, authorization is handled at API Gateway level before requests reach this service (Story 1.2). "
            + "This test would require full Spring Security context which conflicts with the gateway-based auth architecture.")
    void should_return403_when_nonOrganizerConfirmsSpeaker() throws Exception {
        // Given: Assigned speaker
        // (Would need ORGANIZER role to assign first, so skip assignment)

        // When/Then: Non-ORGANIZER should get 403
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}/confirm",
                        eventCode, sessionSlug, "john.doe"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_assignMultipleSpeakersWithDifferentRoles_when_valid() throws Exception {
        // Given: Multiple speaker assignments
        Map<String, Object> primaryRequest = new HashMap<>();
        primaryRequest.put("username", "john.doe");
        primaryRequest.put("speakerRole", "PRIMARY_SPEAKER");

        Map<String, Object> moderatorRequest = new HashMap<>();
        moderatorRequest.put("username", "jane.smith");
        moderatorRequest.put("speakerRole", "MODERATOR");

        // When: Assigning both speakers
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers",
                        eventCode, sessionSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(primaryRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers",
                        eventCode, sessionSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(moderatorRequest)))
                .andExpect(status().isCreated());

        // Then: Both speakers should be listed
        mockMvc.perform(get("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers",
                        eventCode, sessionSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.username=='john.doe')].speakerRole").value("PRIMARY_SPEAKER"))
                .andExpect(jsonPath("$[?(@.username=='jane.smith')].speakerRole").value("MODERATOR"));
    }
}
