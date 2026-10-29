package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.shared.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** #1119 (A3): the deregistration link mail follows the attendee's language, not always German. */
@ExtendWith(MockitoExtension.class)
class DeregistrationEmailServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private UserApiClient userApiClient;

    private DeregistrationEmailService service;
    private Registration registration;
    private Event event;

    @BeforeEach
    void setUp() {
        service = new DeregistrationEmailService(emailService, emailTemplateService, userApiClient);
        registration = Registration.builder()
                .attendeeUsername("jane.doe")
                .attendeeEmail("jane@example.com")
                .attendeeFirstName("Jane")
                .build();
        event = Event.builder().eventCode("BATbern99").title("Test Event").date(Instant.now()).build();
        lenient().when(emailTemplateService.findByKeyAndLocale(anyString(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void should_useEnglishTemplate_when_attendeePrefersEnglish() {
        when(userApiClient.getPreferredLanguage("jane.doe")).thenReturn("en");

        service.sendDeregistrationLinkEmail(registration, event, "https://www.batbern.ch/deregister?t=x");

        verify(emailTemplateService).findByKeyAndLocale("deregistration-link", "en");
    }

    @Test
    void should_useGermanTemplate_when_attendeeHasNoPreference() {
        when(userApiClient.getPreferredLanguage("jane.doe")).thenReturn(null);

        service.sendDeregistrationLinkEmail(registration, event, "https://www.batbern.ch/deregister?t=x");

        verify(emailTemplateService).findByKeyAndLocale("deregistration-link", "de");
    }

    @Test
    void should_useGermanTemplate_when_attendeePrefersSwissGerman() {
        when(userApiClient.getPreferredLanguage("jane.doe")).thenReturn("gsw-be");

        service.sendDeregistrationLinkEmail(registration, event, "https://www.batbern.ch/deregister?t=x");

        verify(emailTemplateService).findByKeyAndLocale("deregistration-link", "de");
    }
}
