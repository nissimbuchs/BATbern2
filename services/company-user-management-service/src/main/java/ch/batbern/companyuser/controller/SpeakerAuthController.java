package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.dto.CognitoAuthResult;
import ch.batbern.companyuser.dto.SpeakerAuthRequest;
import ch.batbern.companyuser.dto.SpeakerConfirmResetRequest;
import ch.batbern.companyuser.dto.SpeakerForgotPasswordRequest;
import ch.batbern.companyuser.service.CognitoIntegrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.util.Map;

/**
 * Story 9.3 Task 2.4: Internal API endpoints for Cognito speaker authentication.
 *
 * These endpoints are called by event-management-service via UserApiClient.
 * They are VPC-internal and permitted without a user JWT (added to SecurityConfig.java).
 *
 * Endpoints:
 * - POST /api/v1/speaker-auth/authenticate  → Cognito ADMIN_USER_PASSWORD_AUTH
 * - POST /api/v1/speaker-auth/forgot-password → Cognito ForgotPassword (sends code email)
 * - POST /api/v1/speaker-auth/confirm-reset  → Cognito ConfirmForgotPassword
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/speaker-auth")
@RequiredArgsConstructor
public class SpeakerAuthController {

    private final CognitoIntegrationService cognitoIntegrationService;

    /**
     * POST /api/v1/speaker-auth/authenticate
     *
     * Authenticates a speaker via Cognito ADMIN_USER_PASSWORD_AUTH.
     * Returns Cognito tokens (access, id, refresh) to caller.
     * 401 on invalid credentials.
     */
    @PostMapping("/authenticate")
    public ResponseEntity<?> authenticate(@Valid @RequestBody SpeakerAuthRequest request) {
        try {
            CognitoAuthResult result = cognitoIntegrationService.authenticateUser(
                    request.getEmail(), request.getPassword());
            return ResponseEntity.ok(result);
        } catch (NotAuthorizedException e) {
            log.warn("Speaker authentication failed — invalid credentials");
            return ResponseEntity.status(401).body(Map.of(
                    "error", "INVALID_CREDENTIALS",
                    "message", "Ungültige Anmeldedaten."
            ));
        } catch (UserNotFoundException e) {
            log.warn("Speaker authentication failed — user not found in Cognito");
            return ResponseEntity.status(401).body(Map.of(
                    "error", "INVALID_CREDENTIALS",
                    "message", "Ungültige Anmeldedaten."
            ));
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotConfirmedException e) {
            log.warn("Speaker authentication failed — Cognito account not confirmed");
            return ResponseEntity.status(403).body(Map.of(
                    "error", "USER_NOT_CONFIRMED",
                    "message", "Dein Konto wurde noch nicht bestätigt. Bitte prüfe deine E-Mails."
            ));
        }
    }

    /**
     * POST /api/v1/speaker-auth/forgot-password
     *
     * Triggers Cognito ForgotPassword flow — Cognito sends a confirmation code to the speaker's email.
     * Always returns 200 (no email enumeration).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody SpeakerForgotPasswordRequest request) {
        try {
            cognitoIntegrationService.initiatePasswordReset(request.getEmail());
        } catch (UserNotFoundException e) {
            // Silently ignore — no email enumeration
            log.debug("Forgot-password: Cognito user not found for email (suppressed for anti-enumeration)");
        }
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/v1/speaker-auth/confirm-reset
     *
     * Confirms Cognito password reset with code + new password.
     * 400 on invalid/expired code.
     */
    @PostMapping("/confirm-reset")
    public ResponseEntity<?> confirmReset(@Valid @RequestBody SpeakerConfirmResetRequest request) {
        try {
            cognitoIntegrationService.confirmPasswordReset(
                    request.getEmail(), request.getConfirmationCode(), request.getNewPassword());
            return ResponseEntity.ok().build();
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_CODE",
                    "message", "Der Code ist falsch oder abgelaufen."
            ));
        } catch (software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "EXPIRED_CODE",
                    "message", "Der Code ist abgelaufen. Bitte fordere einen neuen an."
            ));
        }
    }
}
