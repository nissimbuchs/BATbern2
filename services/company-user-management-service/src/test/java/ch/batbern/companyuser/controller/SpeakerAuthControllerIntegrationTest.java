package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.dto.CognitoAuthResult;
import ch.batbern.companyuser.service.CognitoIntegrationService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotConfirmedException;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SpeakerAuthController (Story 9.3 Task 2.4 / Code Review Fix C1).
 *
 * Tests the three endpoints:
 * - POST /api/v1/speaker-auth/authenticate     (AC2, AC6)
 * - POST /api/v1/speaker-auth/forgot-password  (AC4)
 * - POST /api/v1/speaker-auth/confirm-reset    (AC4)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("SpeakerAuthController Integration Tests")
class SpeakerAuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CognitoIntegrationService cognitoIntegrationService;

    @BeforeEach
    void setUp() {
        reset(cognitoIntegrationService);
    }

    // ─── POST /api/v1/speaker-auth/authenticate ────────────────────────────────

    @Test
    @DisplayName("should return 200 with tokens when credentials are valid")
    void should_return200WithTokens_when_validCredentials() throws Exception {
        CognitoAuthResult tokenResult = CognitoAuthResult.builder()
                .accessToken("test-access-token")
                .idToken("test-id-token")
                .refreshToken("test-refresh-token")
                .build();
        when(cognitoIntegrationService.authenticateUser("speaker@example.com", "SecurePass123!"))
                .thenReturn(tokenResult);

        mockMvc.perform(post("/api/v1/speaker-auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "speaker@example.com", "password", "SecurePass123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idToken").value("test-id-token"))
                .andExpect(jsonPath("$.accessToken").value("test-access-token"));
    }

    @Test
    @DisplayName("should return 401 INVALID_CREDENTIALS when password is wrong")
    void should_return401_when_invalidCredentials() throws Exception {
        when(cognitoIntegrationService.authenticateUser(anyString(), anyString()))
                .thenThrow(NotAuthorizedException.builder().message("Incorrect username or password").build());

        mockMvc.perform(post("/api/v1/speaker-auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "speaker@example.com", "password", "WrongPass!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("should return 401 INVALID_CREDENTIALS when Cognito user not found")
    void should_return401_when_userNotFoundInCognito() throws Exception {
        when(cognitoIntegrationService.authenticateUser(anyString(), anyString()))
                .thenThrow(UserNotFoundException.builder().message("User does not exist").build());

        mockMvc.perform(post("/api/v1/speaker-auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "nobody@example.com", "password", "Pass123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("should return 403 USER_NOT_CONFIRMED when Cognito account not confirmed")
    void should_return403_when_userNotConfirmed() throws Exception {
        when(cognitoIntegrationService.authenticateUser(anyString(), anyString()))
                .thenThrow(UserNotConfirmedException.builder().message("User is not confirmed").build());

        mockMvc.perform(post("/api/v1/speaker-auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "unconfirmed@example.com", "password", "Pass123!"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("USER_NOT_CONFIRMED"));
    }

    @Test
    @DisplayName("should return 400 when email is invalid (AC6 — M4 injection fix)")
    void should_return400_when_invalidEmailFormat() throws Exception {
        mockMvc.perform(post("/api/v1/speaker-auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "not-an-email\"; DROP TABLE users; --", "password", "Pass123!"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return 400 when password is blank")
    void should_return400_when_passwordBlank() throws Exception {
        mockMvc.perform(post("/api/v1/speaker-auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "speaker@example.com", "password", ""))))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /api/v1/speaker-auth/forgot-password ────────────────────────────

    @Test
    @DisplayName("should return 200 when forgot-password succeeds")
    void should_return200_when_forgotPasswordSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/speaker-auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "speaker@example.com"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 200 even when Cognito user not found (no enumeration)")
    void should_return200_when_forgotPasswordForUnknownEmail() throws Exception {
        doThrow(UserNotFoundException.builder().message("User not found").build())
                .when(cognitoIntegrationService).initiatePasswordReset(anyString());

        // Suppress and return 200 — no email enumeration
        mockMvc.perform(post("/api/v1/speaker-auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "unknown@example.com"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 400 when email is invalid (AC6)")
    void should_return400_when_forgotPasswordInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/speaker-auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "invalid-email"))))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /api/v1/speaker-auth/confirm-reset ───────────────────────────────

    @Test
    @DisplayName("should return 200 when confirm-reset succeeds")
    void should_return200_when_confirmResetSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/speaker-auth/confirm-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "speaker@example.com",
                                "confirmationCode", "123456",
                                "newPassword", "NewSecureP@ss1"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should return 400 INVALID_CODE when Cognito code is wrong")
    void should_return400_when_codeMismatch() throws Exception {
        doThrow(software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException
                        .builder().message("Invalid verification code provided").build())
                .when(cognitoIntegrationService).confirmPasswordReset(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/speaker-auth/confirm-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "speaker@example.com",
                                "confirmationCode", "wrong",
                                "newPassword", "NewSecureP@ss1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CODE"));
    }

    @Test
    @DisplayName("should return 400 EXPIRED_CODE when Cognito code is expired")
    void should_return400_when_codeExpired() throws Exception {
        doThrow(software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException
                        .builder().message("Invalid code provided, please request a code again").build())
                .when(cognitoIntegrationService).confirmPasswordReset(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/speaker-auth/confirm-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "speaker@example.com",
                                "confirmationCode", "expired",
                                "newPassword", "NewSecureP@ss1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("EXPIRED_CODE"));
    }

    @Test
    @DisplayName("should return 400 when new password is too short (< 8 chars)")
    void should_return400_when_newPasswordTooShort() throws Exception {
        mockMvc.perform(post("/api/v1/speaker-auth/confirm-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "speaker@example.com",
                                "confirmationCode", "123456",
                                "newPassword", "Short1"))))
                .andExpect(status().isBadRequest());
    }
}
