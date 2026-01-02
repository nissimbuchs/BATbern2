package ch.batbern.events.notification;

import ch.batbern.events.domain.Event;
import ch.batbern.events.event.EventPublishedEvent;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Notification service
 * Story BAT-7: Notifications API Consolidation
 *
 * Implements hybrid storage strategy:
 * - Email/SMS notifications: Create audit trail records
 * - In-app notifications: Query dynamically (no rows created)
 *
 * Checks user preferences before sending
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final UserServiceClient userServiceClient;
    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Event listener for EventPublishedEvent
     * Automatically creates and sends notifications when an event is published
     */
    @EventListener
    @Async
    @Transactional
    public void onEventPublished(EventPublishedEvent event) {
        log.info("Received EventPublishedEvent for event: {}", event.getEventCode());

        // Find all registered attendees for this event
        List<String> attendees = registrationRepository.findUsernamesByEventCode(event.getEventCode());

        log.info("Found {} attendees for event {}", attendees.size(), event.getEventCode());

        // Send notification to each attendee
        for (String username : attendees) {
            try {
                createAndSendEmailNotification(NotificationRequest.builder()
                        .recipientUsername(username)
                        .eventCode(event.getEventCode())
                        .type("EVENT_PUBLISHED")
                        .channel("EMAIL")
                        .subject("Event " + event.getTitle() + " is now published!")
                        .body(buildEventPublishedEmailBody(event))
                        .priority("NORMAL")
                        .build());

                log.debug("Sent notification to {} for event {}", username, event.getEventCode());
            } catch (Exception e) {
                log.error("Failed to send notification to {} for event {}: {}",
                        username, event.getEventCode(), e.getMessage(), e);
            }
        }
    }

    /**
     * Create notification record and send via email
     * Creates audit trail record in database
     */
    @Transactional
    public void createAndSendEmailNotification(NotificationRequest request) {
        // Check user preferences
        UserPreferences prefs = userServiceClient.getPreferences(request.getRecipientUsername());

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
            // Fetch user email
            String email = userServiceClient.getEmailByUsername(request.getRecipientUsername());

            // Build HTML content (simple for now, can add Thymeleaf templates later)
            String htmlBody = buildEmailContent(notification);

            // Send via shared EmailService
            emailService.sendHtmlEmail(email, notification.getSubject(), htmlBody);

            // Update status after delivery
            notification.setStatus("SENT");
            notification.setSentAt(Instant.now());
            notificationRepository.save(notification);

            log.info("Notification sent successfully: {}", notification.getId());

        } catch (Exception e) {
            notification.setStatus("FAILED");
            notification.setFailedAt(Instant.now());
            notification.setFailureReason(e.getMessage());
            notificationRepository.save(notification);

            log.error("Failed to send notification: {}", notification.getId(), e);
        }
    }

    /**
     * Query in-app notifications dynamically (no rows created)
     * Finds events published since user's last login
     */
    public List<InAppNotification> getInAppNotifications(String username) {
        // Find events published since user's last login
        Instant lastLogin = userServiceClient.getLastLogin(username);

        List<Event> newEvents = eventRepository.findByPublishedAtAfter(lastLogin);

        // Convert to notifications on-the-fly (NO database writes)
        return newEvents.stream()
                .map(event -> InAppNotification.builder()
                        .type("EVENT_PUBLISHED")
                        .title(event.getTitle() + " is now published")
                        .eventCode(event.getEventCode())
                        .createdAt(event.getPublishedAt())
                        .build())
                .collect(Collectors.toList());
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
        if (isInQuietHours(prefs)) {
            return false;
        }

        return true;
    }

    /**
     * Check if current time is within user's quiet hours
     */
    private boolean isInQuietHours(UserPreferences prefs) {
        if (prefs.getQuietHoursStart() == null || prefs.getQuietHoursEnd() == null) {
            return false;
        }

        LocalTime now = LocalTime.now();
        LocalTime start = LocalTime.parse(prefs.getQuietHoursStart());
        LocalTime end = LocalTime.parse(prefs.getQuietHoursEnd());

        if (start.isBefore(end)) {
            // Normal range (e.g., 22:00-07:00 next day)
            return now.isAfter(start) || now.isBefore(end);
        } else {
            // Range crossing midnight
            return now.isAfter(start) && now.isBefore(end);
        }
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

    /**
     * Build email body for EventPublishedEvent
     */
    private String buildEventPublishedEmailBody(EventPublishedEvent event) {
        StringBuilder body = new StringBuilder();
        body.append("Great news! The event '").append(event.getTitle()).append("' has been published.\n\n");
        body.append("Event Code: ").append(event.getEventCode()).append("\n");
        body.append("Event Number: ").append(event.getEventNumber()).append("\n");

        if (event.getDate() != null) {
            body.append("Date: ").append(event.getDate()).append("\n");
        }

        body.append("\nYou can now view the full event details and agenda.\n");
        body.append("\nBest regards,\nBATbern Team");

        return body.toString();
    }

    /**
     * Push notification to user's WebSocket topic for real-time updates
     *
     * Sends notification to: /topic/notifications/{username}
     * Frontend subscribes to this topic to receive live updates
     */
    public void pushNotificationViaWebSocket(Notification notification) {
        try {
            String destination = "/topic/notifications/" + notification.getRecipientUsername();

            NotificationResponse response = NotificationResponse.fromEntity(notification);

            messagingTemplate.convertAndSend(destination, response);

            log.debug("Pushed notification via WebSocket to {}: {}",
                    destination, notification.getNotificationType());

        } catch (Exception e) {
            log.error("Failed to push notification via WebSocket for user {}: {}",
                    notification.getRecipientUsername(), e.getMessage());
            // Don't fail the notification if WebSocket push fails
        }
    }
}
