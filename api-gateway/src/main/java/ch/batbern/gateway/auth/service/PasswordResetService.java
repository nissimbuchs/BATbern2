package ch.batbern.gateway.auth.service;

import ch.batbern.gateway.auth.AuditLogger;
import ch.batbern.gateway.auth.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Service for handling password reset operations
 *
 * Story 1.2.2 - Password Reset Flow
 * - AC11: Cognito forgotPassword API integration
 * - AC12: Email enumeration prevention
 * - AC13: Rate limiting
 * - AC15-18: Bilingual email templates
 */
@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);

    private final CognitoIdentityProviderClient cognitoClient;
    private final EmailService emailService;
    private final RateLimitService rateLimitService;
    private final AuditLogger auditLogger;

    @Value("${aws.cognito.userPoolClientId}")
    private String cognitoClientId;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public PasswordResetService(
        CognitoIdentityProviderClient cognitoClient,
        EmailService emailService,
        RateLimitService rateLimitService,
        AuditLogger auditLogger
    ) {
        this.cognitoClient = cognitoClient;
        this.emailService = emailService;
        this.rateLimitService = rateLimitService;
        this.auditLogger = auditLogger;
    }

    /**
     * Initiate password reset flow
     *
     * @param email    User's email address
     * @param language Preferred language (de or en)
     * @param ipAddress User's IP address for audit logging
     */
    public void initiatePasswordReset(String email, String language, String ipAddress) {
        logger.info("Password reset requested for email: {} (language: {})",
            maskEmail(email), language);

        // AC13: Check rate limit (3 per hour per email)
        if (!rateLimitService.allowPasswordReset(email)) {
            logger.warn("Rate limit exceeded for password reset: {}", maskEmail(email));
            auditLogger.logPasswordResetAttempt(email, ipAddress, "RATE_LIMIT_EXCEEDED");
            throw new RateLimitExceededException(
                "Too many password reset requests. Please try again later."
            );
        }

        try {
            // AC11: Call Cognito forgotPassword API
            ForgotPasswordRequest cognitoRequest = ForgotPasswordRequest.builder()
                .clientId(cognitoClientId)
                .username(email)
                .build();

            ForgotPasswordResponse cognitoResponse = cognitoClient.forgotPassword(cognitoRequest);

            // Get the verification code delivery destination
            String codeDeliveryDestination = cognitoResponse.codeDeliveryDetails().destination();

            logger.info("Cognito password reset initiated. Code will be sent to: {}",
                codeDeliveryDestination);

            // Build reset link
            // Format: https://app.batbern.ch/auth/reset-password?email={email}&lang={lang}
            // Note: The actual verification code will be sent by Cognito via email
            // We're sending a custom email with our branding that includes the code
            String resetLink = String.format(
                "%s/auth/reset-password?email=%s&lang=%s",
                frontendUrl,
                URLEncoder.encode(email, StandardCharsets.UTF_8),
                language
            );

            // AC15-18: Send bilingual email using SES template
            emailService.sendPasswordResetEmail(email, language, resetLink);

            // AC14: Log successful attempt
            auditLogger.logPasswordResetAttempt(email, ipAddress, "SUCCESS");

            logger.info("Password reset email sent successfully to: {}", maskEmail(email));

        } catch (UserNotFoundException e) {
            // AC12: Email enumeration prevention
            // Don't reveal that user doesn't exist - just log and continue
            logger.info("Password reset requested for non-existent user: {}",
                maskEmail(email));
            auditLogger.logPasswordResetAttempt(email, ipAddress, "USER_NOT_FOUND");

            // Still log success from caller's perspective (enumeration prevention)

        } catch (Exception e) {
            // Log error but don't throw - enumeration prevention
            logger.error("Failed to initiate password reset for {}: {}",
                maskEmail(email), e.getMessage(), e);
            auditLogger.logPasswordResetAttempt(email, ipAddress, "FAILURE");

            // Don't throw exception to prevent enumeration
            // Caller will see generic success message
        }
    }

    /**
     * Mask email address for logging (security)
     * Example: user@example.com -> u***@example.com
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }

        String[] parts = email.split("@");
        String local = parts[0];
        String domain = parts[1];

        if (local.length() <= 1) {
            return "*@" + domain;
        }

        return local.charAt(0) + "***@" + domain;
    }
}
