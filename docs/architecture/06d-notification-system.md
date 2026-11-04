# Notification & Delivery System

This document outlines the notification and delivery system for the BATbern Event Management Platform: email notifications, in-app notifications for organizers, and delivery tracking.

## Overview

The notification system has two channels in use: **EMAIL** (AWS SES via the shared-kernel
`EmailService`) and **IN_APP** (organizer activity feed). Both are recorded in the `notifications`
table. There is no SMS channel: the `channel` column comment (V33, V34) and the entity still name
`SMS`, but no code path sends one.

The only escalation that exists is `SpeakerReminderService`: when a speaker has not responded or
not submitted content after the third reminder, it emails the assigned organizer (or the event's
organizer) a `SPEAKER_ESCALATION` notification. There is no rule engine (see
[Not implemented](#not-implemented)).

**Implementation Pattern**: Single table in shared `public` schema, following Task System pattern (Story 5.5).

**Key Characteristics**:
- ✅ Single `notifications` table owned by Event Management Service
- ✅ Rows for both channels: email audit trail, and one IN_APP row per organizer per domain event (see the correction below)
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

**Email Notifications** (Eager - Create Records):
- Create notification record when an email is sent
- Track delivery status (audit trail, compliance)
- Storage: ~150,000 rows/year (3000 users × 50 emails/year)

**In-App Notifications** (Lazy - Query Dynamically):
- Don't create rows in advance
- Query when user opens app: "Show events published since last login"
- Storage: Zero rows for in-app (computed dynamically)

> **Correction (2026-10-25, #1068):** the organizer activity feed does NOT follow the lazy model
> above. `OrganizerNotificationListener` (event-management) writes one `IN_APP` row **per
> organizer per domain event**. On production that reached 6,365 rows for a single test organizer,
> almost all from nightly E2E runs. Rules for these rows:
> - **Initiator in every body.** `"{username} moved BATbern60 to Agenda Published"`, `"{username}
>   moved a speaker of BATbern60 from Contacted to Ready"`, `"{username} - Speaker Accepted"`.
>   Actors come from the security context (`SlotAssignmentController` used a literal `"organizer"`).
> - **Scoped to the event by `event_code`** (no FK, ADR-003). `SpeakerWorkflowStateChange` puts
>   the code into the domain event's metadata so it is scoped too.
> - **Deleted with the event.** `DELETE /events/{code}` deletes the event's notifications in the
>   same transaction, and the async listener skips events that no longer exist.
> - **Test runs sweep them.** `POST /api/v1/admin/test-fixtures/ems/cleanup` with `entityType=notifications`
>   deletes rows of every test event code (`BATbern{N>=10000}`, `BRUNO-TEST-*`) whatever their age,
>   plus pre-fix `SpeakerWorkflowStateChange` rows whose speaker_pool row is gone. The Playwright
>   global teardown and the Bruno `99d-posttest-cleanup-notifications` requests call it.

### Implementation

`services/event-management-service/src/main/java/ch/batbern/events/notification/`:

- **`NotificationService.createAndSendEmailNotification(NotificationRequest)`**: reads the
  recipient's preferences from CUMS (`UserApiClient.getPreferences`), skips the send when email
  notifications are disabled or the recipient is in quiet hours, saves a `PENDING` row, sends
  via `EmailService.sendHtmlEmail(...)` with a `MailContext` (so the send is recorded in the
  Mail Center, Story 16.1a), then sets `SENT` or `FAILED`. Its only caller in `src/main` is
  `SpeakerReminderService` (type `SPEAKER_ESCALATION`, priority `HIGH`).
- **`NotificationService.pushNotificationViaWebSocket(Notification)`**: sends the row to the STOMP
  destination `/topic/notifications/{username}`. Failures are logged and ignored.
- **`OrganizerNotificationListener`**: listens to domain events and writes one `IN_APP` row per
  organizer per event (see the correction above), with `notification_type` set to the domain
  event type and a priority derived from the event type name (`Cancelled`/`Failed`/`Error` HIGH,
  `Deadline`/`Urgent` URGENT, `Updated`/`Modified` LOW, else NORMAL). It then calls
  `pushNotificationViaWebSocket`.
- **`NotificationController`**: REST read and status API for the rows.

There is no `EventPublishedEvent` listener any more (#1179): publishing the agenda used to mail
every registration, cancelled ones included, without anyone deciding to.

## Notification Types

`notification_type` is a free `VARCHAR(50)`; there is no backend enum. Values written by
`src/main` code today:

| Value | Channel | Written by |
|---|---|---|
| Domain event type (for example `SpeakerWorkflowStateChange`) | `IN_APP` | `OrganizerNotificationListener` |
| `SPEAKER_ESCALATION` | `EMAIL` | `SpeakerReminderService` (after three unanswered reminders) |

Task deadline reminders do not use this table: `TaskDeadlineReminderScheduler` delegates to
`TaskReminderEmailService` directly.

The frontend union in `web-frontend/src/types/notification.ts` lists more values (including
`VOTING_REQUIRED`), which `TeamActivityFeed` can display; the backend does not produce them.

## Not implemented

Earlier versions of this document described the following. None of it exists in the code
(checked by grep on develop c4593449); it is kept here only so that nobody looks for it:

- an **SMS** channel;
- an **`EscalationRuleEngine`** with deadline-proximity, review-age and voting-stagnation rules,
  `EscalationLevel` / `EscalationNotification` types and automatic escalation to all organizers;
- a **`WorkflowNotificationEvent`** class and backend `NotificationType` / `NotificationPriority`
  enums (`VOTING_REQUIRED`, `OVERFLOW_DETECTED`, `SLOT_ASSIGNED`, ...). Overflow voting was
  removed from scope (ADR-009 replaced it with the slot-capacity gate);
- **dynamically computed** in-app notifications (`getInAppNotifications`, "events published since
  last login").

## User Notification Preferences

**Storage**: Preferences stored in existing `user_profiles.user_preferences` JSONB column (Company-User Management Service)

**Fields** (already exist):
- `emailNotifications` (boolean)
- `inAppNotifications` (boolean)
- `pushNotifications` (boolean)
- `notificationFrequency` (string: "realtime", "daily_digest", "weekly_digest")
- `qnaNotificationFrequency` (string: "live" | "daily" | "off", default "live") — Story 15.7;
  per-user cadence for the Q&A new-questions digest (see Scheduled Notification Jobs). Stored as
  `user_profiles.pref_qna_notification_frequency`.
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

## Real-Time Notifications (WebSocket push, organizer dashboard)

What the code does: `OrganizerNotificationListener` calls
`NotificationService.pushNotificationViaWebSocket`, which publishes to
`/topic/notifications/{username}` via `SimpMessagingTemplate`. On the frontend,
`EventManagementDashboard` subscribes through `useNotificationWebSocket`
(`web-frontend/src/hooks/useNotificationWebSocket.ts`, `services/notificationWebSocketClient.ts`).

Not verified for this document: whether the push reaches clients in production when EMS runs
more than one task. Story 15.1 replaced WebSockets with REST polling for the live agenda for
exactly that multi-task reason (see the V119 migration comment); the notification push was not
part of that change. The rows themselves are always persisted, so the feed is correct on reload.

## Email Notification Templates

### Email Service (AWS SES Integration)

**Infrastructure**: AWS SES for email delivery

**Templates**: Thymeleaf templates in `resources/templates/notifications/`

#### Additional-email CC fan-out (Story 10.32)

As of Story 10.32 (Phase 4 + 6 + 7, 2026-05-22), the shared-kernel `EmailService` exposes CC-aware overloads on both the simple-HTML and attachment paths:

| Method | Signature | Used by |
|---|---|---|
| `sendHtmlEmail` (async) | `(to, subject, html)` — 3-arg, no CC | legacy / anonymous flows |
| `sendHtmlEmail` (async) | `(to, cc, subject, html)` — 4-arg | speaker invitation, speaker acceptance, speaker reminder, quality-review revision, waitlist promotion, waitlist confirmation |
| `sendHtmlEmailSync` | `(to, cc, subject, html, configSet)` — 5-arg | sync newsletter-bulk and inline-loop callers (newsletter itself does NOT use CC; remains primary-only by design) |
| `sendHtmlEmailWithAttachments` | `(to, cc, subject, html, attachments)` — 5-arg | event registration confirmation (ICS attachment); partner meeting calendar invite (ICS attachment) |

Each EMS service that delivers a user-addressed transactional message resolves the recipient via `UserApiClient.getUserByUsername(...)` (directly or through `PrimarySpeakerResolver`), flattens `UserResponse.additionalEmails[]` into a `List<String>`, and passes it as the `cc` argument. SES `Destination.ccAddresses` carries the list; CC entries matching `to` case-insensitively are dropped to avoid duplicate delivery. A null/missing `additionalEmails` field (e.g. rolling-deploy backwards-compat) is treated as an empty list — behaviour is identical to the 3-arg signature in that case.

**Explicitly excluded** from additional-email fan-out:
- `NewsletterEmailService` — newsletter is email-keyed (Open Q#4); subscribers register addresses independently of user profiles.
- `InboundEmailConfirmationEmailService` (unsubscribe + cancel reply paths) — recipients are arbitrary external senders, not registered users.
- Alias forwarding of `batbern{N}-participants@` (`ForwardingRecipientResolver` → `DistributionListService.resolveParticipants`, AC15) — registrant distribution stays attendee-email-driven; a user's additional emails are used only when a registration has no captured `attendeeEmail`.

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
    private String channel;  // EMAIL or IN_APP (the source comment still says "EMAIL, SMS"; SMS is never sent)

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

### Q&A New-Questions Digest Job (Story 15.7)

`QnaNotificationService.flushPending()` (Event Management Service) runs every 5 minutes
(`@Scheduled` + `@SchedulerLock(name = "qnaDigestFlush")`) and emails, when new **top-level**
questions arrive in a session's Q&A: that **session's own presenters** (`PRIMARY_SPEAKER` +
`CO_SPEAKER`) **plus the event's moderator(s)**. Per-recipient behaviour:

- **Recipients (event-2 fix):** the moderator is resolved **event-wide**, not per session —
  `SessionUserRepository.findModeratorUsernamesByEventCode(eventCode)` returns every `MODERATOR`
  across the event. BATbern's moderator is an event-level role (assigned to the moderation
  bookend sessions), so a per-session lookup never reached them for content-session Q&A.
- **Cadence** comes from each recipient's `qnaNotificationFrequency` preference: `live` → at most
  one digest per **15 min** per window; `daily` → at most one per **24 h**; `off` → never.
- **Freeze wins**: only `OPEN` Q&A windows are scanned — a `FROZEN` window never produces a digest.
- **Exclusions**: replies (non-top-level) and a recipient's own posts never notify that recipient;
  `PANELIST`s are not recipients.
- State is kept per `(window, recipient)` in `session_qna_notification` (`last_notified_at` throttle
  anchor + `notified_through` high-water mark, so each email counts only genuinely new questions).
- Recipient email + locale + cadence are resolved at send time via the cached `UserApiClient`
  (ADR-004). **Email is read off the user entity** — `getUserByUsername(username).getEmail()`
  (single-segment `/api/v1/users/{username}`, covered by the CUMS VPC service-to-service rule for
  the no-JWT scheduled context); there is **no** dedicated `/users/{username}/email` endpoint.
  Email body is **DE + EN only** (`qna-new-questions-{de,en}.html`); non-DE/EN → EN. The template
  is categorised `SPEAKER` (admin-editable under the Speakers tab).

### No automatic mail to a whole audience (#1179)

`DeadlineReminderJob` (registration deadline reminder) and `NotificationService.onEventPublished`
were **removed on 2026-11-02**. Both sat dormant from January, because every send on their path
failed on a wrong preferences URL. #1159 fixed that URL on 2026-10-29, and the next morning the
deadline job mailed all 267 BATbern60 registrations, cancelled and waitlisted included, in English,
with a raw ISO timestamp.

The rule since then: **a mail to many people goes out only when an organizer sends it from the Mail
Center.** Automatic triggers may mail one person about their own case (their confirmation link,
their speaker deadline, their task) or organizers. `AutomaticTriggerAudienceGuardTest` lists every
`@Scheduled`, `@EventListener`, `@TransactionalEventListener` and `@SqsListener` method in EMS with
the audience it can reach. A new trigger fails the build until it is added there.

The `DEADLINE_WARNING` and `EVENT_PUBLISHED` values stay valid in the `notifications` table because
the 2026-11-02 rows carry them.

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

- [Story 1.15a.10: Notifications API Consolidation](../stories/archived/epic-1/1.15a.10.notifications-api-consolidation.md) - **This story** - implementation details
- [Story 5.5: Task System](../stories/archived/epic-5/5.5-speaker-content-quality-review-task-system.md) - **Reference pattern** - single table in shared schema with ADR-003 compliance

### Implementation Patterns

**This notification system follows the exact same pattern as the Task System (Story 5.5)**:
- ✅ Single table in shared `public` schema
- ✅ Owned by Event Management Service (by convention)
- ✅ ADR-003 compliant (meaningful IDs: `recipient_username`, `event_code`)
- ✅ Cross-service access via HTTP APIs
- ✅ No foreign key constraints across service boundaries
- ✅ Hybrid storage strategy for performance
