package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.exception.RegistrationNotFoundException;
import ch.batbern.events.exception.RegistrationNotOnWaitlistException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing waitlist promotions.
 * Story 10.11: Venue Capacity Enforcement & Waitlist Management (AC3)
 *
 * Handles:
 * - Filling free places from the waitlist after a cancellation and after the capacity is saved
 * - Manual promotion by organizer (called from promote endpoint)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistPromotionService {

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final WaitlistPromotionEmailService waitlistPromotionEmailService;

    /**
     * Fill the free places of an event from its waitlist, in waitlist order.
     *
     * <p>Free places = registration capacity minus the active registrations (registered +
     * confirmed, the same count that puts a new registration on the waitlist). No capacity
     * (unlimited) means everyone waiting is promoted. Each promoted person gets the
     * waitlist-promotion mail. Called after a cancellation and whenever an organizer saves the
     * capacity, so raising it from 220 to 250 promotes the next 30.
     *
     * <p>Remaining waitlist positions are not renumbered.
     *
     * @param eventId Internal event UUID
     * @return the number of registrations promoted
     */
    @Transactional
    public int promoteFromWaitlist(UUID eventId) {
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null) {
            log.warn("Waitlist promotion skipped: event {} not found", eventId);
            return 0;
        }
        List<Registration> waiting = registrationRepository.findWaitlistByEventIdOrdered(eventId);
        if (waiting.isEmpty()) {
            log.debug("No waitlist candidates for event {}", eventId);
            return 0;
        }

        Integer capacity = event.getRegistrationCapacity();
        long free = capacity == null
                ? waiting.size()
                : capacity - registrationRepository.countByEventIdAndStatusIn(
                        eventId, Registration.CAPACITY_STATUSES);
        int toPromote = (int) Math.max(0, Math.min(free, waiting.size()));

        for (Registration promoted : waiting.subList(0, toPromote)) {
            promoted.setStatus("registered");
            promoted.setWaitlistPosition(null);
            registrationRepository.save(promoted);
            waitlistPromotionEmailService.sendPromotionEmail(promoted);
            log.info("Promoted registration {} from waitlist for event {}",
                    promoted.getRegistrationCode(), event.getEventCode());
        }
        if (toPromote > 0 || free <= 0) {
            log.info("Waitlist fill for event {}: capacity={}, free={}, waiting={}, promoted={}",
                    event.getEventCode(), capacity, free, waiting.size(), toPromote);
        }
        return toPromote;
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
