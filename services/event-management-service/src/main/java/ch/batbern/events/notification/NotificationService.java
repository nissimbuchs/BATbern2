package ch.batbern.events.notification;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Notification service
 * Story BAT-7: Notifications API Consolidation
 *
 * Implements hybrid storage strategy:
 * - Email/SMS notifications: Create audit trail records
 *
 * Checks user preferences before sending
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Zurich");

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final UserApiClient userApiClient;
    private final Clock clock;

    /**
     * Create notification record and send via email
     * Creates audit trail record in database
     */
    @Transactional
    public void createAndSendEmailNotification(NotificationRequest request) {
        // Check user preferences
        UserPreferences prefs = userApiClient.getPreferences(request.getRecipientUsername());

        if (!shouldSend(prefs, request)) {
            log.info("Skipping notification for {} due to user preferences", request.getRecipientUsername());
            return;
        }

        // Create notification record (audit trail)
        Notification notification = notificationRepository.save(Notification.builder()
                .recipientUsername(request.getRecipientUsername())
                .eventCode(request.getEventCode())
                .notificationType(request.getType())
                .channel(request.getChannel())
                .priority(request.getPriority() != null ? request.getPriority() : "NORMAL")
                .subject(request.getSubject())
                .body(request.getBody())
                .status("PENDING")
                .metadata(request.getMetadata())
                .build());

        // Send via AWS SES using shared-kernel EmailService
        try {
            // Fetch user email from the user entity (the dedicated /email endpoint never existed;
            // the entity endpoint is the canonical, VPC-gated read — Story 15.7 follow-up).
            String email = userApiClient.getUserByUsername(request.getRecipientUsername()).getEmail();

            // Build HTML content (simple for now, can add Thymeleaf templates later)
            String htmlBody = buildEmailContent(notification);

            // Synchronous: the status below says whether SES took the mail. The @Async send it
            // used before returned at once, so a refused mail was recorded as SENT.
            emailService.sendHtmlEmailSync(mailContext(request), email, null, notification.getSubject(), htmlBody,
                    null);

            // Update status after delivery
            notification.setStatus("SENT");
            notification.setSentAt(clock.instant());
            notificationRepository.save(notification);

            log.info("Notification sent successfully: {}", notification.getId());

        } catch (Exception e) {
            notification.setStatus("FAILED");
            notification.setFailedAt(clock.instant());
            notification.setFailureReason(e.getMessage());
            notificationRepository.save(notification);

            log.error("Failed to send notification: {}", notification.getId(), e);
        }
    }

    /**
     * Check if notification should be sent based on user preferences
     */
    private boolean shouldSend(UserPreferences prefs, NotificationRequest request) {
        // Check if channel is enabled
        if ("EMAIL".equals(request.getChannel()) && !prefs.isEmailNotificationsEnabled()) {
            return false;
        }

        // Check quiet hours
        if (isInQuietHours(prefs, clock.instant())) {
            return false;
        }

        return true;
    }

    /**
     * Whether {@code now} falls in the recipient's quiet hours, read on the wall clock of their
     * time zone (settings {@code timezone}, Zurich when unset or unknown; the server runs on UTC).
     * The window includes its start and excludes its end; a start after the end spans midnight
     * (22:00-07:00). No window, or start equal to end, means never quiet (#1222).
     */
    static boolean isInQuietHours(UserPreferences prefs, Instant now) {
        if (prefs.getQuietHoursStart() == null || prefs.getQuietHoursEnd() == null
                || prefs.getQuietHoursStart().isBlank() || prefs.getQuietHoursEnd().isBlank()) {
            return false;
        }
        LocalTime start;
        LocalTime end;
        try {
            start = LocalTime.parse(prefs.getQuietHoursStart());
            end = LocalTime.parse(prefs.getQuietHoursEnd());
        } catch (DateTimeParseException e) {
            log.warn("Quiet hours not understood ({}-{}); sending", prefs.getQuietHoursStart(),
                    prefs.getQuietHoursEnd());
            return false;
        }
        LocalTime local = now.atZone(zoneOf(prefs.getTimezone())).toLocalTime();
        if (start.isBefore(end)) {
            // Same-day window, e.g. 09:00-17:00
            return !local.isBefore(start) && local.isBefore(end);
        }
        if (start.isAfter(end)) {
            // Window across midnight, e.g. 22:00-07:00
            return !local.isBefore(start) || local.isBefore(end);
        }
        return false;
    }

    private static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            return DEFAULT_ZONE;
        }
    }

    /**
     * Story 16.1a: speaker escalations go to organizers, everything else here to registrants. One
     * send group per trigger (a publication, a job run) and notification type.
     */
    static MailContext mailContext(NotificationRequest request) {
        MailTrigger trigger = request.getMailTrigger() != null ? request.getMailTrigger() : MailTrigger.system();
        MailContext.Audience audience = "SPEAKER_ESCALATION".equals(request.getType())
                ? MailContext.Audience.ORGANIZERS
                : MailContext.Audience.REGISTRANTS;
        String templateKey = "notification-" + String.valueOf(request.getType()).toLowerCase(Locale.ROOT);
        return MailContext.of(trigger, request.getEventCode(), audience, templateKey);
    }

    /**
     * Build HTML email content from notification
     * TODO: Add Thymeleaf templates for richer formatting
     */
    private String buildEmailContent(Notification notification) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h2>").append(notification.getSubject()).append("</h2>");
        html.append("<p>").append(notification.getBody()).append("</p>");

        if (notification.getEventCode() != null) {
            html.append("<p><strong>Event:</strong> ").append(notification.getEventCode()).append("</p>");
        }

        html.append("<hr>");
        html.append("<p><small>BATbern Notification System</small></p>");
        html.append("</body></html>");

        return html.toString();
    }
}
