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
     * Uses denormalized speakerEmail and speakerName from the invitation entity
     * to avoid cross-table lookups.
     *
     * @param invitation The speaker invitation
     * @param organizerName Name of the organizer sending the invitation
     * @param personalMessage Optional personal message from organizer
     */
    public void sendInvitationEmail(SpeakerInvitation invitation, String organizerName, String personalMessage) {
        String speakerIdentifier = invitation.getSpeakerName() != null
                ? invitation.getSpeakerName()
                : invitation.getUsername();

        if (!emailEnabled) {
            log.info("Email sending disabled - skipping invitation email for {}", speakerIdentifier);
            return;
        }

        try {
            // Use denormalized email from invitation (set during invitation creation)
            String speakerEmail = invitation.getSpeakerEmail();

            if (speakerEmail == null || speakerEmail.isBlank()) {
                log.warn("No email found for speaker {} - skipping email", speakerIdentifier);
                return;
            }

            // Use denormalized name from invitation
            String speakerName = invitation.getSpeakerName() != null
                    ? invitation.getSpeakerName()
                    : invitation.getUsername();

            // Build template variables
            Map<String, String> variables = new HashMap<>();
            variables.put("speakerName", speakerName);
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
            log.error("Failed to send invitation email to {}: {}", speakerIdentifier, e.getMessage());
            // Don't throw - email failure shouldn't block invitation creation
        }
    }

    /**
     * Send reminder email for pending invitation.
     *
     * Uses denormalized speakerEmail and speakerName from the invitation entity.
     *
     * @param invitation The speaker invitation
     */
    public void sendReminderEmail(SpeakerInvitation invitation) {
        String speakerIdentifier = invitation.getSpeakerName() != null
                ? invitation.getSpeakerName()
                : invitation.getUsername();

        if (!emailEnabled) {
            log.info("Email sending disabled - skipping reminder email for {}", speakerIdentifier);
            return;
        }

        try {
            // Use denormalized email from invitation
            String speakerEmail = invitation.getSpeakerEmail();

            if (speakerEmail == null || speakerEmail.isBlank()) {
                log.warn("No email found for speaker {} - skipping reminder", speakerIdentifier);
                return;
            }

            // Use denormalized name from invitation
            String speakerName = invitation.getSpeakerName() != null
                    ? invitation.getSpeakerName()
                    : invitation.getUsername();

            Map<String, String> variables = new HashMap<>();
            variables.put("speakerName", speakerName);
            variables.put("eventCode", invitation.getEventCode());
            variables.put("responseUrl", buildResponseUrl(invitation.getResponseToken()));
            variables.put("expirationDate", formatExpirationDate(invitation));

            String subject = String.format("Reminder: Speaker Invitation for %s", invitation.getEventCode());
            String htmlBody = buildReminderEmailHtml(variables);

            emailService.sendHtmlEmail(speakerEmail, subject, htmlBody);
            log.info("Reminder email sent to {} for event {}", speakerEmail, invitation.getEventCode());

        } catch (Exception e) {
            log.error("Failed to send reminder email to {}: {}", speakerIdentifier, e.getMessage());
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
                    .personal-message { background: #f7fafc; padding: 15px;
                                       border-left: 4px solid #3182ce; margin: 20px 0; }
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

    /**
     * Send notification to organizer when speaker responds to invitation - Story 6.2 AC6.
     *
     * Uses denormalized speakerName from the invitation entity.
     *
     * @param invitation The speaker invitation
     * @param responseType The speaker's response (ACCEPTED, DECLINED, TENTATIVE)
     * @param organizerEmail Email of the organizer to notify
     */
    public void sendOrganizerNotificationEmail(
            SpeakerInvitation invitation,
            String responseType,
            String organizerEmail) {
        if (!emailEnabled) {
            log.info("Email sending disabled - skipping organizer notification for {}", invitation.getEventCode());
            return;
        }

        if (organizerEmail == null || organizerEmail.isBlank()) {
            log.warn("No organizer email provided - skipping notification for invitation {}", invitation.getId());
            return;
        }

        try {
            // Use denormalized name from invitation
            String speakerName = invitation.getSpeakerName() != null
                    ? invitation.getSpeakerName()
                    : invitation.getUsername();

            Map<String, String> variables = new HashMap<>();
            variables.put("speakerName", speakerName);
            variables.put("eventCode", invitation.getEventCode());
            variables.put("responseType", formatResponseType(responseType));
            variables.put("declineReason", invitation.getDeclineReason() != null ? invitation.getDeclineReason() : "");
            variables.put("hasDeclineReason",
                    invitation.getDeclineReason() != null
                            && !invitation.getDeclineReason().isBlank() ? "true" : "");
            variables.put("notes", invitation.getNotes() != null ? invitation.getNotes() : "");
            variables.put("hasNotes",
                    invitation.getNotes() != null
                            && !invitation.getNotes().isBlank() ? "true" : "");

            // Include preferences summary if accepted
            variables.put("hasPreferences", "ACCEPTED".equals(responseType) && hasPreferences(invitation) ? "true" : "");
            variables.put("preferencesSummary", buildPreferencesSummary(invitation));

            String subject = String.format("Speaker Response: %s %s for %s",
                    speakerName, formatResponseType(responseType).toLowerCase(), invitation.getEventCode());
            String htmlBody = buildOrganizerNotificationHtml(variables);

            emailService.sendHtmlEmail(organizerEmail, subject, htmlBody);
            log.info("Organizer notification sent to {} for speaker response to event {}",
                    organizerEmail, invitation.getEventCode());

        } catch (Exception e) {
            log.error("Failed to send organizer notification for invitation {}: {}",
                    invitation.getId(), e.getMessage());
            // Don't throw - email failure shouldn't block response processing
        }
    }

    private String formatResponseType(String responseType) {
        return switch (responseType) {
            case "ACCEPTED" -> "Accepted";
            case "DECLINED" -> "Declined";
            case "TENTATIVE" -> "Needs More Info";
            default -> responseType;
        };
    }

    private boolean hasPreferences(SpeakerInvitation invitation) {
        return invitation.getPreferredTimeSlot() != null
                || invitation.getTravelRequirements() != null
                || invitation.getTechnicalRequirements() != null
                || invitation.getInitialPresentationTitle() != null
                || invitation.getCommentsForOrganizer() != null;
    }

    private String buildPreferencesSummary(SpeakerInvitation invitation) {
        if (!hasPreferences(invitation)) {
            return "No preferences specified";
        }

        StringBuilder sb = new StringBuilder();
        if (invitation.getPreferredTimeSlot() != null) {
            sb.append("Time Slot: ").append(invitation.getPreferredTimeSlot()).append("<br>");
        }
        if (invitation.getTravelRequirements() != null) {
            sb.append("Travel: ").append(invitation.getTravelRequirements()).append("<br>");
        }
        if (invitation.getTechnicalRequirements() != null && !invitation.getTechnicalRequirements().isBlank()) {
            sb.append("Technical: ").append(invitation.getTechnicalRequirements()).append("<br>");
        }
        if (invitation.getInitialPresentationTitle() != null) {
            sb.append("Proposed Title: ").append(invitation.getInitialPresentationTitle()).append("<br>");
        }
        if (invitation.getCommentsForOrganizer() != null) {
            sb.append("Comments: ").append(invitation.getCommentsForOrganizer());
        }
        return sb.toString();
    }

    private String buildOrganizerNotificationHtml(Map<String, String> variables) {
        String template = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #276749; color: white; padding: 20px; text-align: center; }
                    .header.declined { background: #c53030; }
                    .header.tentative { background: #d69e2e; }
                    .content { padding: 20px; }
                    .footer { padding: 20px; font-size: 12px; color: #666; border-top: 1px solid #eee; }
                    .info-box { background: #f7fafc; padding: 15px; border-left: 4px solid #3182ce; margin: 15px 0; }
                    .preferences { background: #f0fff4; padding: 15px; border-left: 4px solid #276749; margin: 15px 0; }
                    .decline-reason { background: #fff5f5; padding: 15px;
                                      border-left: 4px solid #c53030; margin: 15px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Speaker {{responseType}}</h1>
                    </div>
                    <div class="content">
                        <p><strong>{{speakerName}}</strong> has responded to the speaker invitation for
                        <strong>{{eventCode}}</strong>.</p>

                        <div class="info-box">
                            <p><strong>Response:</strong> {{responseType}}</p>
                        </div>

                        {{#hasDeclineReason}}
                        <div class="decline-reason">
                            <p><strong>Reason for declining:</strong></p>
                            <p>{{declineReason}}</p>
                        </div>
                        {{/hasDeclineReason}}

                        {{#hasNotes}}
                        <div class="info-box">
                            <p><strong>Notes from speaker:</strong></p>
                            <p>{{notes}}</p>
                        </div>
                        {{/hasNotes}}

                        {{#hasPreferences}}
                        <div class="preferences">
                            <p><strong>Speaker Preferences:</strong></p>
                            <p>{{preferencesSummary}}</p>
                        </div>
                        {{/hasPreferences}}

                        <p>Best regards,<br>
                        The BATbern Platform</p>
                    </div>
                    <div class="footer">
                        <p>Berner Architekten Treffen | <a href="https://batbern.ch">batbern.ch</a></p>
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
