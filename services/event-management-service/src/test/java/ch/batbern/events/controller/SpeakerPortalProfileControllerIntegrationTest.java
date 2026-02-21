package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.domain.SpeakerAvailability;
import ch.batbern.events.domain.SpeakerInvitationToken;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerInvitationTokenRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerRepository;
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SpeakerPortalProfileController.
 * Story 6.2b: Speaker Profile Update Portal - Task 3 (RED Phase)
 *
 * RED PHASE (TDD): These tests will FAIL until SpeakerPortalProfileController is implemented.
 *
 * Tests REST endpoints:
 * - GET /api/v1/speaker-portal/profile?token=xxx
 * - PATCH /api/v1/speaker-portal/profile
 *
 * Uses real PostgreSQL (Testcontainers) to test full profile flow.
 * Mocks UserApiClient since it calls external Company Service.
 *
 * NOTE: These are PUBLIC endpoints - no authentication required.
 * The magic link token IS the authentication mechanism.
 */
@Transactional
class SpeakerPortalProfileControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MagicLinkService magicLinkService;

    @Autowired
    private SpeakerInvitationTokenRepository tokenRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private SpeakerRepository speakerRepository;

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
    private Speaker testSpeaker;
    private String validToken;
    private UserResponse mockUserResponse;

    private static final String TEST_USERNAME = "jane.speaker";

    @BeforeEach
    void setUp() {
        // Clean up in correct order (FK constraints)
        tokenRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        speakerRepository.deleteAll();
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

        // Create test speaker pool entry (ACCEPTED status - story prerequisite)
        testSpeakerPool = SpeakerPool.builder()
                .eventId(testEventId)
                .speakerName("Jane Speaker")
                .company("Tech Corp AG")
                .expertise("Cloud Architecture")
                .email("jane@techcorp.ch")
                .status(SpeakerWorkflowState.ACCEPTED)
                .username(TEST_USERNAME)
                .invitedAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .acceptedAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .responseDeadline(LocalDate.now().plusDays(10))
                .contentDeadline(LocalDate.now().plusDays(30))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testSpeakerPool = speakerPoolRepository.save(testSpeakerPool);
        testSpeakerPoolId = testSpeakerPool.getId();

        // Create test speaker entity (profile data)
        testSpeaker = Speaker.builder()
                .username(TEST_USERNAME)
                .availability(SpeakerAvailability.AVAILABLE)
                .workflowState(SpeakerWorkflowState.ACCEPTED)
                .expertiseAreas(List.of("Cloud Architecture", "Microservices"))
                .speakingTopics(List.of("AWS", "Kubernetes"))
                .languages(List.of("de", "en"))
                .linkedInUrl("https://linkedin.com/in/janespeaker")
                .build();
        testSpeaker = speakerRepository.save(testSpeaker);

        // Generate a valid token for testing
        validToken = magicLinkService.generateToken(testSpeakerPoolId, TokenAction.VIEW);

        // Mock UserApiClient for HTTP enrichment
        mockUserResponse = new UserResponse();
        mockUserResponse.setId(TEST_USERNAME);
        mockUserResponse.setEmail("jane@techcorp.ch");
        mockUserResponse.setFirstName("Jane");
        mockUserResponse.setLastName("Speaker");
        mockUserResponse.setBio("Expert cloud architect with 10 years experience.");
        mockUserResponse.setProfilePictureUrl(URI.create("https://cdn.batbern.ch/users/jane.speaker/profile.jpg"));

        when(userApiClient.getUserByUsername(anyString())).thenReturn(mockUserResponse);
        when(userApiClient.updateUser(anyString(), any())).thenReturn(mockUserResponse);
    }

    // ==================== AC1: Profile View Tests ====================

    @Nested
    @DisplayName("AC1: Profile View (GET /speaker-portal/profile)")
    class GetProfileTests {

        /**
         * Test 2.1: Should return 200 with combined profile when valid token
         * AC1: Profile view on page load
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_return200WithProfile_when_validToken() throws Exception {
            mockMvc.perform(get("/api/v1/speaker-portal/profile")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    // User fields (from mocked UserApiClient)
                    .andExpect(jsonPath("$.username", is(TEST_USERNAME)))
                    .andExpect(jsonPath("$.email", is("jane@techcorp.ch")))
                    .andExpect(jsonPath("$.firstName", is("Jane")))
                    .andExpect(jsonPath("$.lastName", is("Speaker")))
                    .andExpect(jsonPath("$.bio", is("Expert cloud architect with 10 years experience.")))
                    .andExpect(jsonPath("$.profilePictureUrl", is("https://cdn.batbern.ch/users/jane.speaker/profile.jpg")))
                    // Speaker fields (from database)
                    .andExpect(jsonPath("$.expertiseAreas", hasItems("Cloud Architecture", "Microservices")))
                    .andExpect(jsonPath("$.speakingTopics", hasItems("AWS", "Kubernetes")))
                    .andExpect(jsonPath("$.languages", hasItems("de", "en")))
                    .andExpect(jsonPath("$.linkedInUrl", is("https://linkedin.com/in/janespeaker")))
                    // Computed fields
                    .andExpect(jsonPath("$.profileCompleteness", is(100)))
                    .andExpect(jsonPath("$.missingFields", empty()));
        }

        /**
         * Test 2.2: Should return 401 when token expired
         * AC1: Expired token shows error
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_return401_when_tokenExpired() throws Exception {
            // Given - Create an expired token
            String expiredTokenPlaintext = "expired-profile-token-test-12345";
            String tokenHash = sha256(expiredTokenPlaintext);

            SpeakerInvitationToken expiredToken = SpeakerInvitationToken.builder()
                    .speakerPoolId(testSpeakerPoolId)
                    .tokenHash(tokenHash)
                    .action(TokenAction.VIEW)
                    .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)) // Expired
                    .createdAt(Instant.now().minus(31, ChronoUnit.DAYS))
                    .build();
            tokenRepository.save(expiredToken);

            // When/Then
            mockMvc.perform(get("/api/v1/speaker-portal/profile")
                            .param("token", expiredTokenPlaintext))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Test 2.3: Should return 401 when token not found
         * AC1: Invalid token shows error
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_return401_when_tokenNotFound() throws Exception {
            mockMvc.perform(get("/api/v1/speaker-portal/profile")
                            .param("token", "completely-invalid-token"))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Test 2.4: Should return 400 when token parameter missing
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_return400_when_tokenMissing() throws Exception {
            mockMvc.perform(get("/api/v1/speaker-portal/profile"))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Test 2.5: Should include profile completeness percentage
         * AC8: Profile completeness calculation
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_includeCompletenessPercentage_when_profileIncomplete() throws Exception {
            // Given - Speaker with incomplete profile
            testSpeaker.setExpertiseAreas(null);
            testSpeaker.setLanguages(null);
            speakerRepository.save(testSpeaker);

            // Mock user without bio and photo
            mockUserResponse.setBio(null);
            mockUserResponse.setProfilePictureUrl(null);
            when(userApiClient.getUserByUsername(anyString())).thenReturn(mockUserResponse);

            // When/Then - 30% (firstName 15% + lastName 15%)
            mockMvc.perform(get("/api/v1/speaker-portal/profile")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profileCompleteness", is(30)))
                    .andExpect(jsonPath("$.missingFields", hasItems("bio", "profilePictureUrl", "expertiseAreas", "languages")));
        }
    }

    // ==================== AC2-6: Profile Update Tests ====================

    @Nested
    @DisplayName("AC2-6: Profile Update (PATCH /speaker-portal/profile)")
    class UpdateProfileTests {

        /**
         * Test 2.6: Should return 200 with updated profile when valid patch
         * AC2: Basic profile fields update
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_return200WithUpdatedProfile_when_validPatch() throws Exception {
            mockMvc.perform(patch("/api/v1/speaker-portal/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "firstName": "Janet",
                                    "lastName": "Speaker-Updated",
                                    "bio": "Updated bio for profile."
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username", is(TEST_USERNAME)));
        }

        /**
         * Test 2.7: Should update speaker fields locally
         * AC3-4: Expertise and topics management
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_updateSpeakerFields_when_patchWithSpeakerData() throws Exception {
            mockMvc.perform(patch("/api/v1/speaker-portal/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "expertiseAreas": ["Security", "DevOps", "Cloud"],
                                    "speakingTopics": ["Zero Trust", "CI/CD"],
                                    "linkedInUrl": "https://linkedin.com/in/updated"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk());

            // Verify database update
            Speaker updated = speakerRepository.findByUsername(TEST_USERNAME).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(updated.getExpertiseAreas())
                    .containsExactlyInAnyOrder("Security", "DevOps", "Cloud");
            org.assertj.core.api.Assertions.assertThat(updated.getSpeakingTopics())
                    .containsExactlyInAnyOrder("Zero Trust", "CI/CD");
            org.assertj.core.api.Assertions.assertThat(updated.getLinkedInUrl())
                    .isEqualTo("https://linkedin.com/in/updated");
        }

        /**
         * Test 2.8: Should update languages
         * AC5: Languages selection
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_updateLanguages_when_patchWithLanguages() throws Exception {
            mockMvc.perform(patch("/api/v1/speaker-portal/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "languages": ["de", "en", "fr"]
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk());

            // Verify database update
            Speaker updated = speakerRepository.findByUsername(TEST_USERNAME).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(updated.getLanguages())
                    .containsExactlyInAnyOrder("de", "en", "fr");
        }

        /**
         * Test 2.9: Should call Company Service when user fields updated
         * AC10: Cross-service sync
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_callCompanyService_when_userFieldsUpdated() throws Exception {
            mockMvc.perform(patch("/api/v1/speaker-portal/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "firstName": "Janet",
                                    "bio": "New bio content"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk());

            // Verify Company Service was called
            verify(userApiClient).updateUser(anyString(), any());
        }

        /**
         * Test 2.10: Should return 401 when token expired on update
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_return401_when_tokenExpiredOnUpdate() throws Exception {
            // Given - Create an expired token
            String expiredTokenPlaintext = "expired-update-token-test-67890";
            String tokenHash = sha256(expiredTokenPlaintext);

            SpeakerInvitationToken expiredToken = SpeakerInvitationToken.builder()
                    .speakerPoolId(testSpeakerPoolId)
                    .tokenHash(tokenHash)
                    .action(TokenAction.VIEW)
                    .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .createdAt(Instant.now().minus(31, ChronoUnit.DAYS))
                    .build();
            tokenRepository.save(expiredToken);

            // When/Then
            mockMvc.perform(patch("/api/v1/speaker-portal/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "firstName": "Updated"
                                }
                                """.formatted(expiredTokenPlaintext)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== AC9: Validation Tests ====================

    @Nested
    @DisplayName("AC9: Validation Errors (400)")
    class ValidationTests {

        /**
         * Test 2.11: Should return 400 when bio exceeds 500 characters
         * AC9: Validation - bio length
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_return400_when_bioTooLong() throws Exception {
            String longBio = "x".repeat(501);

            mockMvc.perform(patch("/api/v1/speaker-portal/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "bio": "%s"
                                }
                                """.formatted(validToken, longBio)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Test 2.12: Should return 400 when expertise areas exceed 10
         * AC9: Validation - expertise count
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_return400_when_tooManyExpertiseAreas() throws Exception {
            mockMvc.perform(patch("/api/v1/speaker-portal/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "expertiseAreas": ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"]
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Test 2.13: Should return 400 when speaking topics exceed 10
         * AC9: Validation - topics count
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_return400_when_tooManySpeakingTopics() throws Exception {
            mockMvc.perform(patch("/api/v1/speaker-portal/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "speakingTopics": ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"]
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Test 2.14: Should return 400 when LinkedIn URL invalid
         * AC6: LinkedIn URL validation
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_return400_when_linkedInUrlInvalid() throws Exception {
            mockMvc.perform(patch("/api/v1/speaker-portal/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "linkedInUrl": "https://twitter.com/invalid"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Test 2.15: Should accept valid LinkedIn URL
         * AC6: LinkedIn URL validation - positive case
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_return200_when_linkedInUrlValid() throws Exception {
            mockMvc.perform(patch("/api/v1/speaker-portal/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "linkedInUrl": "https://www.linkedin.com/in/validprofile"
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk());
        }

        /**
         * Test 2.16: Should accept empty LinkedIn URL
         * AC6: LinkedIn URL is optional
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_return200_when_linkedInUrlEmpty() throws Exception {
            mockMvc.perform(patch("/api/v1/speaker-portal/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "token": "%s",
                                    "linkedInUrl": ""
                                }
                                """.formatted(validToken)))
                    .andExpect(status().isOk());
        }

        /**
         * Test 2.17: Should return 400 when token missing in body
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_return400_when_tokenMissingInBody() throws Exception {
            mockMvc.perform(patch("/api/v1/speaker-portal/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "firstName": "Janet"
                                }
                                """))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Test 2.18: Should return 400 when body is empty
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_return400_when_bodyEmpty() throws Exception {
            mockMvc.perform(patch("/api/v1/speaker-portal/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== Speaker Not Found Tests ====================

    @Nested
    @DisplayName("Speaker Not Found (404)")
    class SpeakerNotFoundTests {

        /**
         * Test 2.19: Should return 404 when speaker entity doesn't exist
         * RED Phase: Will fail - SpeakerPortalProfileController doesn't exist yet
         */
        @Test
        void should_return404_when_speakerNotFound() throws Exception {
            // Given - Delete the speaker but keep the token
            speakerRepository.delete(testSpeaker);

            // When/Then
            mockMvc.perform(get("/api/v1/speaker-portal/profile")
                            .param("token", validToken))
                    .andExpect(status().isNotFound());
        }
    }

    // Helper method to compute SHA-256 hash (same as MagicLinkService)
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
