# Notification & Delivery System

This document outlines the notification and delivery system for the BATbern Event Management Platform, including email notifications, delivery tracking, and automatic escalation logic.

## Overview

The notification system provides multi-channel communication (email, SMS) with delivery tracking and intelligent escalation based on workflow state, user preferences, and deadline proximity.

**Implementation Pattern**: Single table in shared `public` schema, following Task System pattern (Story 5.5).

**Key Characteristics**:
- ✅ Single `notifications` table owned by Event Management Service
- ✅ Hybrid storage: email audit trail (create rows), in-app queries (no rows)
- ✅ ADR-003 compliant (meaningful IDs: `recipient_username`, `event_code`)
- ✅ No cross-service aggregation (simple REST API queries)

## Database Schema

**Location**: `services/event-management-service/src/main/resources/db/migration/V33__Create_notifications_table.sql`

```sql
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- ADR-003: Meaningful IDs (NOT foreign keys)
    recipient_username VARCHAR(100) NOT NULL,
    event_code VARCHAR(50),  -- Nullable (non-event notifications)

    -- Notification details
    notification_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    priority VARCHAR(20) DEFAULT 'NORMAL',
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,

    -- Delivery tracking
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMP,
    read_at TIMESTAMP,
    failed_at TIMESTAMP,
    failure_reason TEXT,

    -- Metadata
    metadata JSONB,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_notifications_recipient ON notifications(recipient_username);
CREATE INDEX idx_notifications_event ON notifications(event_code) WHERE event_code IS NOT NULL;
CREATE INDEX idx_notifications_recipient_status ON notifications(recipient_username, status);
```

**Ownership**: Event Management Service (by convention, follows Task System pattern)

## Notification Service

### Hybrid Storage Strategy

**Email/SMS Notifications** (Eager - Create Records):
- Create notification record when email/SMS sent
- Track delivery status (audit trail, compliance)
- Storage: ~150,000 rows/year (3000 users × 50 emails/year)

**In-App Notifications** (Lazy - Query Dynamically):
- Don't create rows in advance
- Query when user opens app: "Show events published since last login"
- Storage: Zero rows for in-app (computed dynamically)

