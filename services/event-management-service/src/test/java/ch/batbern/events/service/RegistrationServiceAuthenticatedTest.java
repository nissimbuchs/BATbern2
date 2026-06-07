package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.IncompleteProfileException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Part A — authenticated quick-register must reject users whose profile is
 * missing first or last name.
 *
 * 2026-05-18 incident: 20 BATbern59 registrations had blank attendee names
 * because the authenticated path trusted whatever was on user_profile.
 * Profiles created by JIT (email fallback) or pre-fix reconciliation came
 * through with empty strings, the registration snapshot copied them, and
 * confirmation emails arrived without greetings.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceAuthenticatedTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private UserApiClient userApiClient;

    @Mock
    private RegistrationEmailService registrationEmailService;

    @InjectMocks
    private RegistrationService registrationService;

    private Event event;

    @BeforeEach
    void setUp() {
        Instant eventDate = Instant.now().plus(30, ChronoUnit.DAYS);
        event = Event.builder()
                .id(UUID.randomUUID())
                .eventCode("BATbern59")
                .eventNumber(59)
                .title("BATbern 59")
                .date(eventDate)
                .registrationDeadline(eventDate.minus(7, ChronoUnit.DAYS))
                .venueCapacity(100)
                .organizerUsername("organizer")
                .workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .eventType(EventType.EVENING)
                .currentAttendeeCount(0)
                .build();

        when(eventRepository.findByEventCode("BATbern59")).thenReturn(Optional.of(event));
    }

    private UserResponse profile(String firstName, String lastName) {
        UserResponse u = new UserResponse();
        u.setId("user.tom");
        u.setEmail("tom@windshop.ch");
        u.setFirstName(firstName);
        u.setLastName(lastName);
        return u;
    }

    @Test
    void should_reject_when_profileFirstNameEmpty() {
        when(userApiClient.getUserByUsername("user.tom")).thenReturn(profile("", "Smith"));

        assertThatThrownBy(() ->
                registrationService.createRegistrationForAuthenticatedUser(
                        "BATbern59", "user.tom", "tom@windshop.ch"))
                .isInstanceOf(IncompleteProfileException.class)
                .extracting("missingFirstName", "missingLastName")
                .containsExactly(true, false);

        verify(registrationRepository, never()).save(any());
    }

    @Test
    void should_reject_when_profileLastNameEmpty() {
        when(userApiClient.getUserByUsername("user.tom")).thenReturn(profile("Tom", ""));

        assertThatThrownBy(() ->
                registrationService.createRegistrationForAuthenticatedUser(
                        "BATbern59", "user.tom", "tom@windshop.ch"))
                .isInstanceOf(IncompleteProfileException.class)
                .extracting("missingFirstName", "missingLastName")
                .containsExactly(false, true);

        verify(registrationRepository, never()).save(any());
    }

    @Test
    void should_reject_when_profileBothNamesEmpty() {
        when(userApiClient.getUserByUsername("user.tom")).thenReturn(profile("", ""));

        assertThatThrownBy(() ->
                registrationService.createRegistrationForAuthenticatedUser(
                        "BATbern59", "user.tom", "tom@windshop.ch"))
                .isInstanceOf(IncompleteProfileException.class)
                .extracting("missingFirstName", "missingLastName")
                .containsExactly(true, true);
    }

    @Test
    void should_reject_when_profileFirstNameBlank() {
        // Whitespace-only must be treated as missing.
        when(userApiClient.getUserByUsername("user.tom")).thenReturn(profile("   ", "Smith"));

        assertThatThrownBy(() ->
                registrationService.createRegistrationForAuthenticatedUser(
                        "BATbern59", "user.tom", "tom@windshop.ch"))
                .isInstanceOf(IncompleteProfileException.class);
    }

    @Test
    void should_succeed_when_profileComplete() {
        UserResponse u = profile("Tom", "Smith");
        when(userApiClient.getUserByUsername("user.tom")).thenReturn(u);
        when(registrationRepository.findByEventIdAndAttendeeUsername(any(), any()))
                .thenReturn(Optional.empty());
        when(registrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() ->
                registrationService.createRegistrationForAuthenticatedUser(
                        "BATbern59", "user.tom", "tom@windshop.ch"))
                .doesNotThrowAnyException();

        verify(registrationRepository).save(any());
    }

    @Test
    void should_notReject_when_profileMissingEntirely() {
        // No user_profile row at all (organizer-pre-loaded attendee path). The
        // existing graceful-fallback uses the JWT email; we explicitly do NOT
        // require a profile here since one might never have existed.
        when(userApiClient.getUserByUsername("user.tom"))
                .thenThrow(new ch.batbern.events.exception.UserNotFoundException("user.tom"));
        when(registrationRepository.findByEventIdAndAttendeeUsername(any(), any()))
                .thenReturn(Optional.empty());
        when(registrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() ->
                registrationService.createRegistrationForAuthenticatedUser(
                        "BATbern59", "user.tom", "tom@windshop.ch"))
                .doesNotThrowAnyException();
    }
}
