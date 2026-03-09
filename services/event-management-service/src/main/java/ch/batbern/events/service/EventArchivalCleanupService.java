package ch.batbern.events.service;

import ch.batbern.events.repository.EventTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cleans up stale data when an event is archived (Story 10.18).
 *
 * <p>Runs synchronously inside the ARCHIVED state transition. The task cancellation
 * step is primary and participates in the caller's transaction — a failure rolls back
 * the entire archival. Waitlist and notification steps run in their own independent
 * transactions ({@link ArchivalBestEffortSteps}): failure in either step is caught and
 * logged without rolling back the task-cancellation step.</p>
 *
 * <p>Idempotent — safe to call multiple times on the same event.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventArchivalCleanupService {

    /**
     * Exact DB value for waitlist registration status (normalised by V74 migration).
     */
    private static final String WAITLIST_STATUS = "waitlist";

    private final EventTaskRepository eventTaskRepository;
    private final ArchivalBestEffortSteps bestEffortSteps;

    /**
     * Clean up all open tasks, waitlisted registrations, and unread notifications
     * for an event being archived. Idempotent — safe to call multiple times.
     *
     * <p>Task cancellation participates in the caller's {@code @Transactional} context.
     * Waitlist and notification cleanup run in separate independent transactions via
     * {@link ArchivalBestEffortSteps}: a failure there is logged but never rolls back
     * task cancellation.</p>
     *
     * @param eventId   the event's database UUID (obtained from the already-loaded event)
     * @param eventCode the event code for logging and notification lookup (e.g. "BATbern42")
     */
    @Transactional
    public void cleanup(UUID eventId, String eventCode) {
        log.info("Starting archival cleanup for event: {}", eventCode);

        // Step 1: Cancel open tasks (primary — rolls back with the outer transaction on failure)
        int cancelledTasks = eventTaskRepository.cancelOpenTasksForEvent(eventId);
        log.info("Archival cleanup: cancelled {} open tasks for event: {}", cancelledTasks, eventCode);

        // Step 2: Cancel waitlisted registrations (best-effort — own independent transaction)
        try {
            int cancelledWaitlist = bestEffortSteps.cancelWaitlistRegistrations(eventId, WAITLIST_STATUS);
            log.info("Archival cleanup: cancelled {} waitlist registrations for event: {}",
                    cancelledWaitlist, eventCode);
        } catch (Exception e) {
            log.warn("Archival cleanup: failed to cancel waitlist registrations for event: {} — {}",
                    eventCode, e.getMessage());
        }

        // Step 3: Dismiss unread notifications (best-effort — own independent transaction)
        try {
            int dismissed = bestEffortSteps.dismissNotifications(eventCode);
            log.info("Archival cleanup: dismissed {} notifications for event: {}", dismissed, eventCode);
        } catch (Exception e) {
            log.warn("Archival cleanup: failed to dismiss notifications for event: {} — {}",
                    eventCode, e.getMessage());
        }

        log.info("Archival cleanup complete for event: {}", eventCode);
    }
}
