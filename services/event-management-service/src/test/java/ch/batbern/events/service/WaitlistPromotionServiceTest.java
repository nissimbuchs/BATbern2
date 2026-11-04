package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.exception.RegistrationNotFoundException;
import ch.batbern.events.exception.RegistrationNotOnWaitlistException;
import ch.batbern.events.repository.EventRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private EventRepository eventRepository;

    @Mock
    private WaitlistPromotionEmailService waitlistPromotionEmailService;

    @InjectMocks
    private WaitlistPromotionService waitlistPromotionService;

    // ── promoteFromWaitlist(UUID eventId) ─────────────────────────────────────

    @Nested
    @DisplayName("promoteFromWaitlist(eventId) fills the free places")
    class PromoteFromWaitlist {

        @Test
        @DisplayName("capacity 250, 220 active, 42 waiting → the first 30 are promoted and mailed")
        void promoteFromWaitlist_fillsAllFreePlacesInOrder() {
            UUID eventId = eventWithCapacity(250);
            List<Registration> waiting = waitlist(eventId, 42);
            when(registrationRepository.findWaitlistByEventIdOrdered(eventId)).thenReturn(waiting);
            when(registrationRepository.countByEventIdAndStatusIn(eventId, Registration.CAPACITY_STATUSES))
                    .thenReturn(220L);

            int promoted = waitlistPromotionService.promoteFromWaitlist(eventId);

            assertThat(promoted).isEqualTo(30);
            assertThat(waiting.subList(0, 30)).allSatisfy(r -> {
                assertThat(r.getStatus()).isEqualTo("registered");
                assertThat(r.getWaitlistPosition()).isNull();
            });
            // remaining positions untouched (no renumbering)
            assertThat(waiting.get(30).getStatus()).isEqualTo("waitlist");
            assertThat(waiting.get(30).getWaitlistPosition()).isEqualTo(31);
            verify(registrationRepository, times(30)).save(any());
            verify(waitlistPromotionEmailService, times(30)).sendPromotionEmail(any());
            verify(waitlistPromotionEmailService).sendPromotionEmail(waiting.get(0));
            verify(waitlistPromotionEmailService, never()).sendPromotionEmail(waiting.get(30));
        }

        @Test
        @DisplayName("fewer waiting than free places → everyone waiting is promoted")
        void promoteFromWaitlist_moreFreePlacesThanWaiting_promotesAll() {
            UUID eventId = eventWithCapacity(250);
            List<Registration> waiting = waitlist(eventId, 3);
            when(registrationRepository.findWaitlistByEventIdOrdered(eventId)).thenReturn(waiting);
            when(registrationRepository.countByEventIdAndStatusIn(eventId, Registration.CAPACITY_STATUSES))
                    .thenReturn(200L);

            assertThat(waitlistPromotionService.promoteFromWaitlist(eventId)).isEqualTo(3);
            verify(waitlistPromotionEmailService, times(3)).sendPromotionEmail(any());
        }

        @Test
        @DisplayName("full or over capacity → nobody promoted, no mail")
        void promoteFromWaitlist_noFreePlace_noOp() {
            UUID eventId = eventWithCapacity(250);
            when(registrationRepository.findWaitlistByEventIdOrdered(eventId)).thenReturn(waitlist(eventId, 5));
            when(registrationRepository.countByEventIdAndStatusIn(eventId, Registration.CAPACITY_STATUSES))
                    .thenReturn(251L);

            assertThat(waitlistPromotionService.promoteFromWaitlist(eventId)).isZero();
            verify(registrationRepository, never()).save(any());
            verify(waitlistPromotionEmailService, never()).sendPromotionEmail(any());
        }

        @Test
        @DisplayName("capacity removed (unlimited) → everyone waiting is promoted")
        void promoteFromWaitlist_unlimited_promotesAll() {
            UUID eventId = eventWithCapacity(null);
            when(registrationRepository.findWaitlistByEventIdOrdered(eventId)).thenReturn(waitlist(eventId, 4));

            assertThat(waitlistPromotionService.promoteFromWaitlist(eventId)).isEqualTo(4);
            verify(waitlistPromotionEmailService, times(4)).sendPromotionEmail(any());
        }

        @Test
        @DisplayName("no-op when waitlist is empty — no email sent")
        void promoteFromWaitlist_emptyWaitlist_noOp() {
            UUID eventId = eventWithCapacity(10);
            when(registrationRepository.findWaitlistByEventIdOrdered(eventId)).thenReturn(List.of());

            assertThat(waitlistPromotionService.promoteFromWaitlist(eventId)).isZero();
            verify(registrationRepository, never()).save(any());
            verify(waitlistPromotionEmailService, never()).sendPromotionEmail(any());
        }

        private UUID eventWithCapacity(Integer capacity) {
            UUID eventId = UUID.randomUUID();
            Event event = Event.builder().id(eventId).eventCode("BATbern99").registrationCapacity(capacity).build();
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            return eventId;
        }

        private List<Registration> waitlist(UUID eventId, int size) {
            List<Registration> list = new ArrayList<>();
            for (int i = 1; i <= size; i++) {
                list.add(buildWaitlistRegistration("reg-" + i, eventId, i));
            }
            return list;
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
