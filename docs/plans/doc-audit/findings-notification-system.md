# Doc Audit Findings — Notification System
**Audited:** 2026-03-09
**Doc:** `docs/architecture/06d-notification-system.md`
**Tests searched:** `services/speaker-coordination-service/src/test/java`, `services/event-management-service/src/test/java`

## Summary
- VALIDATED: 7
- MISMATCH: 6
- UNTESTED: 5
- UNDOCUMENTED: 5

---

## MISMATCH

### M1 — Migration file number (V25 vs V33)
**Doc claims:** "Location: `services/event-management-service/src/main/resources/db/migration/V25__Create_notifications_table.sql`"
**Test asserts:** `NotificationControllerIntegrationTest` comment states "Migration V33 applied (notifications table)" — the controller integration test explicitly cites V33, not V25.
**Action:** Update doc migration path to `V33__Create_notifications_table.sql`.

---

### M2 — HTTP client class name: `UserServiceClient` vs `UserApiClient`
**Doc claims:** `NotificationService` depends on `UserServiceClient` (see code snippets throughout the doc, e.g. `private final UserServiceClient userServiceClient`)
**Test asserts:** `NotificationServiceTest#setUp` constructs `NotificationService(notificationRepository, emailService, userApiClient, ...)` where `userApiClient` is typed `UserApiClient` — a different class name with a different import (`ch.batbern.events.client.UserApiClient`).
**Action:** Replace all occurrences of `UserServiceClient` with `UserApiClient` in the doc.

---

### M3 — EmailService method signature: `send(Notification)` vs `sendHtmlEmail(String, String, String)`
**Doc claims:** `emailService.send(notification)` (full `Notification` object passed; AWS SES built inside `EmailService`)
**Test asserts:** `NotificationServiceTest#should_createNotificationRecord_when_sendingEmail` verifies `verify(emailService).sendHtmlEmail(eq("john.doe@example.com"), eq("Test Subject"), anyString())` — three scalar arguments: recipient address, subject, HTML body.
**Action:** Update the EmailService integration snippet to show `emailService.sendHtmlEmail(recipientEmail, notification.getSubject(), htmlContent)`.

---

### M4 — In-app query method: `findPublishedAfter` vs `findByPublishedAtAfter`
**Doc claims:** `List<Event> newEvents = eventRepository.findPublishedAfter(lastLogin);`
**Test asserts:** `NotificationServiceTest#should_queryDynamically_when_gettingInAppNotifications` stubs `when(eventRepository.findByPublishedAtAfter(lastLogin)).thenReturn(...)` — method name follows Spring Data naming convention `findByPublishedAtAfter`.
**Action:** Update doc method call to `eventRepository.findByPublishedAtAfter(lastLogin)`.

---

### M5 — TaskDeadlineReminderJob: class name, lookup window, and delegation target
**Doc claims:** Class `TaskDeadlineReminderJob` runs `@Scheduled(cron = "0 0 9 * * *")`, calls `taskRepository.findByDueDateBeforeAndStatusNot(threeDaysFromNow, "completed")` (3-day lookahead), and calls `notificationService.createAndSendEmailNotification(...)` directly.
**Test asserts:** `TaskDeadlineReminderSchedulerTest` tests class `TaskDeadlineReminderScheduler`, which calls `eventTaskRepository.findTasksDueForReminder(from, to)` with a **tomorrow-only window** (start-of-tomorrow → start-of-day-after-tomorrow in `Europe/Zurich`), and delegates to `TaskReminderEmailService#sendTaskDeadlineReminder(task, Locale.GERMAN)` — not `NotificationService`.
Three specific assertions differ: (1) class name, (2) 1-day vs 3-day lookahead, (3) delegation target (`TaskReminderEmailService` vs `NotificationService`).
**Action:** Replace the `TaskDeadlineReminderJob` section with `TaskDeadlineReminderScheduler`, update the lookup window to tomorrow-only in Swiss timezone, and update the delegation target.

---

### M6 — Notification status `UNREAD` not in documented status set
**Doc claims:** Entity comment lists valid statuses as `PENDING, SENT, FAILED, READ`.
**Test asserts:** `NotificationControllerIntegrationTest#setUp` saves three notifications with `status("UNREAD")`, and `should_returnUnreadOnly_when_statusUnread` / `should_returnUnreadCount_when_statusUnread` filter by `status=UNREAD`. `UNREAD` is a live status in use.
**Action:** Add `UNREAD` to the documented status enum: `PENDING, UNREAD, SENT, FAILED, READ`.

---

## UNTESTED

### U1 — EscalationRuleEngine thresholds
**Doc claims:** Three specific thresholds in `EscalationRuleEngine`:
- `DEADLINE_WARNING` escalates when `timeUntilDeadline.toDays() <= 3`
- `QUALITY_REVIEW_PENDING` escalates when review age `>= 7 days`
- `VOTING_REQUIRED` escalates when `< 50%` voted after `>= 3 days`
No test class for `EscalationRuleEngine` was found in either test directory.
**Risk:** high — these thresholds could drift silently; the 50%-voting and 7-day-review rules are completely unverified.

---

### U2 — Quiet hours logic
**Doc claims:** `isInQuietHours(UserPreferences prefs)` uses `LocalTime` comparison to suppress notifications during configured quiet hours (default 22:00–07:00). Also contains a suspected bug: the `if (start.isBefore(end))` branch comment says "Normal range (e.g., 22:00-07:00 next day)" but 22:00→07:00 crosses midnight so `start.isBefore(end)` is false — the described range falls into the `else` branch.
No test exercises quiet hours suppression or the midnight-crossing edge case.
**Risk:** high — the example in the doc may reflect a logic inversion bug that would cause quiet-hours notifications to fire, and morning-hours notifications to be suppressed.

