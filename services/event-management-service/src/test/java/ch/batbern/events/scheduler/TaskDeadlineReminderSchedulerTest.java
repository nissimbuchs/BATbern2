package ch.batbern.events.scheduler;

import ch.batbern.events.domain.EventTask;
import ch.batbern.events.repository.EventTaskRepository;
import ch.batbern.events.service.TaskReminderEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TaskDeadlineReminderScheduler.
 * Story 10.3: Task Deadline Reminder Email
 *
 * Tests:
 * - Sends one reminder per task due tomorrow
 * - Sends multiple reminders for multiple tasks
 * - No emails when no tasks are due
 * - Tasks without assignee are not returned by query (filtered by repository)
 * - Uses German locale for all reminders
 */
@ExtendWith(MockitoExtension.class)
class TaskDeadlineReminderSchedulerTest {

    @Mock
    private EventTaskRepository eventTaskRepository;

    @Mock
    private TaskReminderEmailService taskReminderEmailService;

    private TaskDeadlineReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TaskDeadlineReminderScheduler(eventTaskRepository, taskReminderEmailService);
    }

    private EventTask makeTask(String taskName, String assignee) {
        EventTask task = new EventTask();
        task.setId(UUID.randomUUID());
        task.setEventId(UUID.randomUUID());
        task.setTaskName(taskName);
        task.setAssignedOrganizerUsername(assignee);
        task.setStatus("todo");
        ZonedDateTime tomorrow = ZonedDateTime.now(ZoneId.of("Europe/Zurich")).plusDays(1);
        task.setDueDate(tomorrow.toInstant());
        return task;
    }

    @Nested
    @DisplayName("AC1: Reminder dispatch")
    class ReminderDispatchTests {

        @Test
        @DisplayName("should send one reminder when one task is due tomorrow")
        void should_sendOneReminder_when_oneTaskDueTomorrow() {
            EventTask task = makeTask("Venue booking", "organizer1");
            when(eventTaskRepository.findTasksDueForReminder(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(task));

            scheduler.processReminders();

            verify(taskReminderEmailService, times(1))
                    .sendTaskDeadlineReminder(eq(task), eq(Locale.GERMAN));
        }

        @Test
        @DisplayName("should send multiple reminders when multiple tasks are due tomorrow")
        void should_sendMultipleReminders_when_multipleTasksDueTomorrow() {
            EventTask task1 = makeTask("Venue booking", "organizer1");
            EventTask task2 = makeTask("Newsletter announcement", "organizer2");
            EventTask task3 = makeTask("Catering coordination", "organizer1");
            when(eventTaskRepository.findTasksDueForReminder(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(task1, task2, task3));

            scheduler.processReminders();

            verify(taskReminderEmailService, times(3))
                    .sendTaskDeadlineReminder(any(EventTask.class), eq(Locale.GERMAN));
        }

        @Test
        @DisplayName("should send no emails when no tasks are due tomorrow")
        void should_sendNoEmails_when_noTasksDueTomorrow() {
            when(eventTaskRepository.findTasksDueForReminder(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of());

            scheduler.processReminders();

            verify(taskReminderEmailService, never())
                    .sendTaskDeadlineReminder(any(EventTask.class), any(Locale.class));
        }

        @Test
        @DisplayName("should query the correct tomorrow window in Swiss timezone")
        void should_queryTomorrowWindow_in_swissTimezone() {
            ZoneId swissZone = ZoneId.of("Europe/Zurich");
            Instant expectedFrom = java.time.LocalDate.now(swissZone).plusDays(1)
                    .atStartOfDay(swissZone).toInstant();
            Instant expectedTo = java.time.LocalDate.now(swissZone).plusDays(2)
                    .atStartOfDay(swissZone).toInstant();
            when(eventTaskRepository.findTasksDueForReminder(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of());
            ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
            ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);

            scheduler.processReminders();

            verify(eventTaskRepository).findTasksDueForReminder(fromCaptor.capture(), toCaptor.capture());
            // Allow 1 second tolerance for test execution time
            assertThat(fromCaptor.getValue()).isBetween(
                    expectedFrom.minusSeconds(1), expectedFrom.plusSeconds(1));
            assertThat(toCaptor.getValue()).isBetween(
                    expectedTo.minusSeconds(1), expectedTo.plusSeconds(1));
        }

        @Test
        @DisplayName("should use German locale for all reminders")
        void should_useGermanLocale_for_allReminders() {
            EventTask task = makeTask("Test task", "organizer1");
            when(eventTaskRepository.findTasksDueForReminder(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(task));

            scheduler.processReminders();

            verify(taskReminderEmailService).sendTaskDeadlineReminder(any(EventTask.class), eq(Locale.GERMAN));
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should not throw when repository query fails")
        void should_notThrow_when_repositoryFails() {
            when(eventTaskRepository.findTasksDueForReminder(any(Instant.class), any(Instant.class)))
                    .thenThrow(new RuntimeException("Database connection error"));

            // Should complete without throwing
            scheduler.processReminders();
        }
    }
}
