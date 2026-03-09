package ch.batbern.events.service;

import ch.batbern.events.domain.Registration;
import ch.batbern.events.exception.RegistrationNotFoundException;
import ch.batbern.events.exception.RegistrationNotOnWaitlistException;
import ch.batbern.events.repository.RegistrationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WaitlistPromotionService.
 * Story 10.11: Venue Capacity Enforcement & Waitlist Management (AC3, AC10)
 *
 * TDD: Written FIRST (RED phase) before implementation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WaitlistPromotionService Unit Tests")
class WaitlistPromotionServiceTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private WaitlistPromotionEmailService waitlistPromotionEmailService;

    @InjectMocks
    private WaitlistPromotionService waitlistPromotionService;

    // ── promoteFromWaitlist(UUID eventId) ─────────────────────────────────────

    @Nested
    @DisplayName("promoteFromWaitlist(eventId)")
    class PromoteFromWaitlist {

        @Test
        @DisplayName("promotes position-1 when waitlist has 3 people, sends email, clears waitlist_position")
        void promoteFromWaitlist_withMultipleWaitlisted_promotesFirst() {
            UUID eventId = UUID.randomUUID();

            Registration position1 = buildWaitlistRegistration("reg-001", eventId, 1);
            Registration position2 = buildWaitlistRegistration("reg-002", eventId, 2);
            Registration position3 = buildWaitlistRegistration("reg-003", eventId, 3);

            when(registrationRepository.findTopByEventIdAndStatusOrderByWaitlistPositionAsc(eventId, "waitlist"))
                    .thenReturn(Optional.of(position1));
            when(registrationRepository.save(any(Registration.class))).thenAnswer(i -> i.getArgument(0));

            waitlistPromotionService.promoteFromWaitlist(eventId);

            ArgumentCaptor<Registration> savedCaptor = ArgumentCaptor.forClass(Registration.class);
            verify(registrationRepository).save(savedCaptor.capture());

            Registration saved = savedCaptor.getValue();
            assertThat(saved.getRegistrationCode()).isEqualTo("reg-001");
            assertThat(saved.getStatus()).isEqualTo("registered");
            assertThat(saved.getWaitlistPosition()).isNull();

            verify(waitlistPromotionEmailService).sendPromotionEmail(saved);

            // positions 2 and 3 are NOT touched (FIFO by original position — no renumbering)
            assertThat(position2.getWaitlistPosition()).isEqualTo(2);
            assertThat(position3.getWaitlistPosition()).isEqualTo(3);
        }

        @Test
        @DisplayName("no-op when waitlist is empty — no email sent")
        void promoteFromWaitlist_emptyWaitlist_noOp() {
            UUID eventId = UUID.randomUUID();
            when(registrationRepository.findTopByEventIdAndStatusOrderByWaitlistPositionAsc(eventId, "waitlist"))
                    .thenReturn(Optional.empty());

            waitlistPromotionService.promoteFromWaitlist(eventId);

            verify(registrationRepository, never()).save(any());
            verify(waitlistPromotionEmailService, never()).sendPromotionEmail(any());
        }

        @Test
        @DisplayName("remaining waitlist positions are NOT renumbered after promotion")
        void promoteFromWaitlist_doesNotRenumberRemainingPositions() {
            UUID eventId = UUID.randomUUID();
            Registration position1 = buildWaitlistRegistration("reg-P1", eventId, 1);

            when(registrationRepository.findTopByEventIdAndStatusOrderByWaitlistPositionAsc(eventId, "waitlist"))
                    .thenReturn(Optional.of(position1));
            when(registrationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            waitlistPromotionService.promoteFromWaitlist(eventId);

            // We did NOT call findWaitlistByEventIdOrdered (no renumbering query)
            verify(registrationRepository, never()).findWaitlistByEventIdOrdered(any());
        }
    }

    // ── manuallyPromote(registrationCode) ─────────────────────────────────────

    @Nested
    @DisplayName("manuallyPromote(registrationCode)")
    class ManuallyPromote {

        @Test
        @DisplayName("promotes a waitlisted registration successfully")
        void manuallyPromote_waitlistedRegistration_promotes() {
            Registration waitlisted = buildWaitlistRegistration("reg-MANUAL", UUID.randomUUID(), 2);
            when(registrationRepository.findByRegistrationCode("reg-MANUAL"))
                    .thenReturn(Optional.of(waitlisted));
            when(registrationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            waitlistPromotionService.manuallyPromote("reg-MANUAL");

            ArgumentCaptor<Registration> captor = ArgumentCaptor.forClass(Registration.class);
            verify(registrationRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("registered");
            assertThat(captor.getValue().getWaitlistPosition()).isNull();
            verify(waitlistPromotionEmailService).sendPromotionEmail(captor.getValue());
        }

        @Test
        @DisplayName("throws RegistrationNotFoundException when code does not exist")
        void manuallyPromote_notFound_throwsNotFoundException() {
            when(registrationRepository.findByRegistrationCode("unknown-code"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> waitlistPromotionService.manuallyPromote("unknown-code"))
                    .isInstanceOf(RegistrationNotFoundException.class);
        }

        @Test
        @DisplayName("throws RegistrationNotOnWaitlistException when status is not waitlist")
        void manuallyPromote_notOnWaitlist_throwsConflictException() {
            Registration registered = buildWaitlistRegistration("reg-REG", UUID.randomUUID(), null);
            registered.setStatus("registered");
            when(registrationRepository.findByRegistrationCode("reg-REG"))
                    .thenReturn(Optional.of(registered));

            assertThatThrownBy(() -> waitlistPromotionService.manuallyPromote("reg-REG"))
                    .isInstanceOf(RegistrationNotOnWaitlistException.class);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Registration buildWaitlistRegistration(String code, UUID eventId, Integer position) {
        return Registration.builder()
                .id(UUID.randomUUID())
                .registrationCode(code)
                .eventId(eventId)
                .attendeeUsername("user.test")
                .status("waitlist")
                .waitlistPosition(position)
                .registrationDate(Instant.now())
                .build();
    }
}
