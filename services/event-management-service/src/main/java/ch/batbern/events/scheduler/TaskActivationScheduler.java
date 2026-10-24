package ch.batbern.events.scheduler;

import ch.batbern.events.service.EventTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Daily task activation safety net (organizer cockpit plan T1 + T4).
 *
 * <p>Task activation is normally edge-triggered by {@code EventWorkflowTransitionEvent}. This job
 * makes it level-triggered as well: any pending task whose trigger has been reached, or whose due
 * date is within its activation lead days, becomes {@code todo}; superseded newsletter tasks are
 * cancelled. It runs at 05:30, after the 01:00 auto-publish jobs and before the 08:00 deadline
 * reminders, so a reminder never goes out for a task that should already be visible.
 *
 * <p>Uses ShedLock to prevent duplicate execution in multi-instance ECS deployments.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskActivationScheduler {

    private final EventTaskService eventTaskService;

    @Scheduled(cron = "${batbern.task-activation.cron:0 30 5 * * *}", zone = "Europe/Zurich")
    @SchedulerLock(
            name = "TaskActivationScheduler_reconcileOpenTasks",
            lockAtLeastFor = "PT5M",
            lockAtMostFor = "PT30M"
    )
    public void reconcileOpenTasks() {
        try {
            int activated = eventTaskService.reconcileOpenTasks(Instant.now());
            log.info("Task activation reconcile finished: {} task(s) activated", activated);
        } catch (Exception e) {
            log.error("Task activation reconcile failed: {}", e.getMessage(), e);
        }
    }
}
