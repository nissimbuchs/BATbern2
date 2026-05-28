package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Daily job that re-sends the registration-confirmation email to attendees who registered but
 * never confirmed, after a grace window (default 2 days). Complements {@link RegistrationCleanupService}:
 * cleanup eventually deletes never-confirmed rows; this job nudges the attendee with a fresh
 * confirmation link first, so a missed/expired first email is recoverable without manual support.
 *
 * <p>Anti-spam / idempotency: each registration carries {@code confirmation_resend_count} and
 * {@code confirmation_resent_at}. A registration is auto-resent at most {@code max-attempts} times
 * (default 2), with at least {@code after-hours} between resends, so a daily run never floods inboxes.
 *
 * <p>Each resend regenerates fresh confirmation + cancellation JWTs (mirroring the organizer
 * resend endpoint) so the new email always carries a link valid for the full configured window.
 *
 * <p>Runs daily at 08:30 (after speaker reminders at 08:00, before deadline reminders at 09:00).
 * Cleanup runs earlier at 03:00, so rows past the cleanup horizon are already gone by the time this
 * job scans; the {@code notExpiredAfter} bound is a defensive second guard.
 */
@Service
@Slf4j
public class RegistrationResendService {

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final ConfirmationTokenService confirmationTokenService;
    private final RegistrationEmailService registrationEmailService;
    private final UserApiClient userApiClient;

    private final boolean enabled;
    private final long afterHours;
    private final int maxAttempts;
    private final long cleanupAfterHours;
    private final String appBaseUrl;

    public RegistrationResendService(
            RegistrationRepository registrationRepository,
            EventRepository eventRepository,
            ConfirmationTokenService confirmationTokenService,
            RegistrationEmailService registrationEmailService,
            UserApiClient userApiClient,
            @Value("${app.registration.resend.enabled:true}") boolean enabled,
            @Value("${app.registration.resend.after-hours:48}") long afterHours,
            @Value("${app.registration.resend.max-attempts:2}") int maxAttempts,
            @Value("${app.registration.cleanup-after-hours:120}") long cleanupAfterHours,
            @Value("${app.base-url:https://batbern.ch}") String appBaseUrl) {
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.confirmationTokenService = confirmationTokenService;
        this.registrationEmailService = registrationEmailService;
        this.userApiClient = userApiClient;
        this.enabled = enabled;
        this.afterHours = afterHours;
        this.maxAttempts = maxAttempts;
        this.cleanupAfterHours = cleanupAfterHours;
        this.appBaseUrl = appBaseUrl;
    }

    /**
     * Scheduled resend job. ShedLock ensures a single ECS instance runs it.
     */
    @Scheduled(cron = "${app.registration.resend.cron:0 30 8 * * *}")
    @SchedulerLock(
            name = "resendUnconfirmedRegistrations",
            lockAtLeastFor = "PT1M",
            lockAtMostFor = "PT15M"
    )
    public void resendUnconfirmedRegistrations() {
        if (!enabled) {
            log.debug("Registration confirmation resend disabled; skipping scheduled execution");
            return;
        }
        log.info("Starting scheduled resend of unconfirmed registration confirmations");

        Instant now = Instant.now();
        Instant eligibleBefore = now.minus(afterHours, ChronoUnit.HOURS);          // unconfirmed > grace window
        Instant notExpiredAfter = now.minus(cleanupAfterHours, ChronoUnit.HOURS);  // still within active window
        Instant resentBefore = now.minus(afterHours, ChronoUnit.HOURS);            // >= grace since last resend

        List<Registration> eligible = registrationRepository.findResendEligible(
                eligibleBefore, notExpiredAfter, resentBefore, maxAttempts);

        if (eligible.isEmpty()) {
            log.info("No unconfirmed registrations eligible for confirmation resend");
            return;
        }
        log.info("Found {} unconfirmed registration(s) eligible for confirmation resend", eligible.size());

        int sent = 0;
        for (Registration registration : eligible) {
            try {
                if (resendFor(registration, now)) {
                    sent++;
                }
            } catch (Exception e) {
                // Don't let one failure stop the batch
                log.error("Failed to resend confirmation for registration {}",
                        registration.getRegistrationCode(), e);
            }
        }
        log.info("Confirmation resend complete: {} of {} processed", sent, eligible.size());
    }

    private boolean resendFor(Registration registration, Instant now) {
        Event event = eventRepository.findById(registration.getEventId()).orElse(null);
        if (event == null) {
            log.warn("Skipping resend for {} — event {} not found",
                    registration.getRegistrationCode(), registration.getEventId());
            return false;
        }

        UserResponse userProfile = userApiClient.getUserByUsername(registration.getAttendeeUsername());
        if (userProfile == null || userProfile.getEmail() == null || userProfile.getEmail().isBlank()) {
            log.warn("Skipping resend for {} — no resolvable email for attendee {}",
                    registration.getRegistrationCode(), registration.getAttendeeUsername());
            return false;
        }

        String confirmationToken = confirmationTokenService.generateConfirmationToken(
                registration.getId(), event.getEventCode());
        String cancellationToken = confirmationTokenService.generateCancellationToken(
                registration.getId(), event.getEventCode());
        String deregistrationUrl = registration.getDeregistrationToken() != null
                ? appBaseUrl + "/deregister?token=" + registration.getDeregistrationToken()
                : null;

        registrationEmailService.sendRegistrationConfirmation(
                registration,
                userProfile,
                event,
                confirmationToken,
                cancellationToken,
                deregistrationUrl,
                Locale.GERMAN);

        int previous = registration.getConfirmationResendCount() == null
                ? 0 : registration.getConfirmationResendCount();
        registration.setConfirmationResentAt(now);
        registration.setConfirmationResendCount(previous + 1);
        registrationRepository.save(registration);

        log.info("Resent confirmation email for registration {} (auto-resend attempt {} of {})",
                registration.getRegistrationCode(), previous + 1, maxAttempts);
        return true;
    }
}
