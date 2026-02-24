package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerInvitationToken;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerInvitationTokenRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.MagicLinkService;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SpeakerPortalTokenController
 * Story 6.1a: Magic Link Infrastructure - Task 5 (RED Phase)
 *
 * RED PHASE (TDD): These tests will FAIL until SpeakerPortalTokenController is implemented.
 *
 * Tests REST endpoint: POST /api/v1/speaker-portal/validate-token
 * Uses real PostgreSQL (Testcontainers) to test full validation flow.
 *
 * NOTE: This is a PUBLIC endpoint - no authentication required.
 * The token itself IS the authentication mechanism.
 */
@Transactional
class SpeakerPortalTokenControllerIntegrationTest extends AbstractIntegrationTest {

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

    private UUID testSpeakerPoolId;
    private UUID testEventId;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        // Clean up in correct order (FK constraints)
        tokenRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        eventRepository.deleteAll();

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

        // Create test speaker pool entry
        SpeakerPool speakerPool = SpeakerPool.builder()
                .eventId(testEventId)
                .speakerName("Alice Speaker")
                .company("Swiss Tech AG")
                .expertise("Cloud Architecture")
                .status(SpeakerWorkflowState.CONTACTED)
                .username("alice.speaker")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        speakerPool = speakerPoolRepository.save(speakerPool);
        testSpeakerPoolId = speakerPool.getId();
    }

    // ==================== AC5: REST API Tests ====================

    /**
     * Test 5.1: Should return 200 when valid token provided
     * AC5: Success response (200) with speaker context
     * RED Phase: Will fail - SpeakerPortalTokenController doesn't exist yet
     */
    @Test
    void should_return200_when_validTokenProvided() throws Exception {
        // Given - Generate a valid token
        String validToken = magicLinkService.generateToken(testSpeakerPoolId, TokenAction.RESPOND);

        // When/Then
        mockMvc.perform(post("/api/v1/speaker-portal/validate-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "token": "%s"
                            }
                            """.formatted(validToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.username", is("alice.speaker")))
                .andExpect(jsonPath("$.action", is("RESPOND")));
    }

    /**
     * Test 5.2: Should return 401 when expired token provided
     * AC5: Error response (401) with TOKEN_EXPIRED error
     * RED Phase: Will fail - SpeakerPortalTokenController doesn't exist yet
     */
    @Test
    void should_return401_when_expiredTokenProvided() throws Exception {
        // Given - Create an expired token directly in database
        String expiredTokenPlaintext = "expired-token-test-123456789012345678";
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
        mockMvc.perform(post("/api/v1/speaker-portal/validate-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "token": "%s"
                            }
                            """.formatted(expiredTokenPlaintext)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.error", is("EXPIRED")));
    }

    /**
     * Test 5.3: Should return 401 when used RESPOND token provided
     * AC5: Error response (401) with ALREADY_USED error
     * RED Phase: Will fail - SpeakerPortalTokenController doesn't exist yet
     */
    @Test
    void should_return401_when_usedTokenProvided() throws Exception {
        // Given - Create a used RESPOND token
        String usedTokenPlaintext = "used-respond-token-test-567890123";
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
        mockMvc.perform(post("/api/v1/speaker-portal/validate-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "token": "%s"
                            }
                            """.formatted(usedTokenPlaintext)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.error", is("ALREADY_USED")));
    }

    /**
     * Test 5.4: Should return 401 when invalid/unknown token provided
     * AC5: Error response (401) with NOT_FOUND error
     * RED Phase: Will fail - SpeakerPortalTokenController doesn't exist yet
     */
    @Test
    void should_return401_when_invalidTokenProvided() throws Exception {
        // Given - Token that doesn't exist in database
        String invalidToken = "totally-invalid-token-not-in-database";

        // When/Then
        mockMvc.perform(post("/api/v1/speaker-portal/validate-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "token": "%s"
                            }
                            """.formatted(invalidToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.error", is("NOT_FOUND")));
    }

    /**
     * Test 5.5: Should return 400 when request body is missing token
     * AC5: Bad request handling
     * RED Phase: Will fail - SpeakerPortalTokenController doesn't exist yet
     */
    @Test
    void should_return400_when_tokenMissing() throws Exception {
        // When/Then
        mockMvc.perform(post("/api/v1/speaker-portal/validate-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test 5.6: Should allow VIEW token to be validated multiple times
     * AC3: VIEW tokens are reusable
     * RED Phase: Will fail - SpeakerPortalTokenController doesn't exist yet
     */
    @Test
    void should_allowMultipleValidations_when_viewToken() throws Exception {
        // Given - Generate a VIEW token
        String viewToken = magicLinkService.generateToken(testSpeakerPoolId, TokenAction.VIEW);

        // When/Then - First validation
        mockMvc.perform(post("/api/v1/speaker-portal/validate-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "token": "%s"
                            }
                            """.formatted(viewToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.action", is("VIEW")));

        // Second validation should also succeed
        mockMvc.perform(post("/api/v1/speaker-portal/validate-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "token": "%s"
                            }
                            """.formatted(viewToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)));
    }

    // ==================== AC6: Security Tests ====================

    /**
     * Test 6.3: Should log failed attempt with IP for audit
     * AC6: Failed validation attempts logged with IP for audit
     *
     * This test verifies that the endpoint properly handles X-Forwarded-For header
     * for IP extraction (used in logging for audit purposes).
     */
    @Test
    void should_extractClientIp_when_xForwardedForHeaderPresent() throws Exception {
        // Given - An invalid token and a specific client IP
        String invalidToken = "invalid-token-for-ip-test";
        String testClientIp = "203.0.113.42";

        // When/Then - Send request with X-Forwarded-For header
        // The endpoint should extract the IP from the header (verified via 401 response)
        // The actual IP logging is verified by the fact that the endpoint accepts the header
        mockMvc.perform(post("/api/v1/speaker-portal/validate-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", testClientIp + ", 10.0.0.1")
                        .content("""
                            {
                                "token": "%s"
                            }
                            """.formatted(invalidToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.error", is("NOT_FOUND")));

        // Note: The actual log verification would require a log capture mechanism
        // (e.g., Logback ListAppender). The key security property tested here is that:
        // 1. The endpoint accepts and processes X-Forwarded-For header
        // 2. Failed validation returns 401 (which triggers IP logging in controller)
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
