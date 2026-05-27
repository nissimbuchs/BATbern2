package ch.batbern.events.service;

import ch.batbern.events.domain.Registration;
import ch.batbern.events.repository.RegistrationRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
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
 * - 'registered' status registrations older than the cleanup window are deleted (email
 *   confirmation never completed). Window is configurable via
 *   {@code app.registration.cleanup-after-hours} (default 120h / 5 days).
 *
 * INVARIANT: the cleanup window MUST exceed the confirmation-token validity
 * ({@code app.registration.confirmation-token-validity-hours}, default 96h / 4 days) — otherwise a
 * still-valid confirmation link could point at an already-deleted row. This service enforces the
 * invariant defensively at runtime via {@link #effectiveCleanupHours()} (it never deletes a row
 * whose link could still be valid) and warns at startup if the configured window is too small.
 *
 * Runs daily at 3 AM to minimize impact on production traffic
 * (Uses 3 AM to avoid collision with other scheduled jobs at 2 AM)
 */
@Service
@Slf4j
public class RegistrationCleanupService {

    /** Grace period kept between confirmation-link expiry and row deletion. */
    private static final long CLEANUP_GRACE_HOURS = 24;

    private final RegistrationRepository registrationRepository;
    private final long cleanupAfterHours;
    private final long tokenValidityHours;

    public RegistrationCleanupService(
            RegistrationRepository registrationRepository,
            @Value("${app.registration.cleanup-after-hours:120}") long cleanupAfterHours,
            @Value("${app.registration.confirmation-token-validity-hours:96}") long tokenValidityHours) {
        this.registrationRepository = registrationRepository;
        this.cleanupAfterHours = cleanupAfterHours;
        this.tokenValidityHours = tokenValidityHours;
    }

    /**
     * Effective cleanup window in hours. Guarantees we never delete a registration whose
     * confirmation link could still be valid, regardless of (mis)configuration:
     * {@code max(configured cleanup, tokenValidity + grace)}.
     */
    long effectiveCleanupHours() {
        return Math.max(cleanupAfterHours, tokenValidityHours + CLEANUP_GRACE_HOURS);
    }

    @PostConstruct
    void verifyCleanupWindowInvariant() {
        long minSafe = tokenValidityHours + CLEANUP_GRACE_HOURS;
        if (cleanupAfterHours < minSafe) {
            log.warn("Registration cleanup window ({}h) is below the safe minimum ({}h = token validity {}h "
                    + "+ {}h grace); clamping to {}h so valid confirmation links are never orphaned. "
                    + "Fix app.registration.cleanup-after-hours.",
                    cleanupAfterHours, minSafe, tokenValidityHours, CLEANUP_GRACE_HOURS, effectiveCleanupHours());
        }
    }

    /**
     * Scheduled cleanup job - runs daily at 3 AM
     * Cron expression: "0 0 3 * * *"
     * - Second: 0
     * - Minute: 0
     * - Hour: 3 (3 AM)
     * - Day of month: * (every day)
     * - Month: * (every month)
     * - Day of week: * (every day of week)
     *
     * ShedLock ensures only ONE ECS instance executes this job
     */
    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(
            name = "cleanupUnconfirmedRegistrations",
            lockAtMostFor = "30m",
            lockAtLeastFor = "1m"
    )
    @Transactional
    public void cleanupUnconfirmedRegistrations() {
        log.info("Starting scheduled cleanup of unconfirmed registrations");

        Instant now = Instant.now();
        int totalDeleted = deleteUnconfirmedRegistrations(now);

        log.info("Completed cleanup of registrations. Deleted: {}", totalDeleted);
    }

    /**
     * Delete 'registered' status registrations older than the effective cleanup window.
     * These are registrations where the user never clicked the email confirmation link.
     *
     * @param now Current timestamp
     * @return Number of registrations deleted
     */
    private int deleteUnconfirmedRegistrations(Instant now) {
        long windowHours = effectiveCleanupHours();
        Instant expiryThreshold = now.minus(windowHours, ChronoUnit.HOURS);

        List<Registration> unconfirmedRegistrations = registrationRepository
                .findByStatusAndCreatedAtBefore("registered", expiryThreshold);

        if (unconfirmedRegistrations.isEmpty()) {
            log.info("No unconfirmed registrations found older than {} hours", windowHours);
            return 0;
        }

        log.info("Found {} unconfirmed registrations older than {} hours",
                unconfirmedRegistrations.size(), windowHours);

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
        Instant expiryThreshold = now.minus(effectiveCleanupHours(), ChronoUnit.HOURS);

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
