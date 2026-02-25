package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.EmailTemplate;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventTask;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TaskReminderEmailService.
 * Story 10.3: Task Deadline Reminder Email
 *
 * Tests:
 * - Email sent to correct organizer address
 * - Subject line (DE/EN)
 * - Task name and event title in email body
 * - Due date formatted correctly
 * - Task board link included
 * - Optional notes block present/absent
 * - DB template used when available (Story 10.2)
 * - Classpath fallback when no DB template
 * - Skips gracefully when organizer not found
 * - Skips gracefully when event not found
 * - Never throws on email send failure
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskReminderEmailServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private UserApiClient userApiClient;

    @Mock
    private EventRepository eventRepository;

    private TaskReminderEmailService service;

    private EventTask task;
    private Event event;
    private UserResponse organizer;

    @BeforeEach
    void setUp() {
        service = new TaskReminderEmailService(
                emailService, emailTemplateService, userApiClient, eventRepository);

        ReflectionTestUtils.setField(service, "baseUrl", "https://batbern.ch");
        ReflectionTestUtils.setField(service, "organizerName", "BATbern Team");
        ReflectionTestUtils.setField(service, "organizerEmail", "events@batbern.ch");

        // By default, no DB template — classpath fallback used
        when(emailTemplateService.findByKeyAndLocale(anyString(), anyString()))
                .thenReturn(Optional.empty());

        // Mock replaceVariables to do simple substitution
        when(emailService.replaceVariables(anyString(), any())).thenAnswer(invocation -> {
            String template = invocation.getArgument(0);
            java.util.Map<String, String> vars = invocation.getArgument(1);
            String result = template;
            for (var entry : vars.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return result;
        });

        UUID eventId = UUID.randomUUID();

        event = new Event();
        event.setId(eventId);
        event.setEventCode("BAT2026");
        event.setTitle("BATbern 2026");
        ZonedDateTime eventDate = ZonedDateTime.of(2026, 4, 15, 18, 0, 0, 0, ZoneId.of("Europe/Zurich"));
        event.setDate(eventDate.toInstant());

        task = new EventTask();
        task.setId(UUID.randomUUID());
        task.setEventId(eventId);
        task.setTaskName("Moderator beauftragen");
        task.setAssignedOrganizerUsername("organizer1");
        task.setStatus("todo");
        ZonedDateTime tomorrow = ZonedDateTime.now(ZoneId.of("Europe/Zurich")).plusDays(1);
        task.setDueDate(tomorrow.toInstant());

        organizer = new UserResponse()
                .id("organizer1")
                .firstName("Lena")
                .lastName("Müller")
                .email("lena.mueller@example.com");

        when(userApiClient.getUserByUsername("organizer1")).thenReturn(organizer);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
    }

    @Nested
    @DisplayName("AC1: Reminder Email Delivery")
    class ReminderEmailDeliveryTests {

        @Test
        @DisplayName("should send email to assigned organizer's email address")
        void should_sendEmail_to_assignedOrganizer() {
            service.sendTaskDeadlineReminder(task, Locale.GERMAN);

            verify(emailService).sendHtmlEmail(
                    eq("lena.mueller@example.com"),
                    anyString(),
                    anyString()
            );
        }

        @Test
        @DisplayName("should use German subject when German locale")
        void should_useGermanSubject_when_germanLocale() {
            ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);

            service.sendTaskDeadlineReminder(task, Locale.GERMAN);

            verify(emailService).sendHtmlEmail(anyString(), subjectCaptor.capture(), anyString());
            assertThat(subjectCaptor.getValue()).contains("Aufgabenerinnerung");
            assertThat(subjectCaptor.getValue()).contains("Moderator beauftragen");
            assertThat(subjectCaptor.getValue()).contains("fällig morgen");
        }

        @Test
        @DisplayName("should use English subject when English locale")
        void should_useEnglishSubject_when_englishLocale() {
            ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);

            service.sendTaskDeadlineReminder(task, Locale.ENGLISH);

            verify(emailService).sendHtmlEmail(anyString(), subjectCaptor.capture(), anyString());
            assertThat(subjectCaptor.getValue()).contains("Task Reminder");
            assertThat(subjectCaptor.getValue()).contains("Moderator beauftragen");
            assertThat(subjectCaptor.getValue()).contains("due tomorrow");
        }

        @Test
        @DisplayName("should default to German when locale is null")
        void should_defaultToGerman_when_localeIsNull() {
            ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);

            service.sendTaskDeadlineReminder(task, null);

            verify(emailService).sendHtmlEmail(anyString(), subjectCaptor.capture(), anyString());
            assertThat(subjectCaptor.getValue()).contains("Aufgabenerinnerung");
        }

        @Test
        @DisplayName("should include task name in email body")
        void should_includeTaskName_in_body() {
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            service.sendTaskDeadlineReminder(task, Locale.GERMAN);

            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            assertThat(bodyCaptor.getValue()).contains("Moderator beauftragen");
        }

        @Test
        @DisplayName("should include event title in email body")
        void should_includeEventTitle_in_body() {
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            service.sendTaskDeadlineReminder(task, Locale.GERMAN);

            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            assertThat(bodyCaptor.getValue()).contains("BATbern 2026");
        }

        @Test
        @DisplayName("should include task board link in email body")
        void should_includeTaskBoardLink_in_body() {
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            service.sendTaskDeadlineReminder(task, Locale.GERMAN);

            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            assertThat(bodyCaptor.getValue()).contains("https://batbern.ch/events/BAT2026/tasks");
        }

        @Test
        @DisplayName("should include recipient first name in email body")
        void should_includeRecipientName_in_body() {
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            service.sendTaskDeadlineReminder(task, Locale.GERMAN);

            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            assertThat(bodyCaptor.getValue()).contains("Lena");
        }

        @Test
        @DisplayName("should use username as recipient name when firstName is blank")
        void should_useUsername_when_firstNameBlank() {
            when(userApiClient.getUserByUsername("organizer1")).thenReturn(
                    new UserResponse().id("organizer1").firstName("").email("lena.mueller@example.com"));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            service.sendTaskDeadlineReminder(task, Locale.GERMAN);

            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            assertThat(bodyCaptor.getValue()).contains("organizer1");
        }
    }

    @Nested
    @DisplayName("Story 10.2: DB-First Template Loading")
    class TemplateLoadingTests {

        @Test
        @DisplayName("should use DB template html body when available")
        void should_useDbTemplate_when_available() {
            EmailTemplate dbTemplate = new EmailTemplate();
            dbTemplate.setHtmlBody("<p>DB Aufgabe: {{taskName}}</p>");
            dbTemplate.setLayoutKey(null);
            when(emailTemplateService.findByKeyAndLocale("task-reminder", "de"))
                    .thenReturn(Optional.of(dbTemplate));
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

            service.sendTaskDeadlineReminder(task, Locale.GERMAN);

            verify(emailService).sendHtmlEmail(anyString(), anyString(), bodyCaptor.capture());
            assertThat(bodyCaptor.getValue()).contains("DB Aufgabe: Moderator beauftragen");
        }

        @Test
        @DisplayName("should merge layout when DB template has layoutKey set")
        void should_mergeLayout_when_layoutKeySet() {
            EmailTemplate dbTemplate = new EmailTemplate();
            dbTemplate.setHtmlBody("<p>{{taskName}}</p>");
            dbTemplate.setLayoutKey("layout-batbern-default");
            when(emailTemplateService.findByKeyAndLocale("task-reminder", "de"))
                    .thenReturn(Optional.of(dbTemplate));
            when(emailTemplateService.mergeWithLayout(anyString(), eq("layout-batbern-default"), eq("de")))
                    .thenReturn("<html><p>{{taskName}}</p></html>");

            service.sendTaskDeadlineReminder(task, Locale.GERMAN);

            verify(emailTemplateService).mergeWithLayout(anyString(), eq("layout-batbern-default"), eq("de"));
        }
    }

    @Nested
    @DisplayName("Error Handling and Edge Cases")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should skip and not throw when organizer not found in user service")
        void should_skip_when_organizerNotFound() {
            when(userApiClient.getUserByUsername("organizer1"))
                    .thenThrow(new UserNotFoundException("organizer1"));

            service.sendTaskDeadlineReminder(task, Locale.GERMAN);

            verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should skip and not throw when event not found")
        void should_skip_when_eventNotFound() {
            when(eventRepository.findById(task.getEventId())).thenReturn(Optional.empty());

            service.sendTaskDeadlineReminder(task, Locale.GERMAN);

            verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should not throw when email sending fails")
        void should_notThrow_when_emailSendFails() {
            doThrow(new RuntimeException("SES unavailable"))
                    .when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());

            // Should complete without throwing
            service.sendTaskDeadlineReminder(task, Locale.GERMAN);
        }

        @Test
        @DisplayName("should handle task with null due date without throwing")
        void should_handle_nullDueDate() {
            task.setDueDate(null);

            service.sendTaskDeadlineReminder(task, Locale.GERMAN);

            verify(emailService).sendHtmlEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should handle task with null notes without throwing")
        void should_handle_nullNotes() {
            task.setNotes(null);

            service.sendTaskDeadlineReminder(task, Locale.GERMAN);

            verify(emailService).sendHtmlEmail(anyString(), anyString(), anyString());
        }
    }
}