### Implementation

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final UserApiClient userApiClient;
    private final EventRepository eventRepository;

    /**
     * Send email notification (creates audit trail record)
     */
    @EventListener
    @Async
    public void onEventPublished(EventPublishedEvent event) {
        // Get all registered attendees
        List<String> attendees = registrationRepository
            .findUsernamesByEventId(event.getEventId());

        // Create email notifications (audit trail)
        for (String username : attendees) {
            createAndSendEmailNotification(NotificationRequest.builder()
                .recipientUsername(username)
                .eventCode(event.getEventCode())
                .type("EVENT_PUBLISHED")
                .channel("EMAIL")
                .subject("Event " + event.getTitle() + " is now published")
                .body(buildEmailBody(event))
                .build());
        }
    }

    /**
     * Create notification record and send via email
     */
    @Transactional
    public void createAndSendEmailNotification(NotificationRequest request) {
        // Check user preferences (via HTTP call to User Service)
        UserPreferences prefs = userApiClient
            .getPreferences(request.getRecipientUsername());

        if (!shouldSend(prefs, request)) {
            return;  // Skip if user opted out or in quiet hours
        }

        // Create notification record (audit trail)
        Notification notification = notificationRepository.save(Notification.builder()
            .recipientUsername(request.getRecipientUsername())
            .eventCode(request.getEventCode())
            .notificationType(request.getType())
            .channel("EMAIL")
            .subject(request.getSubject())
            .body(request.getBody())
            .status("PENDING")
            .build());

        // Send via AWS SES
        try {
            String recipientEmail = userApiClient.getEmailByUsername(notification.getRecipientUsername());
            String htmlContent = buildEmailContent(notification);
            emailService.sendHtmlEmail(recipientEmail, notification.getSubject(), htmlContent);

            // Update status after delivery
            notification.setStatus("SENT");
            notification.setSentAt(Instant.now());
            notificationRepository.save(notification);

        } catch (Exception e) {
            notification.setStatus("FAILED");
            notification.setFailedAt(Instant.now());
            notification.setFailureReason(e.getMessage());
            notificationRepository.save(notification);
            log.error("Failed to send email notification", e);
        }
    }

    /**
     * Query in-app notifications dynamically (no rows created)
     */
    public List<InAppNotification> getInAppNotifications(String username) {
        // Find events published since user's last login
        Instant lastLogin = userApiClient.getLastLogin(username);

        List<Event> newEvents = eventRepository.findByPublishedAtAfter(lastLogin);

        // Convert to notifications on-the-fly
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
     * Check for escalation requirements
     */
    private void checkEscalationNeeded(WorkflowNotificationEvent event) {
        if (event.getType() == NotificationType.DEADLINE_WARNING) {
            Duration timeUntilDeadline = Duration.between(Instant.now(), event.getDeadline());

            if (timeUntilDeadline.toDays() <= 3) {
                escalateToOrganizers(event);
            }
        }
    }

    /**
     * Escalate to all organizers
     */
    private void escalateToOrganizers(WorkflowNotificationEvent event) {
        List<String> organizerUsernames = userServiceClient.getOrganizerUsernames();

        for (String username : organizerUsernames) {
            createAndSendEmailNotification(NotificationRequest.builder()
                .recipientUsername(username)
                .eventCode(event.getEventCode())
                .type("DEADLINE_WARNING")
                .channel("EMAIL")
                .priority("URGENT")
                .subject("URGENT: " + event.getTitle())
                .body(buildEscalationEmailBody(event))
                .build());
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
    DEADLINE_WARNING,       // Used by DeadlineReminderJob for registration deadline reminders
    DEADLINE_REMINDER,      // Used in NotificationControllerIntegrationTest (VARCHAR column allows this value)
    OVERFLOW_DETECTED,
    VOTING_REQUIRED,
    EVENT_PUBLISHED
    // Note: TASK_DEADLINE_WARNING is NOT written to the notifications table —
    // TaskDeadlineReminderScheduler delegates directly to TaskReminderEmailService, bypassing this system.
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

**Storage**: Preferences stored in existing `user_profiles.user_preferences` JSONB column (Company-User Management Service)

**Fields** (already exist):
- `emailNotifications` (boolean)
- `inAppNotifications` (boolean)
- `pushNotifications` (boolean)
- `notificationFrequency` (string: "realtime", "daily_digest", "weekly_digest")
- `quietHoursStart` (string: "22:00")
- `quietHoursEnd` (string: "07:00")

### Preferences Access

```java
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserApiClient userApiClient;

    /**
     * Check if notification should be sent based on user preferences
     */
    private boolean shouldSend(String username, NotificationRequest request) {
        // Fetch user preferences via HTTP (Company-User Management Service)
        UserPreferences prefs = userApiClient.getPreferences(username);

        // Check if channel is enabled
        if (request.getChannel().equals("EMAIL") && !prefs.isEmailNotificationsEnabled()) {
            return false;
        }

        // Check quiet hours
        if (isInQuietHours(prefs)) {
            return false;
        }

        return true;
    }

    private boolean isInQuietHours(UserPreferences prefs) {
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
}
```

### UserApiClient (HTTP Integration)

```java
@Component
@RequiredArgsConstructor
public class UserApiClient {

    private final RestTemplate restTemplate;

    /**
     * Fetch user preferences from Company-User Management Service
     */
    public UserPreferences getPreferences(String username) {
        String url = "http://company-user-management-service:8081/api/v1/users/{username}/preferences";

        return restTemplate.getForObject(url, UserPreferences.class, username);
    }

    public Instant getLastLogin(String username) {
        String url = "http://company-user-management-service:8081/api/v1/users/{username}/last-login";

        return restTemplate.getForObject(url, Instant.class, username);
    }

    public List<String> getOrganizerUsernames() {
        String url = "http://company-user-management-service:8081/api/v1/users?role=ORGANIZER";

        UserListResponse response = restTemplate.getForObject(url, UserListResponse.class);
        return response.getUsers().stream()
            .map(User::getUsername)
            .collect(Collectors.toList());
    }
}
```

## Real-Time Notifications (Partial — WebSocket Infrastructure Wired)

**Status**: `SimpMessagingTemplate` (`org.springframework.messaging.simp.SimpMessagingTemplate`) is already injected into the `NotificationService` constructor, indicating WebSocket infrastructure is in place. Full real-time push to connected clients is not yet exercised in production flows and remains a future story.

**Planned Approach**: WebSocket integration using Spring WebSocket + STOMP for real-time in-app notifications

**Implementation Notes**:
- Email/SMS notifications provide baseline coverage
- In-app notifications queried dynamically on page load (acceptable UX for MVP)
- Real-time WebSocket can be added later without changing database schema
- Would use same `notifications` table for persistence
- Frontend would receive live updates when notification created

**Future Story**: Will implement WebSocket server, connection management, and React hooks for real-time updates

## Email Notification Templates

### Email Service (AWS SES Integration)

**Infrastructure**: AWS SES for email delivery

**Templates**: Thymeleaf templates in `resources/templates/notifications/`

```java
@Service
@RequiredArgsConstructor
public class EmailService {

    private final AmazonSimpleEmailService sesClient;
    private final TemplateEngine templateEngine;

    /**
     * Send HTML email via AWS SES.
     * Called by NotificationService after creating the audit trail record.
     *
     * @param recipientEmail resolved email address of the recipient
     * @param subject        email subject line
     * @param htmlContent    rendered HTML body
     */
    public void sendHtmlEmail(String recipientEmail, String subject, String htmlContent) {
        // Send via AWS SES
        SendEmailRequest request = new SendEmailRequest()
            .withDestination(new Destination().withToAddresses(recipientEmail))
            .withMessage(new Message()
                .withSubject(new Content().withData(subject))
                .withBody(new Body().withHtml(new Content().withData(htmlContent))))
            .withSource("notifications@batbern.ch");

        sesClient.sendEmail(request);

        log.info("Email sent to {}: {}", recipientEmail, subject);
    }

    private String buildEmailContent(Notification notification) {
        Context context = new Context();
        context.setVariable("recipientUsername", notification.getRecipientUsername());
        context.setVariable("subject", notification.getSubject());
        context.setVariable("body", notification.getBody());
        context.setVariable("eventCode", notification.getEventCode());
        context.setVariable("metadata", notification.getMetadata());

        // Template selection based on notification type
        String templateName = "notifications/" + notification.getNotificationType().toLowerCase();

        return templateEngine.process(templateName, context);
    }
}
```

### Example Template: Event Published

**File**: `resources/templates/notifications/event_published.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title th:text="${subject}"></title>
</head>
<body>
    <h1>Event Published</h1>
    <p>Hello <span th:text="${recipientUsername}"></span>,</p>

    <p th:text="${body}"></p>

    <p>
        <a th:href="@{https://batbern.ch/events/{eventCode}(eventCode=${eventCode})}">
            View Event Details
        </a>
    </p>

    <hr>
    <p style="color: #666; font-size: 12px;">
        This notification was sent to you based on your preferences.
        <a href="https://batbern.ch/settings/notifications">Manage preferences</a>
    </p>
</body>
</html>
```

## Notification Entity

### Domain Model

```java
@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    // ADR-003: Meaningful IDs (NOT foreign keys)
    @Column(name = "recipient_username", nullable = false, length = 100)
    private String recipientUsername;

    @Column(name = "event_code", length = 50)
    private String eventCode;  // Nullable for non-event notifications

    // Notification details
    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;  // EMAIL, SMS

    @Column(name = "priority", length = 20)
    private String priority;  // LOW, NORMAL, HIGH, URGENT

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    // Delivery tracking
    @Column(name = "status", nullable = false, length = 20)
    private String status;  // PENDING, UNREAD, SENT, FAILED, READ

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    // Metadata (flexible JSONB storage)
    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
```

### Repository

```java
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // Find notifications for user
    Page<Notification> findByRecipientUsername(String username, Pageable pageable);

    // Find by username and status
    Page<Notification> findByRecipientUsernameAndStatus(
        String username,
        String status,
        Pageable pageable
    );

    // Count unread notifications
    long countByRecipientUsernameAndStatus(String username, String status);

    // Find delivery history for user
    List<Notification> findByRecipientUsernameAndChannelOrderByCreatedAtDesc(
        String username,
        String channel
    );

    // Find failed deliveries
    List<Notification> findByStatusAndCreatedAtBefore(String status, Instant before);
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
    private final RegistrationRepository registrationRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 9 * * *") // 9 AM daily
    public void sendDeadlineReminders() {
        log.info("Starting deadline reminder job");

        Instant now = Instant.now();
        Instant threeDaysFromNow = now.plus(Duration.ofDays(3));

        // Find events with upcoming registration deadlines
        List<Event> upcomingDeadlines = eventRepository
            .findByRegistrationDeadlineBetween(now, threeDaysFromNow);

        int notificationsSent = 0;

        for (Event event : upcomingDeadlines) {
            Duration timeUntilDeadline = Duration.between(now, event.getRegistrationDeadline());

            // Get all registered attendees for this event
            List<String> attendeeUsernames = registrationRepository
                .findUsernamesByEventCode(event.getEventCode());

            // Send reminder to each attendee
            for (String username : attendeeUsernames) {
                notificationService.createAndSendEmailNotification(
                    NotificationRequest.builder()
                        .recipientUsername(username)
                        .eventCode(event.getEventCode())
                        .type("DEADLINE_WARNING")
                        .channel("EMAIL")
                        .priority("HIGH")
                        .subject(String.format("Registration for %s closes in %d days",
                            event.getTitle(), timeUntilDeadline.toDays()))
                        .body(buildDeadlineReminderBody(event, timeUntilDeadline))
                        .build()
                );
                notificationsSent++;
            }
        }

        log.info("Deadline reminder job completed: {} events, {} reminders sent",
            upcomingDeadlines.size(), notificationsSent);
    }

    private String buildDeadlineReminderBody(Event event, Duration timeUntilDeadline) {
        return String.format(
            "This is a reminder that registration for %s will close in %d days on %s. " +
            "Please ensure you have completed all required actions.",
            event.getTitle(),
            timeUntilDeadline.toDays(),
            event.getRegistrationDeadline()
        );
    }
}
```

### Task Deadline Reminder (Integration with Task System)

**Note**: This scheduler bypasses `NotificationService` entirely — it delegates directly to `TaskReminderEmailService`, so **no record is written to the notifications table** for task reminders.

**Locale**: All task reminder emails are rendered in `Locale.GERMAN`; user language preference is not consulted.

**Timezone**: The tomorrow-only window is computed in `Europe/Zurich` timezone.

**Error handling**: The scheduler catches and logs all `RuntimeException` from the repository; the job does not fail the Spring scheduler thread.

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskDeadlineReminderScheduler {

    private final EventTaskRepository eventTaskRepository;
    private final TaskReminderEmailService taskReminderEmailService;

    @Scheduled(cron = "0 0 9 * * *") // 9 AM daily
    public void sendTaskDeadlineReminders() {
        log.info("Starting task deadline reminder scheduler");

        ZoneId swissZone = ZoneId.of("Europe/Zurich");
        ZonedDateTime startOfTomorrow = LocalDate.now(swissZone).plusDays(1).atStartOfDay(swissZone);
        ZonedDateTime startOfDayAfterTomorrow = startOfTomorrow.plusDays(1);

        try {
            // Find tasks due tomorrow only (tomorrow-only window, Swiss timezone)
            List<EventTask> upcomingTasks = eventTaskRepository
                .findTasksDueForReminder(
                    startOfTomorrow.toInstant(),
                    startOfDayAfterTomorrow.toInstant()
                );

            for (EventTask task : upcomingTasks) {
                // Delegate to email service (bypasses NotificationService/notifications table)
                taskReminderEmailService.sendTaskDeadlineReminder(task, Locale.GERMAN);
            }

            log.info("Task deadline reminder scheduler completed: {} reminders sent", upcomingTasks.size());
        } catch (RuntimeException e) {
            // Catches and logs all repository exceptions; does not fail the Spring scheduler thread
            log.error("Task deadline reminder scheduler failed", e);
        }
    }
}
```

## Related Documentation

### Architecture

- [Backend Architecture Overview](./06-backend-architecture.md) - Overall system architecture
- [Data Architecture](./03-data-architecture.md) - Database design patterns and shared schema approach
- [ADR-003: Meaningful Identifiers](./ADR-003-meaningful-identifiers-public-apis.md) - Why we use `username` and `event_code`
- [Workflow State Machines](./06a-workflow-state-machines.md) - Event-driven notification triggers
- [User Lifecycle Sync Patterns](./06b-user-lifecycle-sync.md) - User preference integration

### Stories

- [Story 1.15a.10: Notifications API Consolidation](../stories/1.15a.10.notifications-api-consolidation.md) - **This story** - implementation details
- [Story 5.5: Task System](../stories/5.5-speaker-content-quality-review-task-system.md) - **Reference pattern** - single table in shared schema with ADR-003 compliance

### Implementation Patterns

**This notification system follows the exact same pattern as the Task System (Story 5.5)**:
- ✅ Single table in shared `public` schema
- ✅ Owned by Event Management Service (by convention)
- ✅ ADR-003 compliant (meaningful IDs: `recipient_username`, `event_code`)
- ✅ Cross-service access via HTTP APIs
- ✅ No foreign key constraints across service boundaries
- ✅ Hybrid storage strategy for performance
