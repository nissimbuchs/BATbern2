package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RegistrationResendService (unconfirmed-registration auto-resend job).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegistrationResendService Tests")
class RegistrationResendServiceTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ConfirmationTokenService confirmationTokenService;

    @Mock
    private RegistrationEmailService registrationEmailService;

    @Mock
    private UserApiClient userApiClient;

    private RegistrationResendService service;

    @BeforeEach
    void setUp() {
        // enabled, resend after 48h, max 2 attempts, cleanup 120h, base url
        service = new RegistrationResendService(
                registrationRepository, eventRepository, confirmationTokenService,
                registrationEmailService, userApiClient,
                true, 48, 2, 120, "https://batbern.ch");
    }

    @Test
    @DisplayName("Disabled: skips entirely without scanning the repository")
    void disabled_skips() {
        RegistrationResendService disabled = new RegistrationResendService(
                registrationRepository, eventRepository, confirmationTokenService,
                registrationEmailService, userApiClient,
                false, 48, 2, 120, "https://batbern.ch");

        disabled.resendUnconfirmedRegistrations();

        verifyNoInteractions(registrationRepository, registrationEmailService, userApiClient);
    }

    @Test
    @DisplayName("No eligible registrations: nothing sent, nothing saved")
    void noEligible_doesNothing() {
        when(registrationRepository.findResendEligible(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.resendUnconfirmedRegistrations();

        verify(registrationEmailService, never())
                .sendRegistrationConfirmation(any(), any(), any(), any(), any(), any(), any());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Eligible registration: fresh tokens generated, email sent, resend tracked")
    void eligible_resendsAndTracks() {
        Registration reg = registration("BATbern59-reg-A", 0);
        Event event = mock(Event.class);
        when(event.getEventCode()).thenReturn("BATbern59");
        UserResponse user = mock(UserResponse.class);
        when(user.getEmail()).thenReturn("attendee@example.ch");

        when(registrationRepository.findResendEligible(any(), any(), any(), eq(2)))
                .thenReturn(List.of(reg));
        when(eventRepository.findById(reg.getEventId())).thenReturn(Optional.of(event));
        when(userApiClient.getUserByUsername("test.user")).thenReturn(user);
        when(confirmationTokenService.generateConfirmationToken(reg.getId(), "BATbern59")).thenReturn("ctoken");
        when(confirmationTokenService.generateCancellationToken(reg.getId(), "BATbern59")).thenReturn("xtoken");

        service.resendUnconfirmedRegistrations();

        verify(registrationEmailService).sendRegistrationConfirmation(
                eq(reg), eq(user), eq(event), eq("ctoken"), eq("xtoken"), any(), any());
        verify(registrationRepository).save(reg);
        assertThat(reg.getConfirmationResendCount()).isEqualTo(1);
        assertThat(reg.getConfirmationResentAt()).isNotNull();
    }

    @Test
    @DisplayName("Attendee without a resolvable email is skipped (no send, no tracking)")
    void noEmail_skips() {
        Registration reg = registration("BATbern59-reg-B", 0);
        Event event = mock(Event.class);
        UserResponse user = mock(UserResponse.class);
        when(user.getEmail()).thenReturn(null);

        when(registrationRepository.findResendEligible(any(), any(), any(), anyInt()))
                .thenReturn(List.of(reg));
        when(eventRepository.findById(reg.getEventId())).thenReturn(Optional.of(event));
        when(userApiClient.getUserByUsername("test.user")).thenReturn(user);

        service.resendUnconfirmedRegistrations();

        verify(registrationEmailService, never())
                .sendRegistrationConfirmation(any(), any(), any(), any(), any(), any(), any());
        verify(registrationRepository, never()).save(any());
        assertThat(reg.getConfirmationResendCount()).isZero();
    }

    @Test
    @DisplayName("A failing registration does not stop the rest of the batch")
    void continuesOnError() {
        Registration bad = registration("BATbern59-reg-BAD", 0);
        Registration good = registration("BATbern59-reg-GOOD", 0);
        Event event = mock(Event.class);
        when(event.getEventCode()).thenReturn("BATbern59");
        UserResponse user = mock(UserResponse.class);
        when(user.getEmail()).thenReturn("ok@example.ch");

        when(registrationRepository.findResendEligible(any(), any(), any(), anyInt()))
                .thenReturn(List.of(bad, good));
        when(eventRepository.findById(bad.getEventId())).thenThrow(new RuntimeException("boom"));
        when(eventRepository.findById(good.getEventId())).thenReturn(Optional.of(event));
        when(userApiClient.getUserByUsername("test.user")).thenReturn(user);

        service.resendUnconfirmedRegistrations();

        // The good registration is still processed despite the earlier failure
        verify(registrationRepository).save(good);
        assertThat(good.getConfirmationResendCount()).isEqualTo(1);
    }

    private Registration registration(String code, int resendCount) {
        return Registration.builder()
                .id(UUID.randomUUID())
                .registrationCode(code)
                .eventId(UUID.randomUUID())
                .attendeeUsername("test.user")
                .status("registered")
                .deregistrationToken(UUID.randomUUID())
                .confirmationResendCount(resendCount)
                .createdAt(Instant.now().minusSeconds(3L * 24 * 3600))
                .build();
    }
}
