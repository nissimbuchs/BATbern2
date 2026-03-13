package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserServiceException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RegistrationService.enrollStakeholders().
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegistrationService.enrollStakeholders() Unit Tests")
class EnrollStakeholdersTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserApiClient userApiClient;

    @Mock
    private RegistrationEmailService registrationEmailService;

    @Mock
    private NewsletterSubscriberService newsletterSubscriberService;

    @Mock
    private WaitlistPromotionEmailService waitlistPromotionEmailService;

    @Mock
    private WaitlistPromotionService waitlistPromotionService;

    @Spy
    @InjectMocks
    private RegistrationService registrationService;

    private Event event;
    private static final String EVENT_CODE = "BATbern99";

    @BeforeEach
    void setUp() {
        event = new Event();
        event.setId(UUID.randomUUID());
        event.setEventCode(EVENT_CODE);
    }

    private UserResponse user(String username) {
        UserResponse u = new UserResponse();
        u.setId(username);
        u.setFirstName("First");
        u.setLastName("Last");
        u.setEmail(username + "@example.com");
        u.setCompanyId("acme");
        return u;
    }

    @Nested
    @DisplayName("Enrollment counts")
    class EnrollmentCounts {

        @Test
        @DisplayName("should return enrolled=2, skipped=0 when both users are new")
        void should_returnCorrectCounts_when_usersAreNew() {
            when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("org.one"));
            when(userApiClient.getPartnerUsernames()).thenReturn(List.of("partner.one"));
            when(userApiClient.getUserByUsername("org.one")).thenReturn(user("org.one"));
            when(userApiClient.getUserByUsername("partner.one")).thenReturn(user("partner.one"));

            doReturn(true).when(registrationService).createInternalRegistration(eq(event), any(UserResponse.class));

            RegistrationService.EnrollmentSummary result = registrationService.enrollStakeholders(event);

            assertThat(result.enrolled()).isEqualTo(2);
            assertThat(result.skipped()).isEqualTo(0);
        }

        @Test
        @DisplayName("should return enrolled=1, skipped=1 when one user is already registered")
        void should_returnCorrectCounts_when_oneAlreadyRegistered() {
            when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("org.one", "org.two"));
            when(userApiClient.getPartnerUsernames()).thenReturn(List.of());
            when(userApiClient.getUserByUsername("org.one")).thenReturn(user("org.one"));
            when(userApiClient.getUserByUsername("org.two")).thenReturn(user("org.two"));

            doReturn(true).when(registrationService).createInternalRegistration(eq(event), eq(user("org.one")));
            doReturn(false).when(registrationService).createInternalRegistration(eq(event), eq(user("org.two")));

            RegistrationService.EnrollmentSummary result = registrationService.enrollStakeholders(event);

            assertThat(result.enrolled()).isEqualTo(1);
            assertThat(result.skipped()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return enrolled=0 when organizer and partner lists are empty")
        void should_returnZero_when_noUsersExist() {
            when(userApiClient.getOrganizerUsernames()).thenReturn(List.of());
            when(userApiClient.getPartnerUsernames()).thenReturn(List.of());

            RegistrationService.EnrollmentSummary result = registrationService.enrollStakeholders(event);

            assertThat(result.enrolled()).isEqualTo(0);
            assertThat(result.skipped()).isEqualTo(0);
            verify(registrationService, never()).createInternalRegistration(any(), any());
        }
    }

    @Nested
    @DisplayName("Error resilience")
    class ErrorResilience {

        @Test
        @DisplayName("should count failed user as skipped, not abort batch")
        void should_countFailedUser_asSkipped() {
            when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("org.one", "org.two"));
            when(userApiClient.getPartnerUsernames()).thenReturn(List.of());
            when(userApiClient.getUserByUsername("org.one"))
                    .thenThrow(new UserServiceException("timeout", 503, new RuntimeException()));
            when(userApiClient.getUserByUsername("org.two")).thenReturn(user("org.two"));

            doReturn(true).when(registrationService).createInternalRegistration(eq(event), any());

            RegistrationService.EnrollmentSummary result = registrationService.enrollStakeholders(event);

            assertThat(result.enrolled()).isEqualTo(1);
            assertThat(result.skipped()).isEqualTo(1);
            verify(registrationService, times(1)).createInternalRegistration(eq(event), any());
        }

        @Test
        @DisplayName("should continue after organizer list failure, still fetch partners")
        void should_continueAfterOrganizerListFailure() {
            when(userApiClient.getOrganizerUsernames())
                    .thenThrow(new UserServiceException("timeout", 503, new RuntimeException()));
            when(userApiClient.getPartnerUsernames()).thenReturn(List.of("partner.one"));
            when(userApiClient.getUserByUsername("partner.one")).thenReturn(user("partner.one"));
            doReturn(true).when(registrationService).createInternalRegistration(eq(event), any());

            RegistrationService.EnrollmentSummary result = registrationService.enrollStakeholders(event);

            assertThat(result.enrolled()).isEqualTo(1);
        }
    }
}
