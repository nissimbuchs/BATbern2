package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerInvitationTokenRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.types.EventWorkflowState;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SpeakerInvitationController
 * Story 6.1b: Speaker Invitation System
 *
 * Tests REST endpoints:
 * - POST /api/v1/events/{eventCode}/speakers/invite
 * - POST /api/v1/events/{eventCode}/speakers/invite-batch
 * - POST /api/v1/events/{eventCode}/speakers/{username}/send-invitation
 *
 * Uses real PostgreSQL (Testcontainers) but mocks UserApiClient and EmailService.
 */
@Transactional
class SpeakerInvitationControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private SpeakerInvitationTokenRepository tokenRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserApiClient userApiClient;

    @MockBean
    private EmailService emailService;

    private Event testEvent;
    private final String testEventCode = "batbern-2026-spring";
    private final String testEmail = "speaker@example.com";
    private final String testUsername = "speaker.test";

    @BeforeEach
    void setUp() {
        // Clean up in correct order (FK constraints)
        tokenRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        eventRepository.deleteAll();

        // Create test event
        testEvent = Event.builder()
                .eventCode(testEventCode)
                .eventNumber(56)
                .title("BATbern Spring 2026")
                .date(Instant.now().plus(60, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(50, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .eventType(ch.batbern.events.dto.generated.EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .organizerUsername("organizer.test")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testEvent = eventRepository.save(testEvent);

        // Mock UserApiClient to return a valid response
        GetOrCreateUserResponse userResponse = new GetOrCreateUserResponse();
        userResponse.setUsername(testUsername);
        userResponse.setCreated(true);
        when(userApiClient.getOrCreateUser(any(GetOrCreateUserRequest.class)))
                .thenReturn(userResponse);

        // Mock EmailService (don't actually send emails)
        doNothing().when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    // ==================== AC1: Single Invitation Tests ====================

    /**
     * Test 1.1: Should create speaker pool entry when valid request
     * AC1: POST /events/{eventCode}/speakers/invite creates SpeakerPool
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_createSpeakerPool_when_validInviteRequest() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/invite", testEventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "email": "%s",
                                "firstName": "John",
                                "lastName": "Speaker",
                                "company": "Swiss Tech AG"
                            }
                            """.formatted(testEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.speakerPoolId").exists())
                .andExpect(jsonPath("$.username", is(testUsername)))
                .andExpect(jsonPath("$.email", is(testEmail)))
                .andExpect(jsonPath("$.speakerName", is("John Speaker")))
                .andExpect(jsonPath("$.status", is("IDENTIFIED")))
                .andExpect(jsonPath("$.created", is(true)))
                .andExpect(jsonPath("$.userCreated", is(true)));

        // Verify UserApiClient was called
        verify(userApiClient).getOrCreateUser(any(GetOrCreateUserRequest.class));
    }

    /**
     * Test 1.2: Should return 200 for existing speaker (idempotency)
     * AC7: Returns existing entry if speaker already invited
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_return200_when_speakerAlreadyInvited() throws Exception {
        // Given - Create existing speaker pool entry
        SpeakerPool existingSpeaker = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .username(testUsername)
                .email(testEmail)
                .speakerName("Existing Speaker")
                .status(SpeakerWorkflowState.CONTACTED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        speakerPoolRepository.save(existingSpeaker);

        // When/Then - Invite same email again
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/invite", testEventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "email": "%s",
                                "firstName": "John",
                                "lastName": "Speaker"
                            }
                            """.formatted(testEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speakerName", is("Existing Speaker")))
                .andExpect(jsonPath("$.status", is("CONTACTED")))
                .andExpect(jsonPath("$.created", is(false)));

        // Verify UserApiClient was NOT called (idempotency)
        verify(userApiClient, never()).getOrCreateUser(any(GetOrCreateUserRequest.class));
    }

    /**
     * Test 1.3: Should return 404 when event not found
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_return404_when_eventNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/invite", "non-existent-event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "email": "%s"
                            }
                            """.formatted(testEmail)))
                .andExpect(status().isNotFound());
    }

    /**
     * Test 1.4: Should return 400 when email missing
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_return400_when_emailMissing() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/invite", testEventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "firstName": "John",
                                "lastName": "Speaker"
                            }
                            """))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test 1.5: Should return 400 when email invalid
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_return400_when_emailInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/invite", testEventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "email": "not-a-valid-email"
                            }
                            """))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test 1.6: Should return 403 when not organizer role
     */
    @Test
    @WithMockUser(username = "speaker.test", roles = {"SPEAKER"})
    void should_return403_when_notOrganizer() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/invite", testEventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "email": "%s"
                            }
                            """.formatted(testEmail)))
                .andExpect(status().isForbidden());
    }

    // ==================== AC3: Send Invitation Tests ====================

    /**
     * Test 3.1: Should send invitation and update status to INVITED
     * AC3: Sends personalized email with magic links
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_sendInvitation_when_validRequest() throws Exception {
        // Given - Create speaker pool entry
        SpeakerPool speaker = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .username(testUsername)
                .email(testEmail)
                .speakerName("Test Speaker")
                .status(SpeakerWorkflowState.IDENTIFIED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        speakerPoolRepository.save(speaker);

        LocalDate responseDeadline = LocalDate.now().plusDays(14);

        // When/Then
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{username}/send-invitation",
                        testEventCode, testUsername)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "responseDeadline": "%s",
                                "locale": "de"
                            }
                            """.formatted(responseDeadline)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INVITED")))
                .andExpect(jsonPath("$.invitedAt").exists())
                .andExpect(jsonPath("$.responseDeadline", is(responseDeadline.toString())));

        // Verify token was created
        org.assertj.core.api.Assertions.assertThat(
                tokenRepository.findBySpeakerPoolId(speaker.getId())
        ).isNotEmpty();
    }

    /**
     * Test 3.2: Should return 404 when speaker not found
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_return404_when_speakerNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{username}/send-invitation",
                        testEventCode, "non-existent-speaker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "responseDeadline": "%s"
                            }
                            """.formatted(LocalDate.now().plusDays(14))))
                .andExpect(status().isNotFound());
    }

    /**
     * Test 3.3: Should return 400 when response deadline in past
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_return400_when_responseDeadlineInPast() throws Exception {
        // Given - Create speaker pool entry
        SpeakerPool speaker = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .username(testUsername)
                .email(testEmail)
                .speakerName("Test Speaker")
                .status(SpeakerWorkflowState.IDENTIFIED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        speakerPoolRepository.save(speaker);

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{username}/send-invitation",
                        testEventCode, testUsername)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "responseDeadline": "%s"
                            }
                            """.formatted(LocalDate.now().minusDays(1))))
                .andExpect(status().isBadRequest());
    }

    // ==================== AC5: Batch Invitation Tests ====================

    /**
     * Test 5.1: Should process batch of invitations
     * AC5: Handles multiple invitations
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_processBatch_when_validRequest() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/invite-batch", testEventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "speakers": [
                                    {
                                        "email": "speaker1@example.com",
                                        "firstName": "Speaker",
                                        "lastName": "One"
                                    },
                                    {
                                        "email": "speaker2@example.com",
                                        "firstName": "Speaker",
                                        "lastName": "Two"
                                    }
                                ]
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequested", is(2)))
                .andExpect(jsonPath("$.successCount", is(2)))
                .andExpect(jsonPath("$.failedCount", is(0)))
                .andExpect(jsonPath("$.results", hasSize(2)));
    }

    /**
     * Test 5.2: Should return 400 when empty speakers list
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_return400_when_emptySpeakersList() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/invite-batch", testEventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "speakers": []
                            }
                            """))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test 5.3: Should handle partial failures in batch
     * AC5: Partial failure support
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_handlePartialFailure_when_someSpeakersInvalid() throws Exception {
        // Mock UserApiClient to fail for second speaker
        GetOrCreateUserResponse validResponse = new GetOrCreateUserResponse();
        validResponse.setUsername("speaker.one");
        validResponse.setCreated(true);

        when(userApiClient.getOrCreateUser(any(GetOrCreateUserRequest.class)))
                .thenAnswer(invocation -> {
                    GetOrCreateUserRequest req = invocation.getArgument(0);
                    if (req.getEmail().contains("fail")) {
                        throw new RuntimeException("User service error");
                    }
                    return validResponse;
                });

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/invite-batch", testEventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "speakers": [
                                    {
                                        "email": "valid@example.com",
                                        "firstName": "Valid",
                                        "lastName": "Speaker"
                                    },
                                    {
                                        "email": "fail@example.com",
                                        "firstName": "Will",
                                        "lastName": "Fail"
                                    }
                                ]
                            }
                            """))
                .andExpect(status().is(207)) // Multi-Status
                .andExpect(jsonPath("$.totalRequested", is(2)))
                .andExpect(jsonPath("$.successCount", is(1)))
                .andExpect(jsonPath("$.failedCount", is(1)))
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].email", is("fail@example.com")));
    }

    // ==================== AC2: User Auto-Creation Tests ====================

    /**
     * Test 2.1: Should call UserApiClient with correct parameters
     * AC2: Auto-creates User via UserApiClient
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_callUserApiClient_when_invitingNewSpeaker() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/invite", testEventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "email": "new.speaker@example.com",
                                "firstName": "New",
                                "lastName": "Speaker",
                                "company": "Test Company"
                            }
                            """))
                .andExpect(status().isCreated());

        // Verify correct parameters passed to UserApiClient
        verify(userApiClient).getOrCreateUser(argThat(request ->
                request.getEmail().equals("new.speaker@example.com")
                && request.getFirstName().equals("New")
                && request.getLastName().equals("Speaker")
                && request.getCompanyId().equals("Test Company")
                && !request.getCognitoSync() // Should be false for speakers
        ));
    }
}
