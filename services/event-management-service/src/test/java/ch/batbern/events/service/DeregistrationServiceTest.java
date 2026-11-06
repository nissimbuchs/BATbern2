package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DeregistrationService.
 * Story 10.12 (AC12): Written FIRST (RED phase) before implementation.
 *
 * Test coverage:
 * - verifyToken() with valid token → DeregistrationVerifyResponse
 * - verifyToken() with unknown token → NoSuchElementException (→ 404)
 * - verifyToken() with already-cancelled → IllegalStateException (→ 409)
 * - deregisterByToken() → status=cancelled, waitlist promotion triggered
 * - deregisterByEmail() with valid email/eventCode → sends email
 * - deregisterByEmail() with unknown email → no error (anti-enumeration)
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("DeregistrationService Unit Tests")
class DeregistrationServiceTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private RegistrationService registrationService;

    @Mock
    private DeregistrationEmailService deregistrationEmailService;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserApiClient userApiClient;

    @Mock
    private PersonAddresses personAddresses;

    @InjectMocks
    private DeregistrationService deregistrationService;

    private static final UUID TOKEN = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final String EVENT_CODE = "BATbern142";
    private static final String EVENT_TITLE = "BATbern #142";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(deregistrationService, "appBaseUrl", "https://batbern.ch");
    }

    // ── verifyToken ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyToken(UUID token)")
    class VerifyToken {

        @Test
        @DisplayName("valid token → returns verify response with registration details")
        void verifyToken_validToken_returnsResponse() {
            Registration reg = buildActiveRegistration(TOKEN, EVENT_ID, "registered");
            Event event = buildEvent(EVENT_ID, EVENT_CODE, EVENT_TITLE);

            when(registrationRepository.findByDeregistrationToken(TOKEN)).thenReturn(Optional.of(reg));
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

            DeregistrationService.DeregistrationVerifyResult result = deregistrationService.verifyToken(TOKEN);

            assertThat(result.registrationCode()).isEqualTo(reg.getRegistrationCode());
            assertThat(result.eventCode()).isEqualTo(EVENT_CODE);
            assertThat(result.eventTitle()).isEqualTo(EVENT_TITLE);
            assertThat(result.attendeeFirstName()).isEqualTo(reg.getAttendeeFirstName());
            assertThat(result.eventDate()).isNotNull();
        }

        @Test
        @DisplayName("unknown token → throws NoSuchElementException (→ 404)")
        void verifyToken_unknownToken_throwsNotFound() {
            when(registrationRepository.findByDeregistrationToken(TOKEN)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deregistrationService.verifyToken(TOKEN))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("invalid_token");
        }

        @Test
        @DisplayName("already-cancelled registration → throws NoSuchElementException (→ 404 per AC3)")
        void verifyToken_cancelledRegistration_throws404() {
            // AC3: verify returns 404 for BOTH "not found" AND "already cancelled"
            Registration cancelled = buildActiveRegistration(TOKEN, EVENT_ID, "cancelled");

            when(registrationRepository.findByDeregistrationToken(TOKEN)).thenReturn(Optional.of(cancelled));

            assertThatThrownBy(() -> deregistrationService.verifyToken(TOKEN))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("invalid_token");
        }
    }

    // ── deregisterByToken ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("deregisterByToken(UUID token)")
    class DeregisterByToken {

        @Test
        @DisplayName("valid token → cancelRegistration called, waitlist promotion triggered")
        void deregisterByToken_validToken_cancelsCancelsAndPromotes() {
            Registration reg = buildActiveRegistration(TOKEN, EVENT_ID, "registered");
            Event event = buildEvent(EVENT_ID, EVENT_CODE, EVENT_TITLE);

            when(registrationRepository.findByDeregistrationToken(TOKEN)).thenReturn(Optional.of(reg));
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

            deregistrationService.deregisterByToken(TOKEN);

            verify(registrationService).cancelRegistration(reg);
        }

        @Test
        @DisplayName("unknown token → throws NoSuchElementException (→ 404)")
        void deregisterByToken_unknownToken_throwsNotFound() {
            when(registrationRepository.findByDeregistrationToken(TOKEN)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deregistrationService.deregisterByToken(TOKEN))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("invalid_token");

            verify(registrationService, never()).cancelRegistration(any());
        }

        @Test
        @DisplayName("already-cancelled token → throws IllegalStateException (→ 409)")
        void deregisterByToken_cancelledRegistration_throws409() {
            Registration cancelled = buildActiveRegistration(TOKEN, EVENT_ID, "cancelled");

            when(registrationRepository.findByDeregistrationToken(TOKEN)).thenReturn(Optional.of(cancelled));

            assertThatThrownBy(() -> deregistrationService.deregisterByToken(TOKEN))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already_cancelled");

            verify(registrationService, never()).cancelRegistration(any());
        }
    }

    // ── deregisterByEmail ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("deregisterByEmail(String email, String eventCode)")
    class DeregisterByEmail {

        @Test
        @DisplayName("valid email/eventCode with active registration → sends deregistration link email")
        void deregisterByEmail_activeRegistration_sendsEmail() {
            Registration reg = buildActiveRegistration(TOKEN, EVENT_ID, "registered");
            Event event = buildEvent(EVENT_ID, EVENT_CODE, EVENT_TITLE);

            when(registrationRepository.findByAttendeeEmailAndEventCode("alice@example.com", EVENT_CODE))
                    .thenReturn(Optional.of(reg));
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

            deregistrationService.deregisterByEmail("alice@example.com", EVENT_CODE);

            // the registration's own address, as before #1183
            verify(deregistrationEmailService).sendDeregistrationLinkEmail(eq(reg), eq(event), any(),
                    eq(reg.getAttendeeEmail()));
        }

        @Test
        @DisplayName("unknown email → returns without error (anti-enumeration)")
        void deregisterByEmail_unknownEmail_noErrorNoEmail() {
            when(registrationRepository.findByAttendeeEmailAndEventCode("ghost@example.com", EVENT_CODE))
                    .thenReturn(Optional.empty());

            // Must NOT throw — anti-enumeration
            deregistrationService.deregisterByEmail("ghost@example.com", EVENT_CODE);

            verify(deregistrationEmailService, never()).sendDeregistrationLinkEmail(any(), any(), any(), any());
        }

        @Test
        @DisplayName("#1183: the requested address is not written to the log in full")
        void deregisterByEmail_logsMaskedAddressOnly(CapturedOutput output) {
            when(registrationRepository.findByAttendeeEmailAndEventCode("ghost.person@example.com", EVENT_CODE))
                    .thenReturn(Optional.empty());

            deregistrationService.deregisterByEmail("ghost.person@example.com", EVENT_CODE);

            assertThat(output).contains("found=false").doesNotContain("ghost.person@example.com");
        }

        @Test
        @DisplayName("already-cancelled registration → returns without error, no email sent")
        void deregisterByEmail_cancelledRegistration_noEmailNoError() {
            Registration cancelled = buildActiveRegistration(TOKEN, EVENT_ID, "cancelled");

            when(registrationRepository.findByAttendeeEmailAndEventCode("alice@example.com", EVENT_CODE))
                    .thenReturn(Optional.of(cancelled));

            deregistrationService.deregisterByEmail("alice@example.com", EVENT_CODE);

            verify(deregistrationEmailService, never()).sendDeregistrationLinkEmail(any(), any(), any(), any());
        }

        // ── #1183: the address belongs to a profile whose registration is under another address ──

        @Test
        @DisplayName("#1183: no registration under the typed address → the owner's registration, link to the typed address")
        void deregisterByEmail_ownersRegistration_linkToTypedAddress() {
            Registration reg = buildActiveRegistration(TOKEN, EVENT_ID, "registered");
            Event event = buildEvent(EVENT_ID, EVENT_CODE, EVENT_TITLE);
            when(registrationRepository.findByAttendeeEmailAndEventCode("work@example.com", EVENT_CODE))
                    .thenReturn(Optional.empty());
            when(userApiClient.getEmailOwners("work@example.com")).thenReturn(List.of("hans.login"));
            when(registrationRepository.findByEventCodeAndAttendeeUsername(EVENT_CODE, "hans.login"))
                    .thenReturn(Optional.of(reg));
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

            deregistrationService.deregisterByEmail("work@example.com", EVENT_CODE);

            verify(deregistrationEmailService).sendDeregistrationLinkEmail(eq(reg), eq(event),
                    eq("https://batbern.ch/deregister?token=" + TOKEN), eq("work@example.com"));
        }

        @Test
        @DisplayName("#1183: the owner's registration as a guest under another verified address is found too")
        void deregisterByEmail_ownersVerifiedAddress_found() {
            Registration reg = buildActiveRegistration(TOKEN, EVENT_ID, "confirmed");
            Event event = buildEvent(EVENT_ID, EVENT_CODE, EVENT_TITLE);
            when(registrationRepository.findByAttendeeEmailAndEventCode("work@example.com", EVENT_CODE))
                    .thenReturn(Optional.empty());
            when(userApiClient.getEmailOwners("work@example.com")).thenReturn(List.of("hans.login"));
            when(registrationRepository.findByEventCodeAndAttendeeUsername(EVENT_CODE, "hans.login"))
                    .thenReturn(Optional.empty());
            when(personAddresses.verified("hans.login")).thenReturn(Set.of("hans@private.example"));
            when(registrationRepository.findByEventCodeAndAttendeeEmailIn(EVENT_CODE, Set.of("hans@private.example")))
                    .thenReturn(List.of(reg));
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

            deregistrationService.deregisterByEmail("work@example.com", EVENT_CODE);

            verify(deregistrationEmailService).sendDeregistrationLinkEmail(eq(reg), eq(event), any(),
                    eq("work@example.com"));
        }

        @Test
        @DisplayName("#1183: nobody owns the address → nothing sent, no error")
        void deregisterByEmail_noOwner_nothingSent() {
            when(registrationRepository.findByAttendeeEmailAndEventCode("ghost@example.com", EVENT_CODE))
                    .thenReturn(Optional.empty());
            when(userApiClient.getEmailOwners("ghost@example.com")).thenReturn(List.of());

            deregistrationService.deregisterByEmail("ghost@example.com", EVENT_CODE);

            verify(deregistrationEmailService, never()).sendDeregistrationLinkEmail(any(), any(), any());
            verify(deregistrationEmailService, never()).sendDeregistrationLinkEmail(any(), any(), any(), any());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Registration buildActiveRegistration(UUID token, UUID eventId, String status) {
        return Registration.builder()
                .id(UUID.randomUUID())
                .registrationCode("BATbern142-reg-TEST01")
                .eventId(eventId)
                .deregistrationToken(token)
                .attendeeFirstName("Alice")
                .attendeeLastName("Test")
                .attendeeEmail("alice@example.com")
                .attendeeUsername("alice.test")
                .status(status)
                .registrationDate(Instant.now())
                .build();
    }

    private Event buildEvent(UUID id, String code, String title) {
        Event event = new Event();
        event.setId(id);
        event.setEventCode(code);
        event.setTitle(title);
        // A week ahead: the event day itself refuses unregistering (#1238 E2).
        event.setDate(Instant.now().plus(7, ChronoUnit.DAYS));
        return event;
    }
}
