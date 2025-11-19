package ch.batbern.events.service;

import ch.batbern.events.domain.Registration;
import ch.batbern.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled cleanup service for unconfirmed registrations
 * Story 4.1.5c: Automatic cleanup of registrations that were never email-confirmed
 *
 * Cleanup Rules:
 * - 'registered' status registrations > 48 hours old: Deleted (email confirmation not completed)
 *
 * Runs daily at 3 AM to minimize impact on production traffic
 * (Uses 3 AM to avoid collision with other scheduled jobs at 2 AM)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RegistrationCleanupService {

    private final RegistrationRepository registrationRepository;

    /**
     * Scheduled cleanup job - runs daily at 3 AM
     * Cron expression: "0 0 3 * * *"
     * - Second: 0
     * - Minute: 0
     * - Hour: 3 (3 AM)
     * - Day of month: * (every day)
     * - Month: * (every month)
     * - Day of week: * (every day of week)
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupUnconfirmedRegistrations() {
        log.info("Starting scheduled cleanup of unconfirmed registrations");

        Instant now = Instant.now();
        int totalDeleted = deleteUnconfirmedRegistrations(now);

        log.info("Completed cleanup of registrations. Deleted: {}", totalDeleted);
    }

    /**
     * Delete 'registered' status registrations older than 48 hours
     * These are registrations where the user never clicked the email confirmation link
     *
     * @param now Current timestamp
     * @return Number of registrations deleted
     */
    private int deleteUnconfirmedRegistrations(Instant now) {
        Instant expiryThreshold = now.minus(48, ChronoUnit.HOURS);

        List<Registration> unconfirmedRegistrations = registrationRepository
                .findByStatusAndCreatedAtBefore("registered", expiryThreshold);

        if (unconfirmedRegistrations.isEmpty()) {
            log.info("No unconfirmed registrations found older than 48 hours");
            return 0;
        }

        log.info("Found {} unconfirmed registrations older than 48 hours", unconfirmedRegistrations.size());

        int deleted = 0;
        for (Registration registration : unconfirmedRegistrations) {
            try {
                registrationRepository.delete(registration);

                deleted++;
                log.debug(
                    "Deleted unconfirmed registration: registrationCode={}, attendeeUsername={},"
                    + " createdAt={}, age={}h",
                    registration.getRegistrationCode(),
                    registration.getAttendeeUsername(),
                    registration.getCreatedAt(),
                    ChronoUnit.HOURS.between(registration.getCreatedAt(), now));
            } catch (Exception e) {
                log.error("Failed to delete registration: registrationCode={}",
                        registration.getRegistrationCode(), e);
                // Continue with next registration - don't let one failure stop cleanup
            }
        }

        log.info("Deleted {} of {} unconfirmed registrations",
                deleted, unconfirmedRegistrations.size());

        return deleted;
    }

    /**
     * Manual trigger for cleanup (for testing or emergency use)
     * Can be called via admin endpoint if needed
     */
    public void triggerManualCleanup() {
        log.warn("Manual cleanup triggered");
        cleanupUnconfirmedRegistrations();
    }

    /**
     * Get cleanup statistics
     * Returns counts of registrations in each status
     */
    public CleanupStatistics getCleanupStatistics() {
        Instant now = Instant.now();
        Instant expiryThreshold = now.minus(48, ChronoUnit.HOURS);

        long registeredCount = registrationRepository.countByStatus("registered");
        long confirmedCount = registrationRepository.countByStatus("confirmed");
        long cancelledCount = registrationRepository.countByStatus("cancelled");

        long deletableUnconfirmed = registrationRepository
                .findByStatusAndCreatedAtBefore("registered", expiryThreshold).size();

        return new CleanupStatistics(
                registeredCount,
                confirmedCount,
                cancelledCount,
                deletableUnconfirmed
        );
    }

    /**
     * DTO for cleanup statistics
     */
    public record CleanupStatistics(
            long registeredCount,
            long confirmedCount,
            long cancelledCount,
            long deletableUnconfirmedCount
    ) {}
}
