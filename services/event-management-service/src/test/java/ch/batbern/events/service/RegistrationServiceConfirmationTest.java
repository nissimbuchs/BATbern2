package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Double opt-in completion: once a pending registration is confirmed (link click or ACCEPT reply),
 * the attendee gets a "registration confirmed" mail carrying the calendar invite. The pending mail
 * no longer carries it, so this mail is the attendee's only signal that the place is booked.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceConfirmationTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserApiClient userApiClient;

    @Mock
    private RegistrationEmailService registrationEmailService;

    @InjectMocks
    private RegistrationService registrationService;

    private final Event event = Event.builder()
            .id(UUID.randomUUID())
            .eventCode("BATbern142")
            .title("Test Event")
            .build();

    private Registration registration(String status) {
        return Registration.builder()
                .id(UUID.randomUUID())
                .eventId(event.getId())
                .registrationCode("BATbern142-reg-abc")
                .attendeeUsername("john.doe")
                .attendeeEmail("john.doe@example.com")
                .status(status)
                .build();
    }

    private final UserResponse user = new UserResponse()
            .id("john.doe").firstName("John").lastName("Doe").email("john.doe@example.com");

    @Test
    void should_sendConfirmedEmailInPreferredLanguage_when_notifyRegistrationConfirmed() {
        Registration registration = registration("confirmed");
        when(userApiClient.getUserByUsername("john.doe")).thenReturn(user);
        when(userApiClient.getPreferredLanguage("john.doe")).thenReturn("en");

        registrationService.notifyRegistrationConfirmed(registration, event);

        verify(registrationEmailService).sendRegistrationConfirmed(registration, user, event, Locale.ENGLISH);
    }

    @Test
    void should_defaultToGerman_when_preferredLanguageUnknown() {
        Registration registration = registration("confirmed");
        when(userApiClient.getUserByUsername("john.doe")).thenReturn(user);
        when(userApiClient.getPreferredLanguage("john.doe")).thenReturn(null);

        registrationService.notifyRegistrationConfirmed(registration, event);

        verify(registrationEmailService).sendRegistrationConfirmed(registration, user, event, Locale.GERMAN);
    }

    @Test
    void should_notFailConfirmation_when_userLookupFails() {
        Registration registration = registration("confirmed");
        when(userApiClient.getUserByUsername("john.doe")).thenThrow(new RuntimeException("CUMS down"));

        assertThatCode(() -> registrationService.notifyRegistrationConfirmed(registration, event))
                .doesNotThrowAnyException();
        verify(registrationEmailService, never()).sendRegistrationConfirmed(any(), any(), any(), any());
    }

    @Test
    void should_confirmAndSendConfirmedEmail_when_acceptReplyReceived() {
        Registration registration = registration("registered");
        when(registrationRepository.findByAttendeeEmailAndEventCode("john.doe@example.com", "BATbern142"))
                .thenReturn(Optional.of(registration));
        when(eventRepository.findByEventCode("BATbern142")).thenReturn(Optional.of(event));
        when(userApiClient.getUserByUsername("john.doe")).thenReturn(user);
        when(userApiClient.getPreferredLanguage("john.doe")).thenReturn("de");

        registrationService.confirmByEmail("john.doe@example.com", "BATbern142");

        assertThat(registration.getStatus()).isEqualTo("confirmed");
        verify(registrationRepository).save(registration);
        verify(registrationEmailService).sendRegistrationConfirmed(registration, user, event, Locale.GERMAN);
    }

    @Test
    void should_notResendConfirmedEmail_when_acceptReplyForAlreadyConfirmedRegistration() {
        Registration registration = registration("confirmed");
        when(registrationRepository.findByAttendeeEmailAndEventCode("john.doe@example.com", "BATbern142"))
                .thenReturn(Optional.of(registration));

        registrationService.confirmByEmail("john.doe@example.com", "BATbern142");

        verify(registrationRepository, never()).save(any());
        verify(registrationEmailService, never()).sendRegistrationConfirmed(any(), any(), any(), any());
    }
}
