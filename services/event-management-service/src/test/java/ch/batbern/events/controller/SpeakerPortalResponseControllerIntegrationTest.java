package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerInvitationToken;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerInvitationTokenRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.MagicLinkService;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SpeakerPortalResponseController
 * Story 6.2a: Invitation Response Portal - Task 3 (RED Phase)
 *
 * RED PHASE (TDD): These tests will FAIL until SpeakerPortalResponseController is implemented.
 *
 * Tests REST endpoint: POST /api/v1/speaker-portal/respond
 * Uses real PostgreSQL (Testcontainers) to test full response flow.
 *
 * NOTE: This is a PUBLIC endpoint - no authentication required.
 * The token itself IS the authentication mechanism.
 */
@Transactional
class SpeakerPortalResponseControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MagicLinkService magicLinkService;

    @Autowired
    private SpeakerInvitationTokenRepository tokenRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserApiClient userApiClient;

    private UUID testSpeakerPoolId;
    private UUID testEventId;
    private Event testEvent;
    private SpeakerPool testSpeakerPool;
    private String validToken;

    @BeforeEach
    void setUp() {
        // Clean up in correct order (FK constraints)
        tokenRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        eventRepository.deleteAll();

        // Mock UserApiClient for accept response flow
        GetOrCreateUserResponse userResponse = new GetOrCreateUserResponse();
        userResponse.setUsername("jane.speaker");
        userResponse.setCreated(false);
        when(userApiClient.getOrCreateUser(any())).thenReturn(userResponse);

        // Create test event
        testEvent = Event.builder()
                .eventCode("bat-bern-2026-spring")
                .eventNumber(42)
                .title("BATbern Spring 2026")
                .date(Instant.now().plus(30, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(20, ChronoUnit.DAYS))
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
        testEventId = testEvent.getId();

        // Create test speaker pool entry with INVITED status
        testSpeakerPool = SpeakerPool.builder()
                .eventId(testEventId)
                .speakerName("Jane Speaker")
                .company("Tech Corp AG")
                .expertise("Cloud Architecture")
                .email("jane@techcorp.ch")
                .status(SpeakerWorkflowState.INVITED)
                .username("jane.speaker")
                .invitedAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .responseDeadline(LocalDate.now().plusDays(10))
                .contentDeadline(LocalDate.now().plusDays(30))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testSpeakerPool = speakerPoolRepository.save(testSpeakerPool);
        testSpeakerPoolId = testSpeakerPool.getId();

        // Generate a valid token for testing
        validToken = magicLinkService.generateToken(testSpeakerPoolId, TokenAction.RESPOND);
    }

    // ==================== AC3: Accept Response Flow Tests ====================

    @Nested
    @DisplayName("AC3: Accept Response Flow")
    class AcceptResponseTests {

        /**
         * Test 1.1: Should return 200 when Accept response submitted
         * AC3: Success response for Accept
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return200_when_acceptResponseSubmitted() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "ACCEPT"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.speakerName", is("Jane Speaker")))
                    .andExpect(jsonPath("$.eventName", is("BATbern Spring 2026")))
                    .andExpect(jsonPath("$.nextSteps", not(empty())));
        }

        /**
         * Test 1.2: Should return 200 when Accept with preferences submitted
         * AC3: Success response with preferences
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return200_when_acceptWithPreferencesSubmitted() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "ACCEPT",
                                    "preferences": {
                                        "timeSlot": "Morning preferred",
                                        "travelRequirements": "Need hotel accommodation",
                                        "technicalRequirements": ["Presenter adapter", "Remote clicker"],
                                        "initialTitle": "Cloud-Native Architecture Patterns",
                                        "comments": "Looking forward to it!"
                                    }
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.speakerName", is("Jane Speaker")));
        }

        /**
         * Test 1.3: Should transition speaker to ACCEPTED state
         * AC3: workflow_state transitions to ACCEPTED
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_transitionToAccepted_when_acceptResponseSubmitted() throws Exception {
            // When - Submit accept response
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "ACCEPT"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk());

            // Then - Verify state transition in database
            SpeakerPool updated = speakerPoolRepository.findById(testSpeakerPoolId).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(updated.getStatus())
                    .isEqualTo(SpeakerWorkflowState.ACCEPTED);
            org.assertj.core.api.Assertions.assertThat(updated.getAcceptedAt())
                    .isNotNull();
        }
    }

    // ==================== AC4: Decline Response Flow Tests ====================

    @Nested
    @DisplayName("AC4: Decline Response Flow")
    class DeclineResponseTests {

        /**
         * Test 2.1: Should return 200 when Decline response with reason submitted
         * AC4: Success response for Decline
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return200_when_declineWithReasonSubmitted() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "DECLINE",
                                    "reason": "Schedule conflict with another conference"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));
        }

        /**
         * Test 2.2: Should return 400 when Decline without reason
         * AC4: Reason is required for decline
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return400_when_declineWithoutReason() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "DECLINE"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Test 2.3: Should return 400 when Decline with blank reason
         * AC4: Reason cannot be blank
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return400_when_declineWithBlankReason() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "DECLINE",
                                    "reason": "   "
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Test 2.4: Should transition speaker to DECLINED state
         * AC4: workflow_state transitions to DECLINED (terminal)
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_transitionToDeclined_when_declineResponseSubmitted() throws Exception {
            // When - Submit decline response
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "DECLINE",
                                    "reason": "Prior commitment"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk());

            // Then - Verify state transition in database
            SpeakerPool updated = speakerPoolRepository.findById(testSpeakerPoolId).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(updated.getStatus())
                    .isEqualTo(SpeakerWorkflowState.DECLINED);
            org.assertj.core.api.Assertions.assertThat(updated.getDeclinedAt())
                    .isNotNull();
            org.assertj.core.api.Assertions.assertThat(updated.getDeclineReason())
                    .isEqualTo("Prior commitment");
        }
    }

    // ==================== AC5: Tentative Response Flow Tests ====================

    @Nested
    @DisplayName("AC5: Tentative Response Flow")
    class TentativeResponseTests {

        /**
         * Test 3.1: Should return 200 when Tentative response with reason submitted
         * AC5: Success response for Tentative
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return200_when_tentativeWithReasonSubmitted() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "TENTATIVE",
                                    "reason": "Awaiting budget approval from manager"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));
        }

        /**
         * Test 3.2: Should return 400 when Tentative without reason
         * AC5: Reason is required for tentative
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return400_when_tentativeWithoutReason() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "TENTATIVE"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Test 3.3: Should keep INVITED state with tentative flag
         * AC5: workflow_state stays INVITED, is_tentative set to true
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_setTentativeFlag_when_tentativeResponseSubmitted() throws Exception {
            // When - Submit tentative response
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "TENTATIVE",
                                    "reason": "Checking calendar"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk());

            // Then - Verify state in database
            SpeakerPool updated = speakerPoolRepository.findById(testSpeakerPoolId).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(updated.getStatus())
                    .isEqualTo(SpeakerWorkflowState.INVITED); // Still INVITED
            org.assertj.core.api.Assertions.assertThat(updated.getIsTentative())
                    .isTrue();
            org.assertj.core.api.Assertions.assertThat(updated.getTentativeReason())
                    .isEqualTo("Checking calendar");
        }

        /**
         * Test 3.4: Should NOT consume token for tentative response
         * AC5: Token is NOT consumed (speaker can return)
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_notConsumeToken_when_tentativeResponse() throws Exception {
            // When - Submit tentative response
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "TENTATIVE",
                                    "reason": "Need to check with team"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk());

            // Then - Token should still be valid (can use again)
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "ACCEPT"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk()); // Should still work
        }
    }

    // ==================== Token Validation Error Tests (401) ====================

    @Nested
    @DisplayName("Token Validation Errors (401)")
    class TokenValidationErrorTests {

        /**
         * Test 4.1: Should return 401 when token not found
         * AC1: Invalid token shows error
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return401_when_tokenNotFound() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "completely-invalid-token",
                                    "response": "ACCEPT"
                                }
                                """))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Test 4.2: Should return 401 when token expired
         * AC1: Expired token shows error
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return401_when_tokenExpired() throws Exception {
            // Given - Create an expired token
            String expiredTokenPlaintext = "expired-response-token-test-12345";
            String tokenHash = sha256(expiredTokenPlaintext);

            SpeakerInvitationToken expiredToken = SpeakerInvitationToken.builder()
                    .speakerPoolId(testSpeakerPoolId)
                    .tokenHash(tokenHash)
                    .action(TokenAction.RESPOND)
                    .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)) // Expired
                    .createdAt(Instant.now().minus(31, ChronoUnit.DAYS))
                    .build();
            tokenRepository.save(expiredToken);

            // When/Then
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "ACCEPT"
                                }
                                """.formatted(expiredTokenPlaintext)))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Test 4.3: Should return 401 when token already used
         * AC1: Used token shows error
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return401_when_tokenAlreadyUsed() throws Exception {
            // Given - Create a used token
            String usedTokenPlaintext = "used-response-token-test-67890";
            String tokenHash = sha256(usedTokenPlaintext);

            SpeakerInvitationToken usedToken = SpeakerInvitationToken.builder()
                    .speakerPoolId(testSpeakerPoolId)
                    .tokenHash(tokenHash)
                    .action(TokenAction.RESPOND)
                    .expiresAt(Instant.now().plus(10, ChronoUnit.DAYS))
                    .usedAt(Instant.now().minus(1, ChronoUnit.HOURS)) // Already used
                    .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .build();
            tokenRepository.save(usedToken);

            // When/Then
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "ACCEPT"
                                }
                                """.formatted(usedTokenPlaintext)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== Already Responded Tests (409) ====================

    @Nested
    @DisplayName("AC7: Already Responded (409)")
    class AlreadyRespondedTests {

        /**
         * Test 5.1: Should return 409 when speaker already accepted
         * AC7: Already responded speakers get conflict error
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return409_when_speakerAlreadyAccepted() throws Exception {
            // Given - Speaker has already accepted
            testSpeakerPool.setStatus(SpeakerWorkflowState.ACCEPTED);
            testSpeakerPool.setAcceptedAt(Instant.now().minus(1, ChronoUnit.DAYS));
            speakerPoolRepository.save(testSpeakerPool);

            // Generate a new token for this speaker
            String newToken = magicLinkService.generateToken(testSpeakerPoolId, TokenAction.RESPOND);

            // When/Then - Try to respond again
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "DECLINE",
                                    "reason": "Changed my mind"
                                }
                                """.formatted(newToken)))
                    .andExpect(status().isConflict());
        }

        /**
         * Test 5.2: Should return 409 when speaker already declined
         * AC7: Already responded speakers get conflict error
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return409_when_speakerAlreadyDeclined() throws Exception {
            // Given - Speaker has already declined
            testSpeakerPool.setStatus(SpeakerWorkflowState.DECLINED);
            testSpeakerPool.setDeclinedAt(Instant.now().minus(1, ChronoUnit.DAYS));
            testSpeakerPool.setDeclineReason("Previous decline");
            speakerPoolRepository.save(testSpeakerPool);

            // Generate a new token for this speaker
            String newToken = magicLinkService.generateToken(testSpeakerPoolId, TokenAction.RESPOND);

            // When/Then - Try to respond again
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "ACCEPT"
                                }
                                """.formatted(newToken)))
                    .andExpect(status().isConflict());
        }

        /**
         * Test 5.3: Should allow response when currently tentative
         * AC7: Tentative speakers can still respond
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return200_when_speakerCurrentlyTentative() throws Exception {
            // Given - Speaker is tentative
            testSpeakerPool.setIsTentative(true);
            testSpeakerPool.setTentativeReason("Was checking calendar");
            speakerPoolRepository.save(testSpeakerPool);

            // When/Then - Should be able to accept now
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "ACCEPT"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));
        }
    }

    // ==================== Request Validation Tests (400) ====================

    @Nested
    @DisplayName("Request Validation Errors (400)")
    class RequestValidationErrorTests {

        /**
         * Test 6.1: Should return 400 when token missing
         * Bad request handling
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return400_when_tokenMissing() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "response": "ACCEPT"
                                }
                                """))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Test 6.2: Should return 400 when response missing
         * Bad request handling
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return400_when_responseMissing() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Test 6.3: Should return 400 when response invalid
         * Bad request handling
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return400_when_responseInvalid() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "response": "MAYBE"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Test 6.4: Should return 400 when body is empty
         * Bad request handling
         * RED Phase: Will fail - SpeakerPortalResponseController doesn't exist yet
         */
        @Test
        void should_return400_when_bodyEmpty() throws Exception {
            mockMvc.perform(post("/api/v1/speaker-portal/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // Helper method to compute SHA-256 hash (same as MagicLinkService)
    private String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