---

### U3 — `onEventPublished` event listener
**Doc claims:** `@EventListener @Async public void onEventPublished(EventPublishedEvent event)` sends EMAIL notifications to all registered attendees when an event is published, using `registrationRepository.findUsernamesByEventId(event.getEventId())`.
No test mocks or verifies this listener firing.
**Risk:** medium — the listener is async and the `findUsernamesByEventId` method name is never exercised in tests (deadline tests use `findUsernamesByEventCode`).

---

### U4 — `escalateToOrganizers` sending URGENT notifications
**Doc claims:** When escalation is triggered, `escalateToOrganizers()` calls `userServiceClient.getOrganizerUsernames()` and sends URGENT-priority notifications to all organizers.
No test verifies escalation email dispatch or the organizer-lookup HTTP call.
**Risk:** medium — escalation is silent failure territory.

---

### U5 — `DEADLINE_REMINDER` notification type
**Doc claims:** The `NotificationType` enum defines: `SPEAKER_INVITED, SPEAKER_ACCEPTED, SPEAKER_DECLINED, CONTENT_SUBMITTED, QUALITY_REVIEW_PENDING, QUALITY_REVIEW_APPROVED, QUALITY_REVIEW_REQUIRES_CHANGES, SLOT_ASSIGNED, DEADLINE_WARNING, OVERFLOW_DETECTED, VOTING_REQUIRED, EVENT_PUBLISHED`.
**Test uses:** `NotificationControllerIntegrationTest#setUp` creates a notification with `notificationType("DEADLINE_REMINDER")` — a string value absent from the documented enum.
**Risk:** low — `notificationType` is a VARCHAR column, so the value works at runtime, but the enum documentation is incomplete.

---

## UNDOCUMENTED

### N1 — SimpMessagingTemplate injected into NotificationService
**Test:** `NotificationServiceTest#setUp` constructs `NotificationService(notificationRepository, emailService, userApiClient, eventRepository, registrationRepository, messagingTemplate)` where `messagingTemplate` is typed `org.springframework.messaging.simp.SimpMessagingTemplate`. The doc explicitly states "Real-Time Notifications (Future Work) — Status: Not implemented … deferred to future story."
**Action:** Remove the "not implemented" statement or add a note that `SimpMessagingTemplate` is already wired into the service constructor (likely for the `watch/` subsystem integration).

---

### N2 — TaskDeadlineReminderScheduler uses Europe/Zurich timezone
**Test:** `TaskDeadlineReminderSchedulerTest#should_queryTomorrowWindow_in_swissTimezone` asserts the scheduler computes start-of-tomorrow and start-of-day-after-tomorrow using `ZoneId.of("Europe/Zurich")`.
**Action:** Add to the scheduler section: "Tomorrow window is computed in `Europe/Zurich` timezone."

---

### N3 — TaskDeadlineReminderScheduler forces German locale
**Test:** `TaskDeadlineReminderSchedulerTest#should_useGermanLocale_for_allReminders` verifies `sendTaskDeadlineReminder(task, Locale.GERMAN)` — all task reminder emails are German-only regardless of user preference.
**Action:** Add to the scheduler section: "Emails rendered in `Locale.GERMAN`; user language preference is not consulted for task reminders."

---

### N4 — TaskDeadlineReminderScheduler swallows repository exceptions
**Test:** `TaskDeadlineReminderSchedulerTest#should_notThrow_when_repositoryFails` verifies the scheduler completes without re-throwing when the repository throws `RuntimeException`.
**Action:** Add error-handling note: "Scheduler catches and logs all `RuntimeException` from the repository; the job does not fail the Spring scheduler thread."

---

### N5 — `TASK_DEADLINE_WARNING` notification type never exercised
**Test:** `DeadlineReminderJobTest#should_includeDeadlineInfo_when_sendingNotification` verifies type `"DEADLINE_WARNING"` is used for registration deadline reminders. The doc's `TaskDeadlineReminderJob` snippet uses `"TASK_DEADLINE_WARNING"`, but the actual `TaskDeadlineReminderScheduler` delegates to `TaskReminderEmailService` and no `NotificationService` call is verified — `"TASK_DEADLINE_WARNING"` is never asserted in any test.
**Action:** Clarify whether `TASK_DEADLINE_WARNING` is still a valid type or whether the scheduler bypasses the notifications table entirely by delegating directly to `TaskReminderEmailService`.

---

## VALIDATED
- "Create notification record before sending email (audit trail)" → `NotificationServiceTest#should_createNotificationRecord_when_sendingEmail` (2× `repository.save` verified)
- "Skip notification when user opted out of email" → `NotificationServiceTest#should_skipNotification_when_userOptedOut`
- "Status → SENT + sentAt set on successful delivery" → `NotificationServiceTest#should_updateStatusToSent_when_emailDeliverySucceeds`
- "Status → FAILED + failedAt + failureReason set on delivery exception" → `NotificationServiceTest#should_updateStatusToFailed_when_emailDeliveryFails`
- "In-app notifications queried dynamically (zero DB writes)" → `NotificationServiceTest#should_queryDynamically_when_gettingInAppNotifications`
- "Deadline reminder finds events with deadlines in next 3 days via `findByRegistrationDeadlineBetween`" → `DeadlineReminderJobTest#should_findUpcomingDeadlines_when_jobExecutes`
- "Repository query methods: `findByRecipientUsername`, `findByRecipientUsernameAndStatus`, `countByRecipientUsernameAndStatus`, `findByRecipientUsernameAndChannelOrderByCreatedAtDesc`, `findByStatusAndCreatedAtBefore`" → `NotificationRepositoryTest` (5 tests)
