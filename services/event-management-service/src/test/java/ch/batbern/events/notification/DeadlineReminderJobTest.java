package ch.batbern.events.notification;

import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeadlineReminderJob (Scheduled notification job)
 * Story BAT-7: Notifications API Consolidation
 *
 * RED PHASE (TDD): These tests will FAIL until DeadlineReminderJob is implemented.
 *
 * Tests cover:
 * - Scheduled execution (daily at 9 AM)
 * - Finding events with upcoming deadlines
 * - Sending reminders to registered attendees
 * - Logging and metrics
 */
@ExtendWith(MockitoExtension.class)
class DeadlineReminderJobTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private NotificationService notificationService;

    private DeadlineReminderJob deadlineReminderJob;

    @BeforeEach
    void setUp() {
        deadlineReminderJob = new DeadlineReminderJob(
                eventRepository,
                registrationRepository,
                notificationService
        );
    }

    /**
     * Should find events with deadlines in next 3 days
     */
    @Test
    void should_findUpcomingDeadlines_when_jobExecutes() {
        // Given
        Instant threeDaysFromNow = Instant.now().plus(3, ChronoUnit.DAYS);

        Event event = Event.builder()
                .eventCode("BATbern123")
                .title("Test Event")
                .registrationDeadline(threeDaysFromNow.minus(1, ChronoUnit.DAYS))
                .build();

        when(eventRepository.findByRegistrationDeadlineBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(event));
        when(registrationRepository.findUsernamesByEventCode("BATbern123"))
                .thenReturn(List.of("john.doe", "jane.smith"));

        // When
        deadlineReminderJob.sendDeadlineReminders();

        // Then
        verify(eventRepository).findByRegistrationDeadlineBetween(any(Instant.class), any(Instant.class));
    }

    /**
     * Should send reminders to all registered attendees
     */
    @Test
    void should_sendReminders_when_attendeesRegistered() {
        // Given
        Instant threeDaysFromNow = Instant.now().plus(3, ChronoUnit.DAYS);

        Event event = Event.builder()
                .eventCode("BATbern123")
                .title("Test Event")
                .registrationDeadline(threeDaysFromNow)
                .build();

        when(eventRepository.findByRegistrationDeadlineBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(event));
        when(registrationRepository.findUsernamesByEventCode("BATbern123"))
                .thenReturn(List.of("john.doe", "jane.smith"));

        // When
        deadlineReminderJob.sendDeadlineReminders();

        // Then
        verify(notificationService, times(2)).createAndSendEmailNotification(any(NotificationRequest.class));
    }

    /**
     * Should include deadline info in notification
     */
    @Test
    void should_includeDeadlineInfo_when_sendingNotification() {
        // Given
        Instant threeDaysFromNow = Instant.now().plus(3, ChronoUnit.DAYS);

        Event event = Event.builder()
                .eventCode("BATbern123")
                .title("Test Event")
                .registrationDeadline(threeDaysFromNow.minus(1, ChronoUnit.DAYS))
                .build();

        when(eventRepository.findByRegistrationDeadlineBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(event));
        when(registrationRepository.findUsernamesByEventCode("BATbern123"))
                .thenReturn(List.of("john.doe"));

        // When
        deadlineReminderJob.sendDeadlineReminders();

        // Then
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).createAndSendEmailNotification(captor.capture());

        NotificationRequest request = captor.getValue();
        assertThat(request.getRecipientUsername()).isEqualTo("john.doe");
        assertThat(request.getEventCode()).isEqualTo("BATbern123");
        assertThat(request.getType()).isEqualTo("DEADLINE_WARNING");
        assertThat(request.getPriority()).isEqualTo("HIGH");
    }

    /**
     * Should skip when no upcoming deadlines
     */
    @Test
    void should_skipSending_when_noUpcomingDeadlines() {
        // Given
        when(eventRepository.findByRegistrationDeadlineBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        // When
        deadlineReminderJob.sendDeadlineReminders();

        // Then
        verify(notificationService, never()).createAndSendEmailNotification(any(NotificationRequest.class));
    }

    /**
     * Should handle events with no registered attendees
     */
    @Test
    void should_handleGracefully_when_noAttendeesRegistered() {
        // Given
        Instant threeDaysFromNow = Instant.now().plus(3, ChronoUnit.DAYS);

        Event event = Event.builder()
                .eventCode("BATbern123")
                .title("Test Event")
                .registrationDeadline(threeDaysFromNow)
                .build();

        when(eventRepository.findByRegistrationDeadlineBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(event));
        when(registrationRepository.findUsernamesByEventCode("BATbern123"))
                .thenReturn(List.of()); // No attendees

        // When
        deadlineReminderJob.sendDeadlineReminders();

        // Then
        verify(notificationService, never()).createAndSendEmailNotification(any(NotificationRequest.class));
    }
}
