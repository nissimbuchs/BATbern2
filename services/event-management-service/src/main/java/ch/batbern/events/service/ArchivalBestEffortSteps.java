package ch.batbern.events.service;

import ch.batbern.events.notification.NotificationRepository;
import ch.batbern.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Best-effort archival cleanup steps that run in their own independent transactions
 * (Story 10.18 AC5). Using {@code PROPAGATION_REQUIRES_NEW} ensures that if either
 * step fails, only that step's transaction is rolled back — the primary task-cancellation
 * transaction in {@link EventArchivalCleanupService} is unaffected.
 *
 * <p>Must be a separate Spring-managed bean so that Spring AOP can intercept the
 * {@code @Transactional} annotation (self-call interception is not supported).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
class ArchivalBestEffortSteps {

    private final RegistrationRepository registrationRepository;
    private final NotificationRepository notificationRepository;

    /**
     * Cancel all waitlisted registrations for the event. Runs in its own independent
     * transaction so that failure does not affect the task-cancellation transaction.
     *
     * @param eventId        the event UUID
     * @param waitlistStatus the exact waitlist status string (e.g. "waitlist")
     * @return number of registrations cancelled
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cancelWaitlistRegistrations(UUID eventId, String waitlistStatus) {
        return registrationRepository.cancelWaitlistRegistrationsForEvent(eventId, waitlistStatus);
    }

    /**
     * Dismiss all unread notifications for the event. Runs in its own independent
     * transaction so that failure does not affect the task-cancellation transaction.
     *
     * @param eventCode the event code (e.g. "BATbern42")
     * @return number of notifications dismissed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int dismissNotifications(String eventCode) {
        return notificationRepository.dismissNotificationsForEvent(eventCode);
    }
}
