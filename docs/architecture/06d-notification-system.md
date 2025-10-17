# Real-time Notification & Escalation System

This document outlines the notification and escalation system for the BATbern Event Management Platform, including real-time notifications, deadline warnings, and automatic escalation logic.

## Overview

The notification system provides multi-channel communication (email, WebSocket) with intelligent escalation based on workflow state, user preferences, and deadline proximity.

## Workflow Notification Service

```java
@Service
@Slf4j
public class WorkflowNotificationService {

    private final EmailService emailService;
    private final WebSocketNotificationService webSocketService;
    private final NotificationPreferencesService preferencesService;

    public void sendWorkflowNotification(WorkflowNotificationEvent event) {
        List<String> recipients = determineRecipients(event);

        for (String userId : recipients) {
            NotificationPreferences prefs = preferencesService.getPreferences(userId);

            if (prefs.isEmailEnabled()) {
                emailService.sendWorkflowNotification(userId, event);
            }

            if (prefs.isRealTimeEnabled()) {
                webSocketService.sendNotification(userId, event);
            }
        }

        // Check for escalation requirements
        checkEscalationNeeded(event);
    }

    private void checkEscalationNeeded(WorkflowNotificationEvent event) {
        if (event.getType() == NotificationType.DEADLINE_WARNING) {
            Duration timeUntilDeadline = Duration.between(Instant.now(), event.getDeadline());

            if (timeUntilDeadline.toDays() <= 3) {
                // Escalate to all organizers
                escalateToOrganizers(event);
            }
        }
    }

    private void escalateToOrganizers(WorkflowNotificationEvent event) {
        List<String> organizers = userService.getOrganizerIds();

        EscalationNotification escalation = EscalationNotification.builder()
            .originalEvent(event)
            .escalatedAt(Instant.now())
            .escalationLevel(EscalationLevel.ORGANIZER_TEAM)
            .urgency(UrgencyLevel.HIGH)
            .build();

        for (String organizerId : organizers) {
            emailService.sendEscalationNotification(organizerId, escalation);
            webSocketService.sendEscalationAlert(organizerId, escalation);
        }
    }
}
```

## Notification Types

### WorkflowNotificationEvent

```java
@Data
@Builder
public class WorkflowNotificationEvent {
    private String eventId;
    private NotificationType type;
    private String title;
    private String message;
    private Instant deadline;
    private Map<String, Object> metadata;
    private List<String> recipientIds;
    private NotificationPriority priority;
}

public enum NotificationType {
    SPEAKER_INVITED,
    SPEAKER_ACCEPTED,
    SPEAKER_DECLINED,
    CONTENT_SUBMITTED,
    QUALITY_REVIEW_PENDING,
    QUALITY_REVIEW_APPROVED,
    QUALITY_REVIEW_REQUIRES_CHANGES,
    SLOT_ASSIGNED,
    DEADLINE_WARNING,
    OVERFLOW_DETECTED,
    VOTING_REQUIRED,
    EVENT_PUBLISHED
}

public enum NotificationPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}
```

## Escalation Configuration

### Escalation Rules

```java
@Component
public class EscalationRuleEngine {

    public boolean shouldEscalate(WorkflowNotificationEvent event) {
        return switch (event.getType()) {
            case DEADLINE_WARNING -> checkDeadlineProximity(event.getDeadline());
            case QUALITY_REVIEW_PENDING -> checkReviewAge(event);
            case VOTING_REQUIRED -> checkVotingStagnation(event);
            default -> false;
        };
    }

    private boolean checkDeadlineProximity(Instant deadline) {
        Duration timeUntilDeadline = Duration.between(Instant.now(), deadline);
        return timeUntilDeadline.toDays() <= 3;
    }

    private boolean checkReviewAge(WorkflowNotificationEvent event) {
        Instant submittedAt = (Instant) event.getMetadata().get("submittedAt");
        Duration age = Duration.between(submittedAt, Instant.now());
        return age.toDays() >= 7; // Review pending for 7+ days
    }

    private boolean checkVotingStagnation(WorkflowNotificationEvent event) {
        Integer totalOrganizers = (Integer) event.getMetadata().get("totalOrganizers");
        Integer votesReceived = (Integer) event.getMetadata().get("votesReceived");
        Instant votingStarted = (Instant) event.getMetadata().get("votingStartedAt");

        Duration votingAge = Duration.between(votingStarted, Instant.now());

        // Escalate if less than 50% voted after 3 days
        return votingAge.toDays() >= 3 && votesReceived < (totalOrganizers / 2);
    }
}
```

### Escalation Levels

```java
public enum EscalationLevel {
    TEAM_LEAD,        // Escalate to team lead
    ORGANIZER_TEAM,   // Escalate to all organizers
    SYSTEM_ADMIN      // Escalate to system administrators
}

@Data
@Builder
public class EscalationNotification {
    private WorkflowNotificationEvent originalEvent;
    private Instant escalatedAt;
    private EscalationLevel escalationLevel;
    private UrgencyLevel urgency;
    private String escalationReason;
    private List<String> escalatedTo;
}
```

## User Notification Preferences

### Preferences Model

```java
@Entity
@Table(name = "notification_preferences")
@Data
@Builder
public class NotificationPreferences {

    @Id
    private UUID userId;

    private boolean emailEnabled = true;
    private boolean realTimeEnabled = true;
    private boolean smsEnabled = false;

    // Notification type preferences
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<NotificationType, ChannelPreference> typePreferences;

    @Data
    @Builder
    public static class ChannelPreference {
        private boolean email;
        private boolean realTime;
        private boolean sms;
    }
}
```

