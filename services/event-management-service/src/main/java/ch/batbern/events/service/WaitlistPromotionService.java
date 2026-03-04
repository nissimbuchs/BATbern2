package ch.batbern.events.service;

import ch.batbern.events.domain.Registration;
import ch.batbern.events.exception.RegistrationNotFoundException;
import ch.batbern.events.exception.RegistrationNotOnWaitlistException;
import ch.batbern.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing waitlist promotions.
 * Story 10.11: Venue Capacity Enforcement & Waitlist Management (AC3)
 *
 * Handles:
 * - Automatic promotion when a spot opens (called by RegistrationService.cancelRegistration in 10.12)
 * - Manual promotion by organizer (called from promote endpoint)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistPromotionService {

    private final RegistrationRepository registrationRepository;
    private final WaitlistPromotionEmailService waitlistPromotionEmailService;

    /**
     * Promote the first waitlisted registration for an event (lowest waitlistPosition).
     * Called after a cancellation to automatically fill the freed spot.
     *
     * @param eventId Internal event UUID
     */
    @Transactional
    public void promoteFromWaitlist(UUID eventId) {
        Optional<Registration> candidate =
                registrationRepository.findTopByEventIdAndStatusOrderByWaitlistPositionAsc(eventId, "waitlist");

        if (candidate.isEmpty()) {
            log.debug("No waitlist candidates for event {}", eventId);
            return;
        }

        Registration promoted = candidate.get();
        promoted.setStatus("registered");
        promoted.setWaitlistPosition(null);
        registrationRepository.save(promoted);

        waitlistPromotionEmailService.sendPromotionEmail(promoted);
        log.info("Promoted registration {} from waitlist for event {}",
                promoted.getRegistrationCode(), eventId);
    }

    /**
     * Manually promote a specific registration from waitlist to registered.
     * Called from the organizer UI "Promote to Registered" action.
     *
     * @param registrationCode Public registration code
     * @throws RegistrationNotFoundException     when code does not exist → 404
     * @throws RegistrationNotOnWaitlistException when registration exists but is not on waitlist → 409
     */
    @Transactional
    public void manuallyPromote(String registrationCode) {
        Registration registration = registrationRepository.findByRegistrationCode(registrationCode)
                .orElseThrow(() -> new RegistrationNotFoundException(registrationCode));

        if (!"waitlist".equals(registration.getStatus())) {
            throw new RegistrationNotOnWaitlistException(registrationCode);
        }

        registration.setStatus("registered");
        registration.setWaitlistPosition(null);
        registrationRepository.save(registration);

        waitlistPromotionEmailService.sendPromotionEmail(registration);
        log.info("Manually promoted registration {} from waitlist", registrationCode);
    }
}
