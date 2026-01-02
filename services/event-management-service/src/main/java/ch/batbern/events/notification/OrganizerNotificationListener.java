package ch.batbern.events.notification;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.shared.events.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Organizer Notification Listener
 * Story BAT-7: Notifications API Consolidation
 *
 * Listens to ALL domain events and creates in-app notifications for all organizers.
 * This ensures organizers stay informed about all system activity.
 *
 * Key Features:
 * - Listens to base DomainEvent class (catches all events)
 * - Creates IN_APP notifications (no email sending)
 * - Notifies all users with ORGANIZER role
 * - Async processing to avoid blocking event publishers
 * - Real-time WebSocket push for instant updates
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrganizerNotificationListener {

    private final NotificationRepository notificationRepository;
    private final UserApiClient userApiClient;
    private final NotificationService notificationService;

    /**
     * Listen to ALL domain events and create in-app notifications for organizers
     */
    @EventListener
    @Async
    @Transactional
    public void onAnyDomainEvent(DomainEvent<?> event) {
        try {
            log.debug("Received domain event: {} (type: {})", event.getEventId(), event.getEventType());

            // Fetch all organizers
            List<String> organizers;
            try {
                organizers = userApiClient.getOrganizerUsernames();
            } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
                // No JWT token available (async system event without user context)
                // This is expected for system-triggered events (scheduled tasks, etc.)
                log.warn("Cannot fetch organizer list for system event {} - no authentication context available. " +
                        "Organizer notifications will be skipped for this event. " +
                        "Consider implementing service account authentication for background tasks.",
                        event.getEventType());
                return;
            } catch (Exception e) {
                log.error("Failed to fetch organizer list for event {}: {}",
                        event.getEventType(), e.getMessage());
                return;
            }

            if (organizers.isEmpty()) {
                log.debug("No organizers found, skipping notification for event: {}", event.getEventType());
                return;
            }

            log.info("Creating in-app notifications for {} organizers for event: {}",
                    organizers.size(), event.getEventType());

            // Create in-app notification for each organizer
            for (String organizerUsername : organizers) {
                try {
                    createInAppNotification(event, organizerUsername);
                    log.debug("Created in-app notification for organizer: {} (event: {})",
                            organizerUsername, event.getEventType());
                } catch (Exception e) {
                    log.error("Failed to create in-app notification for organizer {} (event: {}): {}",
                            organizerUsername, event.getEventType(), e.getMessage());
                    // Continue with other organizers even if one fails
                }
            }

        } catch (Exception e) {
            log.error("Failed to process domain event {} for organizer notifications: {}",
                    event.getEventType(), e.getMessage(), e);
        }
    }

    /**
     * Create in-app notification record (no email sending)
     */
    private void createInAppNotification(DomainEvent<?> event, String organizerUsername) {
        // Build notification metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("eventId", event.getEventId());
        metadata.put("eventType", event.getEventType());
        metadata.put("aggregateId", event.getAggregateId() != null ? event.getAggregateId().toString() : null);
        metadata.put("userId", event.getUserId());
        metadata.put("occurredAt", event.getOccurredAt());

        // Create notification record
        Notification notification = Notification.builder()
                .recipientUsername(organizerUsername)
                .eventCode(extractEventCode(event))  // Extract event code if available
                .notificationType(event.getEventType())
                .channel("IN_APP")  // In-app only, no email
                .priority(determinePriority(event.getEventType()))
                .subject(buildSubject(event))
                .body(buildBody(event))
                .status("UNREAD")  // In-app notifications start as UNREAD
                .metadata(metadata)
                .build();

        notification = notificationRepository.save(notification);

        // Push to organizer's WebSocket topic for real-time update
        notificationService.pushNotificationViaWebSocket(notification);
    }

    /**
     * Extract event code from domain event if available
     */
    private String extractEventCode(DomainEvent<?> event) {
        // Try to extract eventCode from metadata or reflection
        // For now, return null (can be enhanced later with specific event handling)
        return null;
    }

    /**
     * Determine priority based on event type
     */
    private String determinePriority(String eventType) {
        if (eventType == null) {
            return "NORMAL";
        }

        // High priority events
        if (eventType.contains("Cancelled") || eventType.contains("Failed") || eventType.contains("Error")) {
            return "HIGH";
        }

        // Urgent priority events
        if (eventType.contains("Deadline") || eventType.contains("Urgent")) {
            return "URGENT";
        }

        // Low priority events
        if (eventType.contains("Updated") || eventType.contains("Modified")) {
            return "LOW";
        }

        return "NORMAL";
    }

    /**
     * Build notification subject from domain event
     */
    private String buildSubject(DomainEvent<?> event) {
        String eventName = event.getEventName();
        String aggregateType = event.getAggregateType();

        return String.format("%s: %s", aggregateType, formatEventName(eventName));
    }

    /**
     * Build notification body from domain event
     */
    private String buildBody(DomainEvent<?> event) {
        StringBuilder body = new StringBuilder();

        body.append("Event: ").append(formatEventName(event.getEventName())).append("\n");
        body.append("Type: ").append(event.getEventType()).append("\n");

        if (event.getUserId() != null) {
            body.append("Triggered by: ").append(event.getUserId()).append("\n");
        }

        body.append("Occurred at: ").append(event.getOccurredAt()).append("\n");

        if (event.getAggregateId() != null) {
            body.append("Aggregate ID: ").append(event.getAggregateId()).append("\n");
        }

        return body.toString();
    }

    /**
     * Format event name for display (e.g., "EventPublishedEvent" -> "Event Published")
     */
    private String formatEventName(String eventName) {
        if (eventName == null) {
            return "Unknown Event";
        }

        // Remove "Event" suffix
        if (eventName.endsWith("Event")) {
            eventName = eventName.substring(0, eventName.length() - 5);
        }

        // Add spaces before capitals
        return eventName.replaceAll("([A-Z])", " $1").trim();
    }
}
