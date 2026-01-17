package ch.batbern.events.service;

import ch.batbern.events.domain.SpeakerInvitation;
import ch.batbern.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending speaker invitation emails - Story 6.1 AC4.
 *
 * Uses the shared EmailService for AWS SES integration.
 * Supports template variable substitution for personalized invitations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationEmailService {

    private final EmailService emailService;
    private final SpeakerService speakerService;

    @Value("${app.frontend.url:http://localhost:8100}")
    private String frontendUrl;

    @Value("${app.email.invitation.enabled:true}")
    private boolean emailEnabled;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd. MMMM yyyy");

    /**
     * Send invitation email to speaker.
     *
     * @param invitation The speaker invitation
     * @param organizerName Name of the organizer sending the invitation
     * @param personalMessage Optional personal message from organizer
     */
    public void sendInvitationEmail(SpeakerInvitation invitation, String organizerName, String personalMessage) {
        if (!emailEnabled) {
            log.info("Email sending disabled - skipping invitation email for {}", invitation.getUsername());
            return;
        }

        try {
            // Get speaker details for personalization
            var speaker = speakerService.getSpeakerEntityByUsername(invitation.getUsername());
            String speakerEmail = getSpeakerEmail(speaker.getUsername());

            if (speakerEmail == null || speakerEmail.isBlank()) {
                log.warn("No email found for speaker {} - skipping email", invitation.getUsername());
                return;
            }

            // Build template variables
            Map<String, String> variables = new HashMap<>();
            variables.put("speakerName", speaker.getUsername()); // TODO: Get actual name from user service
            variables.put("eventCode", invitation.getEventCode());
            variables.put("organizerName", organizerName);
            variables.put("responseUrl", buildResponseUrl(invitation.getResponseToken()));
            variables.put("expirationDate", formatExpirationDate(invitation));
            variables.put("personalMessage", personalMessage != null ? personalMessage : "");

            String subject = String.format("Speaker Invitation for %s", invitation.getEventCode());
            String htmlBody = buildInvitationEmailHtml(variables);

            emailService.sendHtmlEmail(speakerEmail, subject, htmlBody);
            log.info("Invitation email sent to {} for event {}", speakerEmail, invitation.getEventCode());

        } catch (Exception e) {
            log.error("Failed to send invitation email to {}: {}", invitation.getUsername(), e.getMessage());
            // Don't throw - email failure shouldn't block invitation creation
        }
    }

    /**
     * Send reminder email for pending invitation.
     *
     * @param invitation The speaker invitation
     */
    public void sendReminderEmail(SpeakerInvitation invitation) {
        if (!emailEnabled) {
            log.info("Email sending disabled - skipping reminder email for {}", invitation.getUsername());
            return;
        }

        try {
            var speaker = speakerService.getSpeakerEntityByUsername(invitation.getUsername());
            String speakerEmail = getSpeakerEmail(speaker.getUsername());

            if (speakerEmail == null || speakerEmail.isBlank()) {
                log.warn("No email found for speaker {} - skipping reminder", invitation.getUsername());
                return;
            }

            Map<String, String> variables = new HashMap<>();
            variables.put("speakerName", speaker.getUsername());
            variables.put("eventCode", invitation.getEventCode());
            variables.put("responseUrl", buildResponseUrl(invitation.getResponseToken()));
            variables.put("expirationDate", formatExpirationDate(invitation));

            String subject = String.format("Reminder: Speaker Invitation for %s", invitation.getEventCode());
            String htmlBody = buildReminderEmailHtml(variables);

            emailService.sendHtmlEmail(speakerEmail, subject, htmlBody);
            log.info("Reminder email sent to {} for event {}", speakerEmail, invitation.getEventCode());

        } catch (Exception e) {
            log.error("Failed to send reminder email to {}: {}", invitation.getUsername(), e.getMessage());
        }
    }

    private String buildResponseUrl(String token) {
        return String.format("%s/speaker/invitation/%s", frontendUrl, token);
    }

    private String formatExpirationDate(SpeakerInvitation invitation) {
        if (invitation.getExpiresAt() == null) {
            return "No expiration";
        }
        return DATE_FORMATTER.format(invitation.getExpiresAt().atZone(java.time.ZoneId.of("Europe/Zurich")));
    }

    private String getSpeakerEmail(String username) {
        // TODO: Fetch from user service via API client
        // For now, assume username might be email format
        if (username.contains("@")) {
            return username;
        }
        // Return null if we can't determine email - will skip sending
        log.debug("Cannot determine email for username: {}", username);
        return null;
    }

    private String buildInvitationEmailHtml(Map<String, String> variables) {
        String template = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #1a365d; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; }
                    .button { display: inline-block; background: #3182ce; color: white; padding: 12px 24px;
                              text-decoration: none; border-radius: 4px; margin: 20px 0; }
                    .footer { padding: 20px; font-size: 12px; color: #666; border-top: 1px solid #eee; }
                    .personal-message { background: #f7fafc; padding: 15px; border-left: 4px solid #3182ce; margin: 20px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>BATbern - Speaker Invitation</h1>
                    </div>
                    <div class="content">
                        <p>Grüezi {{speakerName}},</p>

                        <p>You have been invited to speak at <strong>{{eventCode}}</strong>.</p>

                        {{#personalMessage}}
                        <div class="personal-message">
                            <p><strong>Personal message from {{organizerName}}:</strong></p>
                            <p>{{personalMessage}}</p>
                        </div>
                        {{/personalMessage}}

                        <p>Please respond to this invitation by clicking the button below:</p>

                        <p style="text-align: center;">
                            <a href="{{responseUrl}}" class="button">Respond to Invitation</a>
                        </p>

                        <p><small>This invitation expires on {{expirationDate}}.</small></p>

                        <p>If you have any questions, please contact the organizer.</p>

                        <p>Best regards,<br>
                        The BATbern Team</p>
                    </div>
                    <div class="footer">
                        <p>Berner Architekten Treffen | <a href="https://batbern.ch">batbern.ch</a></p>
                        <p>If you cannot click the button, copy this link: {{responseUrl}}</p>
                    </div>
                </div>
            </body>
            </html>
            """;

        return emailService.replaceVariables(template, variables);
    }

    private String buildReminderEmailHtml(Map<String, String> variables) {
        String template = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #744210; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; }
                    .button { display: inline-block; background: #d69e2e; color: white; padding: 12px 24px;
                              text-decoration: none; border-radius: 4px; margin: 20px 0; }
                    .footer { padding: 20px; font-size: 12px; color: #666; border-top: 1px solid #eee; }
                    .urgent { background: #fffaf0; padding: 15px; border-left: 4px solid #d69e2e; margin: 20px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Reminder: Speaker Invitation</h1>
                    </div>
                    <div class="content">
                        <p>Grüezi {{speakerName}},</p>

                        <div class="urgent">
                            <p><strong>Reminder:</strong> You have a pending speaker invitation for
                            <strong>{{eventCode}}</strong> that expires on <strong>{{expirationDate}}</strong>.</p>
                        </div>

                        <p>Please respond to this invitation by clicking the button below:</p>

                        <p style="text-align: center;">
                            <a href="{{responseUrl}}" class="button">Respond Now</a>
                        </p>

                        <p>Best regards,<br>
                        The BATbern Team</p>
                    </div>
                    <div class="footer">
                        <p>Berner Architekten Treffen | <a href="https://batbern.ch">batbern.ch</a></p>
                        <p>If you cannot click the button, copy this link: {{responseUrl}}</p>
                    </div>
                </div>
            </body>
            </html>
            """;

        return emailService.replaceVariables(template, variables);
    }
}