### Preferences Service

```java
@Service
@RequiredArgsConstructor
public class NotificationPreferencesService {

    private final NotificationPreferencesRepository preferencesRepository;

    public NotificationPreferences getPreferences(String userId) {
        return preferencesRepository.findById(UUID.fromString(userId))
            .orElse(createDefaultPreferences(userId));
    }

    private NotificationPreferences createDefaultPreferences(String userId) {
        return NotificationPreferences.builder()
            .userId(UUID.fromString(userId))
            .emailEnabled(true)
            .realTimeEnabled(true)
            .smsEnabled(false)
            .typePreferences(Map.of(
                NotificationType.SPEAKER_INVITED, ChannelPreference.builder()
                    .email(true)
                    .realTime(true)
                    .sms(false)
                    .build(),
                NotificationType.DEADLINE_WARNING, ChannelPreference.builder()
                    .email(true)
                    .realTime(true)
                    .sms(true)
                    .build()
            ))
            .build();
    }
}
```

## WebSocket Integration

### WebSocket Configuration

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins("*")
            .withSockJS();
    }
}
```

### WebSocket Notification Service

```java
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendNotification(String userId, WorkflowNotificationEvent event) {
        String destination = "/user/" + userId + "/queue/notifications";

        NotificationMessage message = NotificationMessage.builder()
            .id(UUID.randomUUID().toString())
            .type(event.getType().name())
            .title(event.getTitle())
            .message(event.getMessage())
            .priority(event.getPriority().name())
            .timestamp(Instant.now())
            .metadata(event.getMetadata())
            .build();

        messagingTemplate.convertAndSend(destination, message);

        log.info("WebSocket notification sent to user {}: {}", userId, event.getType());
    }

    public void sendEscalationAlert(String userId, EscalationNotification escalation) {
        String destination = "/user/" + userId + "/queue/escalations";

        messagingTemplate.convertAndSend(destination, escalation);

        log.warn("Escalation alert sent to user {}: {}", userId, escalation.getEscalationLevel());
    }
}
```

### Notification Message Format

```java
@Data
@Builder
public class NotificationMessage {
    private String id;
    private String type;
    private String title;
    private String message;
    private String priority;
    private Instant timestamp;
    private Map<String, Object> metadata;
    private boolean read;
    private String actionUrl;
}
```

## Email Notification Templates

### Email Service

```java
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public void sendWorkflowNotification(String userId, WorkflowNotificationEvent event) {
        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        String subject = buildSubject(event);
        String htmlContent = buildEmailContent(event, user);

        sendEmail(user.getEmail(), subject, htmlContent);
    }

    public void sendEscalationNotification(String userId, EscalationNotification escalation) {
        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        String subject = "URGENT: " + escalation.getOriginalEvent().getTitle();
        String htmlContent = buildEscalationEmailContent(escalation, user);

        sendEmail(user.getEmail(), subject, htmlContent);
    }

    private void sendEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom("notifications@batbern.ch");

            mailSender.send(message);

            log.info("Email notification sent to {}: {}", to, subject);

        } catch (Exception e) {
            log.error("Failed to send email notification to {}", to, e);
        }
    }

    private String buildEmailContent(WorkflowNotificationEvent event, User user) {
        Context context = new Context();
        context.setVariable("user", user);
        context.setVariable("event", event);

        return templateEngine.process("notifications/" + event.getType().name().toLowerCase(), context);
    }
}
```

## Notification Persistence

### Notification History

```java
@Entity
@Table(name = "notification_history")
@Data
@Builder
public class NotificationHistory {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;
    private String eventId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String title;
    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    private boolean delivered;
    private Instant sentAt;
    private Instant deliveredAt;
    private Instant readAt;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}

public enum NotificationChannel {
    EMAIL,
    WEBSOCKET,
    SMS
}
```

## Scheduled Notification Jobs

### Deadline Reminder Job

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class DeadlineReminderJob {

    private final EventRepository eventRepository;
    private final WorkflowNotificationService notificationService;

    @Scheduled(cron = "0 0 9 * * *") // 9 AM daily
    public void sendDeadlineReminders() {
        log.info("Starting deadline reminder job");

        Instant now = Instant.now();
        Instant threeDaysFromNow = now.plus(Duration.ofDays(3));

        // Find events with upcoming deadlines
        List<Event> upcomingDeadlines = eventRepository
            .findByRegistrationDeadlineBetween(now, threeDaysFromNow);

        for (Event event : upcomingDeadlines) {
            Duration timeUntilDeadline = Duration.between(now, event.getRegistrationDeadline());

            WorkflowNotificationEvent notification = WorkflowNotificationEvent.builder()
                .eventId(event.getId().toString())
                .type(NotificationType.DEADLINE_WARNING)
                .title("Registration Deadline Approaching")
                .message(String.format("Registration for %s closes in %d days",
                    event.getTitle(), timeUntilDeadline.toDays()))
                .deadline(event.getRegistrationDeadline())
                .priority(NotificationPriority.HIGH)
                .build();

            notificationService.sendWorkflowNotification(notification);
        }

        log.info("Deadline reminder job completed: {} reminders sent", upcomingDeadlines.size());
    }
}
```

## Related Documentation

- [Backend Architecture Overview](./06-backend-architecture.md)
- [Workflow State Machines](./06a-workflow-state-machines.md)
- [User Lifecycle Sync Patterns](./06b-user-lifecycle-sync.md)
