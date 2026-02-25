package ch.batbern.events.scheduler;

import ch.batbern.events.domain.EventTask;
import ch.batbern.events.repository.EventTaskRepository;
import ch.batbern.events.service.TaskReminderEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Scheduled job for task deadline reminder emails.
 * Story 10.3: Task Deadline Reminder Email
 *
 * Runs daily at 8 AM and sends reminder emails for all tasks due the following day
 * to their assigned organizer.
 *
 * Uses ShedLock to prevent duplicate execution in multi-instance ECS deployments.
 * Follows the same pattern as {@link SpeakerReminderScheduler}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskDeadlineReminderScheduler {

    private static final ZoneId SWISS_ZONE = ZoneId.of("Europe/Zurich");

    private final EventTaskRepository eventTaskRepository;
    private final TaskReminderEmailService taskReminderEmailService;

    /**
     * Send task deadline reminders for tasks due tomorrow.
     * Runs daily at 8:00 AM (before 9 AM registration deadline reminders).
     *
     * ShedLock ensures only one instance runs in multi-node deployment.
     * Lock is held for minimum 5 minutes, maximum 30 minutes.
     */
    @Scheduled(cron = "${batbern.task-reminders.cron:0 0 8 * * *}")
    @SchedulerLock(
            name = "TaskDeadlineReminderScheduler_processReminders",
            lockAtLeastFor = "PT5M",
            lockAtMostFor = "PT30M"
    )
    public void processReminders() {
        log.info("Starting scheduled task deadline reminder processing");

        try {
            Instant startOfTomorrow = LocalDate.now(SWISS_ZONE).plusDays(1)
                    .atStartOfDay(SWISS_ZONE).toInstant();
            Instant endOfTomorrow = LocalDate.now(SWISS_ZONE).plusDays(2)
                    .atStartOfDay(SWISS_ZONE).toInstant();

            List<EventTask> tasksDueTomorrow =
                    eventTaskRepository.findTasksDueForReminder(startOfTomorrow, endOfTomorrow);

            if (tasksDueTomorrow.isEmpty()) {
                log.debug("No tasks due tomorrow — skipping reminder emails");
                return;
            }

            log.info("Found {} task(s) due tomorrow — sending reminders", tasksDueTomorrow.size());

            for (EventTask task : tasksDueTomorrow) {
                taskReminderEmailService.sendTaskDeadlineReminder(task, Locale.GERMAN);
            }

            log.info("Task deadline reminder processing complete: {} reminder(s) dispatched",
                    tasksDueTomorrow.size());

        } catch (Exception e) {
            log.error("Task deadline reminder processing failed", e);
        }
    }
}
