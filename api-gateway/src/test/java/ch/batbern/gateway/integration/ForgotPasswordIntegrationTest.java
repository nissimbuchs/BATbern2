package ch.batbern.gateway.integration;

import ch.batbern.gateway.auth.service.RateLimitService;
import ch.batbern.gateway.config.TestSecurityConfig;
import ch.batbern.gateway.security.InMemoryRateLimitStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Integration Tests for Forgot Password Flow
 *
 * TDD RED Phase: These tests are written first and should FAIL initially.
 * Tests cover Story 1.2.2 Acceptance Criteria:
 * - AC11: Cognito forgotPassword API integration
 * - AC12: Email enumeration prevention
 * - AC13: Rate limiting (3 requests per hour per email)
 * - AC15-18: Bilingual email template integration
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("Forgot Password Flow Integration Tests")
class ForgotPasswordIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private InMemoryRateLimitStorage rateLimitStorage;

    /**
     * Reset rate limit history before each test to ensure test isolation
     * Fixes QA issue TEST-001: Rate limit state persisting between tests
     */
    @BeforeEach
    void setUp() {
        // Clear password reset rate limits (3/hour per email)
        rateLimitService.clearHistory();

        // Clear general API Gateway rate limits (10/min per endpoint)
        rateLimitStorage.clearAll();
    }

    // ========================================
    // AC11: Forgot Password API Endpoint Tests
    // ========================================

    @Test
    @DisplayName("should_return200_when_validEmailProvided")
    void should_return200_when_validEmailProvided() throws Exception {
        // Given: A valid forgot password request
        Map<String, String> request = new HashMap<>();
        request.put("email", "user@example.com");

        // When: Request is sent to forgot password endpoint
        MvcResult result = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andReturn();

        // Then: Response should indicate success
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("success");
    }

    @Test
    @DisplayName("should_return400_when_emailMissing")
    void should_return400_when_emailMissing() throws Exception {
        // Given: Request with missing email
        Map<String, String> request = new HashMap<>();

        // When: Request is sent without email
        // Then: Should return 400 Bad Request
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return400_when_emailInvalidFormat")
    void should_return400_when_emailInvalidFormat() throws Exception {
        // Given: Request with invalid email format
        Map<String, String> request = new HashMap<>();
        request.put("email", "invalid-email");

        // When: Request is sent with invalid email
        // Then: Should return 400 Bad Request
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return400_when_emailTooLong")
    void should_return400_when_emailTooLong() throws Exception {
        // Given: Request with email exceeding 255 characters
        String longEmail = "a".repeat(250) + "@example.com";
        Map<String, String> request = new HashMap<>();
        request.put("email", longEmail);

        // When: Request is sent with too-long email
        // Then: Should return 400 Bad Request
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ========================================
    // AC12: Email Enumeration Prevention Tests
    // ========================================

    @Test
    @DisplayName("should_returnSameResponse_when_emailNotFound")
    void should_returnSameResponse_when_emailNotFound() throws Exception {
        // Given: Email that doesn't exist in system
        Map<String, String> request = new HashMap<>();
        request.put("email", "nonexistent@example.com");

        // When: Request is sent
        MvcResult result = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Response should be identical to success case (prevent enumeration)
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("success");
        assertThat(responseBody).doesNotContain("not found");
        assertThat(responseBody).doesNotContain("does not exist");
    }

    @Test
    @DisplayName("should_notRevealUserExistence_when_emailChecked")
    void should_notRevealUserExistence_when_emailChecked() throws Exception {
        // Given: Two requests - one with existing email, one without
        Map<String, String> existingRequest = new HashMap<>();
        existingRequest.put("email", "existing@example.com");

        Map<String, String> nonexistentRequest = new HashMap<>();
        nonexistentRequest.put("email", "nonexistent@example.com");

        // When: Both requests are sent
        MvcResult existingResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existingRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult nonexistentResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonexistentRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Responses should be identical (prevent user enumeration)
        String existingResponse = existingResult.getResponse().getContentAsString();
        String nonexistentResponse = nonexistentResult.getResponse().getContentAsString();

        assertThat(existingResponse).isEqualTo(nonexistentResponse);
    }

    // ========================================
    // AC13: Rate Limiting Tests
    // ========================================

    @Test
    @DisplayName("should_return429_when_rateLimitExceeded")
    void should_return429_when_rateLimitExceeded() throws Exception {
        // Given: Same email making multiple requests
        Map<String, String> request = new HashMap<>();
        request.put("email", "ratelimit@example.com");

        // When: Making 3 successful requests (within limit)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // Then: 4th request should return 429 Too Many Requests
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("should_allowRequests_when_withinRateLimit")
    void should_allowRequests_when_withinRateLimit() throws Exception {
        // Given: Email making requests within rate limit
        Map<String, String> request = new HashMap<>();
        request.put("email", "withinlimit@example.com");

        // When: Making 3 requests (within 3 per hour limit)
        for (int i = 0; i < 3; i++) {
            // Then: All requests should succeed
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Test
    @DisplayName("should_trackRateLimitPerEmail_when_multipleEmailsUsed")
    void should_trackRateLimitPerEmail_when_multipleEmailsUsed() throws Exception {
        // Given: Two different emails
        Map<String, String> request1 = new HashMap<>();
        request1.put("email", "user1@example.com");

        Map<String, String> request2 = new HashMap<>();
        request2.put("email", "user2@example.com");

        // When: Each email makes 3 requests
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request1)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request2)))
                    .andExpect(status().isOk());
        }

        // Then: Both should succeed (rate limit is per email, not global)
        // This verifies that rate limiting is tracked separately per email
    }

    // ========================================
    // AC15-18: Bilingual Email Template Tests
    // ========================================

    @Test
    @DisplayName("should_sendGermanEmail_when_acceptLanguageIsDE")
    void should_sendGermanEmail_when_acceptLanguageIsDE() throws Exception {
        // Given: Request with German Accept-Language header
        Map<String, String> request = new HashMap<>();
        request.put("email", "german@example.com");

        // When: Request is sent with German locale
        MvcResult result = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept-Language", "de-CH")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Response should indicate success
        // Email service should receive German template selection (verified in service tests)
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("success");
    }

    @Test
    @DisplayName("should_sendEnglishEmail_when_acceptLanguageIsEN")
    void should_sendEnglishEmail_when_acceptLanguageIsEN() throws Exception {
        // Given: Request with English Accept-Language header
        Map<String, String> request = new HashMap<>();
        request.put("email", "english@example.com");

        // When: Request is sent with English locale
        MvcResult result = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept-Language", "en-US")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Response should indicate success
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("success");
    }

    @Test
    @DisplayName("should_defaultToGerman_when_acceptLanguageMissing")
    void should_defaultToGerman_when_acceptLanguageMissing() throws Exception {
        // Given: Request without Accept-Language header
        Map<String, String> request = new HashMap<>();
        request.put("email", "default@example.com");

        // When: Request is sent without language header
        MvcResult result = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Should default to German (default language)
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("success");
    }

    // ========================================
    // AC14: Security Logging Tests
    // ========================================

    @Test
    @DisplayName("should_logAttempt_when_passwordResetRequested")
    void should_logAttempt_when_passwordResetRequested() throws Exception {
        // Given: A password reset request
        Map<String, String> request = new HashMap<>();
        request.put("email", "logging@example.com");

        // When: Request is processed
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "192.168.1.1")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Then: Attempt should be logged with IP address
        // (Logging verification would be done with log capture in service tests)
    }

    // ========================================
    // Resend Reset Link Tests
    // ========================================

    @Test
    @DisplayName("should_allowResend_when_resendEndpointCalled")
    void should_allowResend_when_resendEndpointCalled() throws Exception {
        // Given: A resend reset link request
        Map<String, String> request = new HashMap<>();
        request.put("email", "resend@example.com");

        // When: Resend endpoint is called
        MvcResult result = mockMvc.perform(post("/api/v1/auth/resend-reset-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        // Then: Should return success response
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("success");
    }

    @Test
    @DisplayName("should_respectRateLimit_when_resendingResetLink")
    void should_respectRateLimit_when_resendingResetLink() throws Exception {
        // Given: Email that has already requested reset link
        Map<String, String> request = new HashMap<>();
        request.put("email", "resend-ratelimit@example.com");

        // When: Making initial request
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // And: Resending multiple times
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/resend-reset-link")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // Then: Next resend should hit rate limit
        mockMvc.perform(post("/api/v1/auth/resend-reset-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }
}
