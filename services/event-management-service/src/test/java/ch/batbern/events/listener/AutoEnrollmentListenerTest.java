package ch.batbern.events.listener;

import ch.batbern.events.domain.Event;
import ch.batbern.events.event.EventCreatedEvent;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AutoEnrollmentListener.
 *
 * The listener delegates enrollment to RegistrationService.enrollStakeholders().
 * These tests verify the listener's orchestration: event lookup, delegation, and error handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AutoEnrollmentListener Unit Tests")
class AutoEnrollmentListenerTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RegistrationService registrationService;

    @InjectMocks
    private AutoEnrollmentListener listener;

    private static final String EVENT_CODE = "BATbern99";
    private static final UUID EVENT_ID = UUID.randomUUID();

    private Event event;
    private EventCreatedEvent domainEvent;

    @BeforeEach
    void setUp() {
        event = new Event();
        event.setId(EVENT_ID);
        event.setEventCode(EVENT_CODE);

        domainEvent = new EventCreatedEvent(
                EVENT_ID, EVENT_CODE, "Test Event", 99,
                Instant.now(), Instant.now(),
                "Venue", "Address", 100,
                "CREATED", "organizer.user", "Description",
                "organizer.user"
        );
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("should delegate to enrollStakeholders when event is found")
        void should_delegateToEnrollStakeholders_when_eventFound() {
            when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
            when(registrationService.enrollStakeholders(event))
                    .thenReturn(new RegistrationService.EnrollmentSummary(3, 1));

            listener.onEventCreated(domainEvent);

            verify(registrationService).enrollStakeholders(event);
        }

        @Test
        @DisplayName("should not throw when enrollStakeholders returns zero enrolled")
        void should_notThrow_when_noUsersToEnroll() {
            when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
            when(registrationService.enrollStakeholders(event))
                    .thenReturn(new RegistrationService.EnrollmentSummary(0, 0));

            listener.onEventCreated(domainEvent); // must not throw
        }
    }

    @Nested
    @DisplayName("Error resilience")
    class ErrorResilience {

        @Test
        @DisplayName("should log warning and not throw when event is not found")
        void should_logWarning_when_eventNotFound() {
            when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.empty());

            listener.onEventCreated(domainEvent); // must not throw

            verify(registrationService, never()).enrollStakeholders(any());
        }

        @Test
        @DisplayName("should log warning and not throw when enrollStakeholders throws")
        void should_logWarning_when_enrollStakeholdersFails() {
            when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
            when(registrationService.enrollStakeholders(event))
                    .thenThrow(new RuntimeException("User service unavailable"));

            listener.onEventCreated(domainEvent); // must not throw
        }
    }
}
