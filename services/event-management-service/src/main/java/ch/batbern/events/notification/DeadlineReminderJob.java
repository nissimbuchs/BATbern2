package ch.batbern.events.notification;

import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled job for deadline reminder notifications
 * Story BAT-7: Notifications API Consolidation
 *
 * Runs daily at 9 AM to send reminders for upcoming event registration deadlines
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeadlineReminderJob {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final NotificationService notificationService;

    /**
     * Send deadline reminders for events with deadlines in next 3 days
     * Runs daily at 9 AM
     */
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

    /**
     * Build email body for deadline reminder
     */
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
