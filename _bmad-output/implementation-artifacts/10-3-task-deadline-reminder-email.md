# Story 10.3: Task Deadline Reminder Email

Status: done

## Story

As an **organizer**,
I want to receive an email reminder the day before a task I'm assigned to is due,
so that I don't miss important deadlines for event-related tasks.

## Acceptance Criteria

1. A scheduled job runs daily at 8 AM (Swiss time) and finds all tasks due the following calendar day
2. For each matching task, an email reminder is sent to the assigned organizer
3. Only non-completed tasks with an assigned organizer are included (pending, todo, in_progress)
4. The email contains the task name, event title, due date (dd.MM.yyyy), and a direct link to the task board
5. The email subject identifies the task and states it is due tomorrow (German by default)
6. Email templates support both German (`task-reminder-de.html`) and English (`task-reminder-en.html`)
7. DB-backed template loading (Story 10.2) is used with classpath fallback
8. If the assigned organizer user cannot be found in the user service, the reminder is skipped gracefully (logged, no error)
9. Email send failures do not propagate — the scheduler continues processing remaining tasks
10. ShedLock prevents duplicate execution in multi-instance (ECS) deployments

## Tasks / Subtasks

- [x] Task 1: Add repository query for tasks due tomorrow (AC: 1, 3)
  - [x] 1.1 Write unit test verifying the new query method signature
  - [x] 1.2 Add `findTasksDueForReminder(Instant from, Instant to)` JPQL query to `EventTaskRepository`
    - Filters: `dueDate >= from AND dueDate < to AND status != 'completed' AND assignedOrganizerUsername IS NOT NULL`

- [x] Task 2: Create `TaskReminderEmailService` (AC: 2, 4, 5, 6, 7, 8, 9)
  - [x] 2.1 Write `TaskReminderEmailServiceTest` (unit, `@ExtendWith(MockitoExtension.class)`)
    - Tests: correct recipient, subject DE/EN, body contains task name + event title + link, DB template used, classpath fallback, skip on user not found, skip on event not found, no throw on send failure, null due date handled, null notes handled
  - [x] 2.2 Implement `TaskReminderEmailService` following `SpeakerInvitationEmailService` pattern
    - `@Async` method `sendTaskDeadlineReminder(EventTask task, Locale locale)`
    - Calls `userApiClient.getUserByUsername()` (15-min cached) for email + firstName
    - Calls `eventRepository.findById()` for event title and code
    - Loads template via `emailTemplateService.findByKeyAndLocale("task-reminder", localeStr)` (DB-first)
    - Falls back to `email-templates/task-reminder-{locale}.html` on classpath
    - Variable substitution: `recipientName`, `taskName`, `eventTitle`, `eventCode`, `dueDate`, `taskNotes`, `taskBoardLink`, `organizerName`, `organizerEmail`, `currentYear`
    - Subject DE: `"Aufgabenerinnerung: {taskName} fällig morgen"`
    - Subject EN: `"Task Reminder: {taskName} due tomorrow"`

- [x] Task 3: Create HTML email templates (AC: 6)
  - [x] 3.1 `task-reminder-de.html` — German content fragment (no HTML wrapper; layout provides it)
    - Greeting, task name + event info, due date highlighted, task board CTA button, conditional notes block
  - [x] 3.2 `task-reminder-en.html` — English equivalent
  - Note: `EmailTemplateSeedService` auto-seeds `task-reminder-*` → `TASK_REMINDER` category on startup (already implemented in Story 10.2)

- [x] Task 4: Create `TaskDeadlineReminderScheduler` (AC: 1, 10)
  - [x] 4.1 Write `TaskDeadlineReminderSchedulerTest` (unit)
    - Tests: 1 task → 1 email, 3 tasks → 3 emails, 0 tasks → 0 emails, correct tomorrow window in Swiss TZ, German locale used, no throw on repository failure
  - [x] 4.2 Implement `TaskDeadlineReminderScheduler`
    - `@Scheduled(cron = "${batbern.task-reminders.cron:0 0 8 * * *}")`
    - `@SchedulerLock(name = "TaskDeadlineReminderScheduler_processReminders", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")`
    - Time window: `startOfTomorrow` → `endOfTomorrow` in `Europe/Zurich`
    - Dispatches `taskReminderEmailService.sendTaskDeadlineReminder(task, Locale.GERMAN)` per task
    - Logs count of reminders dispatched

## Dev Notes

- **No Flyway migration needed** — date-window query is naturally idempotent (window advances each day); ShedLock prevents double-runs
- **`UserResponse.id` = username** — the generated OpenAPI type stores username in the `id` field (comment in `UserApiClientImpl` line 418)
- **Scheduler cron** configurable via `batbern.task-reminders.cron` property (independent of `batbern.reminders.cron` used by speaker reminders)
- **Task board URL**: `{baseUrl}/events/{eventCode}/tasks`
- **Locale**: defaults to German for all organizer-facing emails; locale param kept for future flexibility

## Files Changed

| Action | Path |
|--------|------|
| Modified | `services/event-management-service/src/main/java/ch/batbern/events/repository/EventTaskRepository.java` |
| Created | `services/event-management-service/src/main/java/ch/batbern/events/service/TaskReminderEmailService.java` |
| Created | `services/event-management-service/src/main/java/ch/batbern/events/scheduler/TaskDeadlineReminderScheduler.java` |
| Created | `services/event-management-service/src/main/resources/email-templates/task-reminder-de.html` |
| Created | `services/event-management-service/src/main/resources/email-templates/task-reminder-en.html` |
| Created | `services/event-management-service/src/test/java/ch/batbern/events/service/TaskReminderEmailServiceTest.java` |
| Created | `services/event-management-service/src/test/java/ch/batbern/events/scheduler/TaskDeadlineReminderSchedulerTest.java` |
