package ch.batbern.events.scheduler;

import ch.batbern.events.config.ReminderProperties;
import ch.batbern.events.service.SpeakerReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for speaker deadline reminder processing.
 * Story 6.5: Automated Deadline Reminders (AC2)
 *
 * Runs daily at 8 AM to send reminders for upcoming speaker deadlines.
 * Uses ShedLock to prevent duplicate execution in multi-instance ECS deployments.
 *
 * Follows same pattern as {@link TokenCleanupScheduler}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpeakerReminderScheduler {

    private final SpeakerReminderService speakerReminderService;
    private final ReminderProperties reminderProperties;

    /**
     * Process speaker deadline reminders.
     * Runs daily at 8:00 AM (before 9 AM registration deadline reminders).
     *
     * ShedLock ensures only one instance runs in multi-node deployment.
     * Lock is held for minimum 5 minutes, maximum 30 minutes.
     */
    @Scheduled(cron = "${batbern.reminders.cron:0 0 8 * * *}")
    @SchedulerLock(
            name = "SpeakerReminderScheduler_processReminders",
            lockAtLeastFor = "PT5M",
            lockAtMostFor = "PT30M"
    )
    public void processReminders() {
        if (!reminderProperties.isEnabled()) {
            log.debug("Speaker reminders disabled, skipping scheduled execution");
            return;
        }

        log.info("Starting scheduled speaker reminder processing");

        try {
            SpeakerReminderService.ReminderProcessingResult result =
                    speakerReminderService.processReminders();

            log.info("Scheduled speaker reminder processing complete: {} response, {} content, {} skipped",
                    result.responseReminders(), result.contentReminders(), result.skipped());
        } catch (Exception e) {
            log.error("Scheduled speaker reminder processing failed", e);
        }
    }
}
