package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SpeakerAutoRegistrationService}.
 *
 * <p>Covers the happy path and the skip conditions enumerated in the spec
 * ({@code _bmad-output/implementation-artifacts/spec-auto-participant-email-aliases-excel-export.md},
 * F1): idempotency, past-event skip, user-not-found skip, and capacity bypass.
 */
@ExtendWith(MockitoExtension.class)
class SpeakerAutoRegistrationServiceTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserApiClient userApiClient;

    @InjectMocks
    private SpeakerAutoRegistrationService service;

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final String USERNAME = "test.speaker";
    private static final String TRIGGER = "POOL_ACCEPTED";

    private Event futureEvent;

    @BeforeEach
    void setUp() {
        futureEvent = Event.builder()
                .id(EVENT_ID)
                .eventCode("BATbern99")
                .date(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();
    }

    @Test
    @DisplayName("Happy path: creates confirmed Registration with metadata.autoRegisteredFrom")
    void should_createRegistrationRow_when_speakerNotYetRegistered() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(futureEvent));
        when(registrationRepository.findByEventIdAndAttendeeUsername(EVENT_ID, USERNAME))
                .thenReturn(Optional.empty());
        when(userApiClient.getUserByUsername(USERNAME)).thenReturn(testUser(USERNAME));
        when(registrationRepository.existsByRegistrationCode(any())).thenReturn(false);
        when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.autoRegisterIfAbsent(EVENT_ID, USERNAME, TRIGGER);

        ArgumentCaptor<Registration> captor = ArgumentCaptor.forClass(Registration.class);
        verify(registrationRepository, times(1)).save(captor.capture());

        Registration saved = captor.getValue();
        assertThat(saved.getAttendeeUsername()).isEqualTo(USERNAME);
        assertThat(saved.getEventId()).isEqualTo(EVENT_ID);
        assertThat(saved.getStatus()).isEqualTo("confirmed");
        assertThat(saved.getAttendeeFirstName()).isEqualTo("Test");
        assertThat(saved.getAttendeeLastName()).isEqualTo("Speaker");
        assertThat(saved.getAttendeeEmail()).isEqualTo("test.speaker@example.com");
        assertThat(saved.getAttendeeCompanyId()).isEqualTo("TestCo");
        assertThat(saved.getMetadata()).containsEntry("autoRegisteredFrom", TRIGGER);
        assertThat(saved.getRegistrationCode()).startsWith("BATbern99-reg-");
        assertThat(saved.getDeregistrationToken()).isNotNull();
        assertThat(saved.getRegistrationDate()).isNotNull();
    }

    @Test
    @DisplayName("Idempotent: existing registration → no save, no UserApiClient call")
    void should_doNothing_when_speakerAlreadyRegistered() {
        Registration existing = Registration.builder()
                .eventId(EVENT_ID)
                .attendeeUsername(USERNAME)
                .status("registered")
                .build();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(futureEvent));
        when(registrationRepository.findByEventIdAndAttendeeUsername(EVENT_ID, USERNAME))
                .thenReturn(Optional.of(existing));

        service.autoRegisterIfAbsent(EVENT_ID, USERNAME, TRIGGER);

        verify(registrationRepository, never()).save(any(Registration.class));
        verify(userApiClient, never()).getUserByUsername(any());
    }

    @Test
    @DisplayName("Past event: skip silently (no save, no UserApiClient call)")
    void should_skip_when_eventInPast() {
        Event pastEvent = Event.builder()
                .id(EVENT_ID)
                .eventCode("BATbern1")
                .date(Instant.now().minus(7, ChronoUnit.DAYS))
                .build();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(pastEvent));

        service.autoRegisterIfAbsent(EVENT_ID, USERNAME, TRIGGER);

        verify(registrationRepository, never()).save(any(Registration.class));
        verify(userApiClient, never()).getUserByUsername(any());
    }

    @Test
    @DisplayName("UserApiClient 404 → graceful skip, no exception propagated")
    void should_skipGracefully_when_userNotFound() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(futureEvent));
        when(registrationRepository.findByEventIdAndAttendeeUsername(EVENT_ID, USERNAME))
                .thenReturn(Optional.empty());
        when(userApiClient.getUserByUsername(USERNAME))
                .thenThrow(new UserNotFoundException(USERNAME));

        service.autoRegisterIfAbsent(EVENT_ID, USERNAME, TRIGGER);

        verify(registrationRepository, never()).save(any(Registration.class));
    }

    @Test
    @DisplayName("UserApiClient 5xx / network → graceful skip (review patch #2)")
    void should_skipGracefully_when_cumsTransientFailure() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(futureEvent));
        when(registrationRepository.findByEventIdAndAttendeeUsername(EVENT_ID, USERNAME))
                .thenReturn(Optional.empty());
        when(userApiClient.getUserByUsername(USERNAME))
                .thenThrow(new RuntimeException("simulated CUMS 503"));

        // Must NOT bubble; the speaker workflow's caller relies on this absorbing failures.
        service.autoRegisterIfAbsent(EVENT_ID, USERNAME, TRIGGER);

        verify(registrationRepository, never()).save(any(Registration.class));
    }

    @Test
    @DisplayName("Missing event → silent skip (workflow must not fail because event vanished)")
    void should_skip_when_eventNotFound() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        service.autoRegisterIfAbsent(EVENT_ID, USERNAME, TRIGGER);

        verify(registrationRepository, never()).save(any(Registration.class));
    }

    @Test
    @DisplayName("Capacity bypass: speakers register even when registration_capacity is reached")
    void should_bypassCapacity_when_autoRegisteringSpeaker() {
        // Registration_capacity is intentionally NOT consulted on this path — speakers are
        // committed by organizers, not registering. We model this by simply asserting that
        // the service does not call any capacity-related method on the repository. The
        // strongest signal is that the only RegistrationRepository methods invoked are
        // findByEventIdAndAttendeeUsername (idempotency check), existsByRegistrationCode
        // (code-collision check), and save (the actual persist).
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(futureEvent));
        when(registrationRepository.findByEventIdAndAttendeeUsername(EVENT_ID, USERNAME))
                .thenReturn(Optional.empty());
        when(userApiClient.getUserByUsername(USERNAME)).thenReturn(testUser(USERNAME));
        when(registrationRepository.existsByRegistrationCode(any())).thenReturn(false);
        when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.autoRegisterIfAbsent(EVENT_ID, USERNAME, TRIGGER);

        verify(registrationRepository, never()).countByEventIdAndStatusIn(any(), any());
        verify(registrationRepository, never()).getNextWaitlistPosition(any());
        ArgumentCaptor<Registration> captor = ArgumentCaptor.forClass(Registration.class);
        verify(registrationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("confirmed");
        assertThat(captor.getValue().getWaitlistPosition()).isNull();
    }

    @Test
    @DisplayName("Trigger source is recorded verbatim (other valid triggers)")
    void should_storeTriggerSourceVerbatim() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(futureEvent));
        when(registrationRepository.findByEventIdAndAttendeeUsername(EVENT_ID, USERNAME))
                .thenReturn(Optional.empty());
        when(userApiClient.getUserByUsername(USERNAME)).thenReturn(testUser(USERNAME));
        when(registrationRepository.existsByRegistrationCode(any())).thenReturn(false);
        when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.autoRegisterIfAbsent(EVENT_ID, USERNAME, "SESSION_CO_SPEAKER");

        ArgumentCaptor<Registration> captor = ArgumentCaptor.forClass(Registration.class);
        verify(registrationRepository).save(captor.capture());
        assertThat(captor.getValue().getMetadata())
                .containsEntry("autoRegisteredFrom", "SESSION_CO_SPEAKER");
    }

    private UserResponse testUser(String username) {
        return new UserResponse()
                .id(username)
                .firstName("Test")
                .lastName("Speaker")
                .email(username + "@example.com")
                .companyId("TestCo");
    }
}
