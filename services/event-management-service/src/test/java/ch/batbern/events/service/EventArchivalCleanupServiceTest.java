package ch.batbern.events.service;

import ch.batbern.events.repository.EventTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EventArchivalCleanupService (Story 10.18 AC8).
 *
 * Tests cover:
 * - Bulk task cancellation on archival (AC1)
 * - Waitlist registration cancellation (AC3)
 * - Notification dismissal (AC4)
 * - Idempotency - safe to call twice (AC6)
 * - Non-critical step failure does not propagate and tasks are still cancelled (AC5)
 */
@ExtendWith(MockitoExtension.class)
class EventArchivalCleanupServiceTest {

    @Mock
    private EventTaskRepository eventTaskRepository;

    @Mock
    private ArchivalBestEffortSteps bestEffortSteps;

    @InjectMocks
    private EventArchivalCleanupService service;

    private static final String EVENT_CODE = "BATbern42";
    private static final UUID EVENT_ID = UUID.randomUUID();

    @Test
    @DisplayName("cleanup() with open tasks → bulk cancel called with correct eventId (AC1)")
    void cleanup_withOpenTasks_callsCancelOpenTasksWithCorrectEventId() {
        when(eventTaskRepository.cancelOpenTasksForEvent(EVENT_ID)).thenReturn(2);
        when(bestEffortSteps.cancelWaitlistRegistrations(eq(EVENT_ID), anyString())).thenReturn(0);
        when(bestEffortSteps.dismissNotifications(EVENT_CODE)).thenReturn(0);

        service.cleanup(EVENT_ID, EVENT_CODE);

        verify(eventTaskRepository, times(1)).cancelOpenTasksForEvent(EVENT_ID);
    }

    @Test
    @DisplayName("cleanup() with waitlisted registrations → best-effort step called with 'waitlist' (AC3)")
    void cleanup_withWaitlistedRegistrations_callsBestEffortCancelWaitlist() {
        when(eventTaskRepository.cancelOpenTasksForEvent(EVENT_ID)).thenReturn(0);
        when(bestEffortSteps.cancelWaitlistRegistrations(EVENT_ID, "waitlist")).thenReturn(1);
        when(bestEffortSteps.dismissNotifications(EVENT_CODE)).thenReturn(0);

        service.cleanup(EVENT_ID, EVENT_CODE);

        verify(bestEffortSteps, times(1)).cancelWaitlistRegistrations(EVENT_ID, "waitlist");
    }

    @Test
    @DisplayName("cleanup() → notifications dismissed via best-effort step (AC4)")
    void cleanup_dismissesNotifications() {
        when(eventTaskRepository.cancelOpenTasksForEvent(EVENT_ID)).thenReturn(0);
        when(bestEffortSteps.cancelWaitlistRegistrations(eq(EVENT_ID), anyString())).thenReturn(0);
        when(bestEffortSteps.dismissNotifications(EVENT_CODE)).thenReturn(3);

        service.cleanup(EVENT_ID, EVENT_CODE);

        verify(bestEffortSteps, times(1)).dismissNotifications(EVENT_CODE);
    }

    @Test
    @DisplayName("cleanup() is idempotent — calling twice produces no exceptions (AC6)")
    void cleanup_calledTwice_noExceptions() {
        when(eventTaskRepository.cancelOpenTasksForEvent(EVENT_ID)).thenReturn(0);
        when(bestEffortSteps.cancelWaitlistRegistrations(eq(EVENT_ID), anyString())).thenReturn(0);
        when(bestEffortSteps.dismissNotifications(EVENT_CODE)).thenReturn(0);

        assertThatNoException().isThrownBy(() -> {
            service.cleanup(EVENT_ID, EVENT_CODE);
            service.cleanup(EVENT_ID, EVENT_CODE);
        });
    }

    @Test
    @DisplayName("cleanup() notification step throws → no exception propagates; task cancel was called (AC5)")
    void cleanup_notificationStepThrows_doesNotPropagateException_andTaskCancelWasCalled() {
        when(eventTaskRepository.cancelOpenTasksForEvent(EVENT_ID)).thenReturn(2);
        when(bestEffortSteps.cancelWaitlistRegistrations(eq(EVENT_ID), anyString())).thenReturn(0);
        doThrow(new RuntimeException("notification failure"))
                .when(bestEffortSteps).dismissNotifications(EVENT_CODE);

        assertThatNoException().isThrownBy(() -> service.cleanup(EVENT_ID, EVENT_CODE));

        // Step 1 (task cancellation) was still executed — it is unaffected by step 3 failure
        verify(eventTaskRepository, times(1)).cancelOpenTasksForEvent(EVENT_ID);
    }

    @Test
    @DisplayName("cleanup() registration step throws → no exception propagates; task cancel was called (AC5)")
    void cleanup_registrationStepThrows_doesNotPropagateException_andTaskCancelWasCalled() {
        when(eventTaskRepository.cancelOpenTasksForEvent(EVENT_ID)).thenReturn(2);
        doThrow(new RuntimeException("registration failure"))
                .when(bestEffortSteps).cancelWaitlistRegistrations(eq(EVENT_ID), anyString());
        when(bestEffortSteps.dismissNotifications(EVENT_CODE)).thenReturn(0);

        assertThatNoException().isThrownBy(() -> service.cleanup(EVENT_ID, EVENT_CODE));

        // Step 1 (task cancellation) was still executed — it is unaffected by step 2 failure
        verify(eventTaskRepository, times(1)).cancelOpenTasksForEvent(EVENT_ID);
    }

    @Test
    @DisplayName("cleanup() registration step throws → notification step still attempted (AC5)")
    void cleanup_registrationStepThrows_notificationStepStillAttempted() {
        when(eventTaskRepository.cancelOpenTasksForEvent(EVENT_ID)).thenReturn(2);
        doThrow(new RuntimeException("registration failure"))
                .when(bestEffortSteps).cancelWaitlistRegistrations(eq(EVENT_ID), anyString());
        when(bestEffortSteps.dismissNotifications(EVENT_CODE)).thenReturn(0);

        service.cleanup(EVENT_ID, EVENT_CODE);

        verify(bestEffortSteps, times(1)).dismissNotifications(EVENT_CODE);
    }

    @Test
    @DisplayName("cleanup() with zero cancelledTasks → best-effort steps still called (AC1, AC3, AC4 idempotency)")
    void cleanup_withZeroTasks_stillCallsBestEffortSteps() {
        when(eventTaskRepository.cancelOpenTasksForEvent(EVENT_ID)).thenReturn(0);
        when(bestEffortSteps.cancelWaitlistRegistrations(eq(EVENT_ID), anyString())).thenReturn(0);
        when(bestEffortSteps.dismissNotifications(EVENT_CODE)).thenReturn(0);

        service.cleanup(EVENT_ID, EVENT_CODE);

        verify(eventTaskRepository, times(1)).cancelOpenTasksForEvent(EVENT_ID);
        verify(bestEffortSteps, times(1)).cancelWaitlistRegistrations(eq(EVENT_ID), anyString());
        verify(bestEffortSteps, times(1)).dismissNotifications(EVENT_CODE);
    }
}
