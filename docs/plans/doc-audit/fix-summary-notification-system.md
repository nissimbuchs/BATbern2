# Fix Summary — Notification System
**Fixed:** 2026-03-09

## Changes made

- **M1**: Updated migration file path from `V25__Create_notifications_table.sql` to `V33__Create_notifications_table.sql` to match the version cited in `NotificationControllerIntegrationTest`.

- **M2**: Replaced all occurrences of `UserServiceClient` with `UserApiClient` throughout the doc (class field declarations, method calls, section heading, and class definition). The actual class is `ch.batbern.events.client.UserApiClient`, as confirmed by `NotificationServiceTest#setUp`.

- **M3**: Updated the `EmailService` method from `send(Notification notification)` to `sendHtmlEmail(String recipientEmail, String subject, String htmlContent)` to match `verify(emailService).sendHtmlEmail(eq("john.doe@example.com"), eq("Test Subject"), anyString())` in `NotificationServiceTest`. Also updated the `NotificationService.createAndSendEmailNotification` snippet to derive `recipientEmail` from `userApiClient.getEmailByUsername(...)` before calling `sendHtmlEmail`. Removed the now-redundant `UserApiClient` field from `EmailService` (recipient email is passed in directly).

- **M4**: Updated `eventRepository.findPublishedAfter(lastLogin)` to `eventRepository.findByPublishedAtAfter(lastLogin)` to match the Spring Data method name verified in `NotificationServiceTest#should_queryDynamically_when_gettingInAppNotifications`.

- **M5**: Replaced the `TaskDeadlineReminderJob` section entirely with `TaskDeadlineReminderScheduler`. Key changes:
  - Class name: `TaskDeadlineReminderJob` → `TaskDeadlineReminderScheduler`
  - Lookup window: 3-day lookahead (`findByDueDateBeforeAndStatusNot(threeDaysFromNow, "completed")`) → tomorrow-only window (`eventTaskRepository.findTasksDueForReminder(startOfTomorrow, startOfDayAfterTomorrow)`) in `Europe/Zurich` timezone
  - Delegation target: `notificationService.createAndSendEmailNotification(...)` → `taskReminderEmailService.sendTaskDeadlineReminder(task, Locale.GERMAN)`
  - Added notes on timezone, German locale, exception swallowing, and bypass of `NotificationService`/notifications table.

- **M6**: Added `UNREAD` to the `Notification.status` field comment: `// PENDING, UNREAD, SENT, FAILED, READ`, matching active usage in `NotificationControllerIntegrationTest`.

- **N1**: Updated the "Real-Time Notifications" section heading from "Future Work / Not implemented" to "Partial — WebSocket Infrastructure Wired", noting that `SimpMessagingTemplate` is already injected into the `NotificationService` constructor (observed in `NotificationServiceTest#setUp`).

- **N2**: Added `Europe/Zurich` timezone note to the `TaskDeadlineReminderScheduler` section (also covered by M5 rewrite).

- **N3**: Added `Locale.GERMAN` note to the `TaskDeadlineReminderScheduler` section (also covered by M5 rewrite).

- **N4**: Added exception-swallowing note to the `TaskDeadlineReminderScheduler` section (also covered by M5 rewrite).

- **N5**: Added `DEADLINE_REMINDER` to the `NotificationType` enum in the doc (used in `NotificationControllerIntegrationTest` as a VARCHAR value) and added an explanatory comment that `TASK_DEADLINE_WARNING` is never written to the notifications table because `TaskDeadlineReminderScheduler` bypasses `NotificationService`.

## Skipped — needs manual decision

- **U1**: `EscalationRuleEngine` thresholds (`DEADLINE_WARNING` ≤3 days, `QUALITY_REVIEW_PENDING` ≥7 days, `VOTING_REQUIRED` <50% after ≥3 days) — no test class for `EscalationRuleEngine` exists. These thresholds could drift silently. Consider adding unit tests or removing the class if it is not yet instantiated.

- **U2**: Quiet hours logic — no test exercises `isInQuietHours` suppression or the midnight-crossing edge case. The doc's branch comment `"Normal range (e.g., 22:00-07:00 next day)"` is placed in the `start.isBefore(end)` branch, but 22:00→07:00 crosses midnight so `start.isBefore(end)` is false — this may reflect a logic inversion bug. Needs a test and possibly a bug fix.

- **U3**: `onEventPublished` event listener — no test mocks or verifies this listener. The `findUsernamesByEventId` method name is also untested (deadline tests use `findUsernamesByEventCode`). Consider adding an integration test.

- **U4**: `escalateToOrganizers` / URGENT notification dispatch — no test verifies escalation email or organizer-lookup HTTP call. Consider adding tests before relying on this in production.
