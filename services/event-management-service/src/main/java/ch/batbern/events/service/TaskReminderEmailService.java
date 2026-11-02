package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTask;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.mailcenter.render.MailPipeline;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.events.mailcenter.variables.MailVariable;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Service for sending task deadline reminder emails to assigned organizers.
 * Story 10.3: Task Deadline Reminder Email
 *
 * Sends a reminder 1 day before the task due date to the assigned organizer.
 * Follows the same async, DB-first template loading pattern as speaker email services.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskReminderEmailService {

    private final EmailService emailService;
    private final UserApiClient userApiClient;
    private final EventRepository eventRepository;
    private final MailPipeline mailPipeline;

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final ZoneId SWISS_ZONE = ZoneId.of("Europe/Zurich");

    /**
     * Send a task deadline reminder email asynchronously.
     * AC1: Reminder email sent 1 day before task due date to task owner.
     *
     * @param task the task due tomorrow
     * @param locale preferred language (defaults to German)
     */
    @Async
    public void sendTaskDeadlineReminder(EventTask task, Locale locale) {
        sendReminder(task, locale, MailTrigger.system());
    }

    /**
     * As above, as part of a scheduler run: all reminders of one run and event are one recorded
     * mail (Story 16.1a AC2).
     */
    @Async
    public void sendTaskDeadlineReminder(EventTask task, Locale locale, MailTrigger run) {
        sendReminder(task, locale, run);
    }

    private void sendReminder(EventTask task, Locale locale, MailTrigger run) {
        String username = task.getAssignedOrganizerUsername();
        try {
            // Look up organizer profile (email + display name) in one call
            UserResponse organizer;
            try {
                organizer = userApiClient.getUserByUsername(username);
            } catch (UserNotFoundException e) {
                log.warn("Skipping task reminder — organizer not found: username={}", username);
                return;
            }

            // Look up event for title and code
            Optional<Event> eventOpt = eventRepository.findById(task.getEventId());
            if (eventOpt.isEmpty()) {
                log.warn("Skipping task reminder — event not found: eventId={}", task.getEventId());
                return;
            }
            Event event = eventOpt.get();

            log.info("Sending task deadline reminder to: {} for task: {} event: {}",
                    LoggingUtils.maskEmail(organizer.getEmail()), task.getTaskName(), event.getEventCode());

            Locale emailLocale = (locale != null) ? locale : Locale.GERMAN;
            EmailContent content = buildEmailBody(task, event, organizer, emailLocale);

            MailContext context = MailContext.of(run, event.getEventCode(), MailContext.Audience.ORGANIZERS,
                    "task-reminder");
            emailService.sendHtmlEmail(context, organizer.getEmail(), null, content.subject(), content.html(), null);

            log.info("Task deadline reminder sent successfully to: {}",
                    LoggingUtils.maskEmail(organizer.getEmail()));

        } catch (Exception e) {
            log.error("Failed to send task deadline reminder for task={} assignee={}",
                    task.getId(), username, e);
            // Don't re-throw — email failure must not break the scheduler
        }
    }

    private record EmailContent(String html, String subject) {}

    private EmailContent buildEmailBody(EventTask task, Event event, UserResponse organizer, Locale locale) {
        String localeStr = locale.getLanguage();

        String recipientName = (organizer.getFirstName() != null && !organizer.getFirstName().isBlank())
                ? organizer.getFirstName()
                : organizer.getId();

        String formattedDueDate = task.getDueDate() != null
                ? task.getDueDate().atZone(SWISS_ZONE).format(DATE_FORMATTER)
                : "";

        Map<String, String> variables = mailPipeline.variables().event(event, localeStr)
                .put(MailVariable.RECIPIENT_NAME, recipientName)
                .put(MailVariable.TASK_NAME, task.getTaskName())
                .put(MailVariable.DUE_DATE, formattedDueDate)
                .put(MailVariable.TASK_NOTES, task.getNotes())
                .put(MailVariable.TASK_BOARD_LINK, baseUrl + "/events/" + event.getEventCode() + "/tasks")
                .put(MailVariable.DASHBOARD_LINK, baseUrl + "/dashboard")
                .asMap();

        RenderedMail mail = mailPipeline.render(mailPipeline.template("task-reminder", localeStr), variables);
        String html = mail.html();
        String subject = mail.subject() != null ? mail.subject()
                : localeStr.equals("de")
                        ? "Aufgabenerinnerung: " + task.getTaskName() + " fällig morgen"
                        : "Task Reminder: " + task.getTaskName() + " due tomorrow";
        return new EmailContent(html, subject);
    }
}
