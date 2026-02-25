package ch.batbern.events.service;

import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTask;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private final EmailTemplateService emailTemplateService;
    private final UserApiClient userApiClient;
    private final EventRepository eventRepository;

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;

    @Value("${app.email.organizer-name:BATbern Team}")
    private String organizerName;

    @Value("${app.email.organizer-email:events@batbern.ch}")
    private String organizerEmail;

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

            emailService.sendHtmlEmail(organizer.getEmail(), content.subject(), content.html());

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
        String classpathFallback = localeStr.equals("de")
                ? "email-templates/task-reminder-de.html"
                : "email-templates/task-reminder-en.html";

        // DB-first template loading (Story 10.2)
        String contentHtml = loadHtmlContent("task-reminder", localeStr, classpathFallback);

        String recipientName = (organizer.getFirstName() != null && !organizer.getFirstName().isBlank())
                ? organizer.getFirstName()
                : organizer.getId();

        String formattedDueDate = task.getDueDate() != null
                ? task.getDueDate().atZone(SWISS_ZONE).format(DATE_FORMATTER)
                : "";

        String taskBoardLink = baseUrl + "/events/" + event.getEventCode() + "/tasks";

        Map<String, String> variables = Map.ofEntries(
                Map.entry("recipientName", recipientName),
                Map.entry("taskName", task.getTaskName()),
                Map.entry("eventTitle", event.getTitle()),
                Map.entry("eventCode", event.getEventCode()),
                Map.entry("dueDate", formattedDueDate),
                Map.entry("taskNotes", task.getNotes() != null ? task.getNotes() : ""),
                Map.entry("taskBoardLink", taskBoardLink),
                Map.entry("organizerName", organizerName),
                Map.entry("organizerEmail", organizerEmail),
                Map.entry("dashboardLink", baseUrl + "/dashboard"),
                Map.entry("eventUrl", baseUrl + "/events/" + event.getEventCode()),
                Map.entry("supportUrl", baseUrl + "/support"),
                Map.entry("currentYear", String.valueOf(java.time.Year.now().getValue())),
                Map.entry("logoUrl", baseUrl + "/BATbern_white_logo.svg")
        );

        String html = emailService.replaceVariables(contentHtml, variables);
        String subject = emailTemplateService.resolveSubject("task-reminder", localeStr)
                .map(s -> emailService.replaceVariables(s, variables))
                .orElseGet(() -> localeStr.equals("de")
                        ? "Aufgabenerinnerung: " + task.getTaskName() + " fällig morgen"
                        : "Task Reminder: " + task.getTaskName() + " due tomorrow");
        return new EmailContent(html, subject);
    }

    /**
     * Load HTML content from DB (with optional layout merge) or fall back to classpath.
     * Story 10.2 AC1: DB-first template loading.
     */
    private String loadHtmlContent(String templateKey, String localeStr, String classpathFallback) {
        Optional<EmailTemplate> dbTemplate = emailTemplateService.findByKeyAndLocale(templateKey, localeStr);
        if (dbTemplate.isPresent()) {
            String contentHtml = dbTemplate.get().getHtmlBody();
            String layoutKey = dbTemplate.get().getLayoutKey();
            if (layoutKey != null) {
                return emailTemplateService.mergeWithLayout(contentHtml, layoutKey, localeStr);
            }
            return contentHtml;
        }
        // Classpath fallback
        try {
            ClassPathResource resource = new ClassPathResource(classpathFallback);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Email template not found in DB or classpath: {}/{}", templateKey, localeStr);
            return "";
        }
    }
}
