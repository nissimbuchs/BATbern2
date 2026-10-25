package ch.batbern.events.notification;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.events.DomainEvent;
import ch.batbern.shared.events.EventWorkflowTransitionEvent;
import ch.batbern.shared.events.SpeakerWorkflowStateChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private final EventRepository eventRepository;

    /**
     * Listen to ALL domain events and create in-app notifications for organizers
     */
    @EventListener
    @Async
    @Transactional
    public void onAnyDomainEvent(DomainEvent<?> event) {
        try {
            log.debug("Received domain event: {} (type: {})", event.getEventId(), event.getEventType());

            // This listener is @Async: when the event was deleted in the meantime (E2E/Bruno
            // create and delete within seconds), its notifications are already gone and a late
            // one would linger forever (#1068).
            String eventCode = extractEventCode(event);
            if (eventCode != null && !eventRepository.existsByEventCode(eventCode)) {
                log.debug("Skipping notification for {}: event {} no longer exists",
                        event.getEventType(), eventCode);
                return;
            }

            // Fetch all organizers
            List<String> organizers;
            try {
                organizers = userApiClient.getOrganizerUsernames();
            } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
                // No JWT token available (async system event without user context)
                // This is expected for system-triggered events (scheduled tasks, etc.)
                log.warn("Cannot fetch organizer list for system event {} - no authentication context available. "
                        + "Organizer notifications will be skipped for this event. "
                        + "Consider implementing service account authentication for background tasks.",
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
        // Extract event code first
        String eventCode = extractEventCode(event);

        // Build notification metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("eventId", event.getEventId());
        metadata.put("eventType", event.getEventType());
        metadata.put("aggregateId", event.getAggregateId() != null ? event.getAggregateId().toString() : null);
        metadata.put("userId", event.getUserId());
        metadata.put("occurredAt", event.getOccurredAt());
        if (eventCode != null) {
            metadata.put("eventCode", eventCode);
        }

        // Create notification record
        Notification notification = Notification.builder()
                .recipientUsername(organizerUsername)
                .eventCode(eventCode)  // Event code for filtering
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
     * Uses reflection to access eventCode field from specific event types
     */
    private String extractEventCode(DomainEvent<?> event) {
        // Try to extract eventCode from metadata first
        if (event.getMetadata() != null && event.getMetadata().containsKey("eventCode")) {
            return event.getMetadata().get("eventCode");
        }

        // Use reflection to extract eventCode from specific event types
        // (EventCreatedEvent, EventPublishedEvent, etc. all have eventCode field)
        try {
            java.lang.reflect.Field eventCodeField = event.getClass().getDeclaredField("eventCode");
            eventCodeField.setAccessible(true);
            Object eventCodeValue = eventCodeField.get(event);
            if (eventCodeValue instanceof String) {
                return (String) eventCodeValue;
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Event doesn't have eventCode field (e.g., non-event domain events)
            log.trace("Event {} doesn't have eventCode field", event.getEventType());
        }

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
        String formattedEventName = formatEventName(eventName);

        // Get event code for context
        String eventCode = extractEventCode(event);
        if (eventCode != null) {
            return String.format("%s - %s", eventCode, formattedEventName);
        }

        return formattedEventName;
    }

    /**
     * Build notification body from domain event
     */
    private String buildBody(DomainEvent<?> event) {
        // Note: DomainEvent.getUserId() returns username (misleading name)
        // All events pass username to parent constructor
        String username = event.getUserId() != null && !event.getUserId().isEmpty()
            ? event.getUserId()
            : "System";

        // Build user-friendly message based on event type
        String eventType = event.getEventType();
        String eventCode = extractEventCode(event);

        // Every message names who caused it (#1068); transitions also say where to.
        if (event instanceof EventWorkflowTransitionEvent transition) {
            return String.format("%s moved %s to %s",
                username, transition.getEventCode(), formatState(transition.getToState().name()));
        } else if (event instanceof SpeakerWorkflowStateChangeEvent change) {
            return String.format("%s moved a speaker%s from %s to %s",
                username,
                eventCode != null ? " of " + eventCode : "",
                formatState(change.getFromState().name()),
                formatState(change.getToState().name()));
        } else if (eventType.contains("Published")) {
            return String.format("%s published%s",
                username,
                eventCode != null ? " event " + eventCode : " an event");
        } else if (eventType.contains("Created")) {
            return String.format("%s created%s",
                username,
                eventCode != null ? " event " + eventCode : " a new event");
        } else if (eventType.contains("Updated")) {
            return String.format("%s updated%s",
                username,
                eventCode != null ? " event " + eventCode : " an event");
        } else if (eventType.contains("Deleted")) {
            return String.format("%s deleted%s",
                username,
                eventCode != null ? " event " + eventCode : " an event");
        } else {
            // Generic fallback
            return String.format("%s - %s",
                username,
                formatEventName(event.getEventName()));
        }
    }

    /**
     * Format an enum state for display (e.g., "AGENDA_PUBLISHED" -> "Agenda Published")
     */
    private String formatState(String state) {
        StringBuilder sb = new StringBuilder();
        for (String word : state.toLowerCase(Locale.ROOT).split("_")) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return sb.toString();
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
