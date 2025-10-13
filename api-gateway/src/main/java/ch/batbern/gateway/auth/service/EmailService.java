package ch.batbern.gateway.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.SendTemplatedEmailRequest;
import software.amazon.awssdk.services.ses.model.SendTemplatedEmailResponse;

/**
 * Email service for sending templated emails via AWS SES
 *
 * Story 1.2.2 - AC15-18: Bilingual email template integration
 */
@Service
public class EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    private final SesClient sesClient;

    @Value("${aws.ses.from-email:noreply@batbern.ch}")
    private String fromEmail;

    @Value("${aws.ses.template.password-reset-de:BATbern-development-PasswordReset-DE}")
    private String passwordResetTemplateDE;

    @Value("${aws.ses.template.password-reset-en:BATbern-development-PasswordReset-EN}")
    private String passwordResetTemplateEN;

    @Value("${spring.profiles.active:development}")
    private String activeProfile;

    public EmailService(SesClient sesClient) {
        this.sesClient = sesClient;
    }

    /**
     * Send password reset email using SES template
     *
     * @param toEmail    Recipient email address
     * @param language   Language code (de or en)
     * @param resetLink  Password reset link to include in email
     */
    public void sendPasswordResetEmail(String toEmail, String language, String resetLink) {
        try {
            // Select template based on language
            String templateName = language.equalsIgnoreCase("de")
                ? passwordResetTemplateDE
                : passwordResetTemplateEN;

            LOGGER.info("Sending password reset email to {} using template {} (language: {})",
                maskEmail(toEmail), templateName, language);

            // In test profile, log instead of sending actual email
            if ("test".equals(activeProfile)) {
                LOGGER.info("TEST MODE: Would send email to {} with reset link: {}",
                    maskEmail(toEmail), resetLink);
                return;
            }

            // Prepare template data
            String templateData = String.format("{\"resetLink\": \"%s\"}", resetLink);

            // Create the email request
            SendTemplatedEmailRequest emailRequest = SendTemplatedEmailRequest.builder()
                .source(fromEmail)
                .destination(Destination.builder()
                    .toAddresses(toEmail)
                    .build())
                .template(templateName)
                .templateData(templateData)
                .build();

            // Send the email
            SendTemplatedEmailResponse response = sesClient.sendTemplatedEmail(emailRequest);

            LOGGER.info("Password reset email sent successfully. Message ID: {}",
                response.messageId());

        } catch (Exception e) {
            LOGGER.error("Failed to send password reset email to {}: {}",
                maskEmail(toEmail), e.getMessage(), e);
            // Don't throw exception - we don't want to reveal if email exists
            // The user will see generic success message regardless
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
