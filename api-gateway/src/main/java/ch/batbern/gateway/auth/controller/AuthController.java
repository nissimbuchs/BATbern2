package ch.batbern.gateway.auth.controller;

import ch.batbern.gateway.auth.dto.ForgotPasswordRequest;
import ch.batbern.gateway.auth.dto.ForgotPasswordResponse;
import ch.batbern.gateway.auth.dto.ResendResetLinkRequest;
import ch.batbern.gateway.auth.dto.ResendResetLinkResponse;
import ch.batbern.gateway.auth.exception.RateLimitExceededException;
import ch.batbern.gateway.auth.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication Controller - Password Reset Endpoints
 *
 * Story 1.2.2 - Forgot Password Flow
 * Implements AC11-AC18 (API endpoints, rate limiting, email templates)
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication and password management endpoints")
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final PasswordResetService passwordResetService;

    public AuthController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /**
     * Forgot Password Endpoint
     *
     * Story 1.2.2 - AC11: Cognito forgotPassword API integration
     * Story 1.2.2 - AC12: Email enumeration prevention (always returns success)
     * Story 1.2.2 - AC13: Rate limiting (3 requests per hour per email)
     * Story 1.2.2 - AC15-18: Bilingual email template selection
     */
    @PostMapping("/forgot-password")
    @Operation(
        summary = "Request password reset",
        description = "Initiates password reset flow by sending a reset link to the user's email. "
                     + "Always returns success to prevent email enumeration."
    )
    @ApiResponse(responseCode = "200", description = "Password reset email sent (or would be sent if email exists)")
    @ApiResponse(responseCode = "400", description = "Invalid request (email validation failed)")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded (3 requests per hour)")
    public ResponseEntity<?> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "de") String acceptLanguage,
            HttpServletRequest httpRequest) {

        // Extract language (de or en)
        String language = acceptLanguage.toLowerCase().startsWith("de") ? "de" : "en";

        // Get client IP address
        String ipAddress = getClientIpAddress(httpRequest);

        LOGGER.info("Forgot password request received for email (masked), language: {}, IP: {}",
            language, ipAddress);

        try {
            // Call password reset service
            passwordResetService.initiatePasswordReset(request.getEmail(), language, ipAddress);

            // AC12: Always return success (email enumeration prevention)
            return ResponseEntity.ok(new ForgotPasswordResponse(
                true,
                "If an account exists with this email, you will receive a password reset link."
            ));

        } catch (RateLimitExceededException e) {
            // AC13: Rate limit exceeded - return 429
            LOGGER.warn("Rate limit exceeded for forgot password request from IP: {}", ipAddress);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Rate limit exceeded");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(errorResponse);
        }
    }

    /**
     * Resend Reset Link Endpoint
     *
     * Story 1.2.2 - AC7: Resend functionality with 60-second cooldown
     * Same rate limiting as forgot-password
     */
    @PostMapping("/resend-reset-link")
    @Operation(
        summary = "Resend password reset link",
        description = "Resends the password reset link to the user's email. "
                     + "Subject to same rate limiting as forgot-password (3 per hour)."
    )
    @ApiResponse(responseCode = "200", description = "Reset link resent successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    public ResponseEntity<ResendResetLinkResponse> resendResetLink(
            @Valid @RequestBody ResendResetLinkRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "de") String acceptLanguage,
            HttpServletRequest httpRequest) {

        String language = acceptLanguage.toLowerCase().startsWith("de") ? "de" : "en";
        String ipAddress = getClientIpAddress(httpRequest);

        LOGGER.info("Resend reset link request received, language: {}, IP: {}", language, ipAddress);

        try {
            // Reuse the same password reset service (same rate limiting)
            passwordResetService.initiatePasswordReset(request.getEmail(), language, ipAddress);

            return ResponseEntity.ok(new ResendResetLinkResponse(
                true,
                "Reset link sent again to your email."
            ));

        } catch (RateLimitExceededException e) {
            LOGGER.warn("Rate limit exceeded for resend request from IP: {}", ipAddress);

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ResendResetLinkResponse(false, e.getMessage()));
        }
    }

    /**
     * Get client IP address from request
     * Handles proxy headers (X-Forwarded-For, X-Real-IP)
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
