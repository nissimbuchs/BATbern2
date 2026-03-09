package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Service for self-service deregistration flows.
 * <p>
 * Story 10.12: Self-Service Deregistration (AC3, AC4, AC5, AC6)
 * <p>
 * Three deregistration flows:
 * 1. Token-based (verify → confirm): attendee clicks link from email → /deregister?token=
 * 2. By-email: attendee requests link via email input on event page
 * 3. Organizer soft-cancel: organizer cancels via the admin UI (handled by EventController)
 * <p>
 * Anti-enumeration: by-email flow always returns success to prevent email discovery.
 * ADR-005: Works for both anonymous and authenticated registrants.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeregistrationService {

    private final RegistrationRepository registrationRepository;
    private final RegistrationService registrationService;
    private final DeregistrationEmailService deregistrationEmailService;
    private final EventRepository eventRepository;

    @Value("${app.base-url:https://batbern.ch}")
    private String appBaseUrl;

    /**
     * Verify a deregistration token and return registration details for confirmation display.
     *
     * @param token UUID deregistration token from email link
     * @return Registration summary for confirmation page
     * @throws NoSuchElementException   if token not found (→ 404)
     * @throws IllegalStateException    if registration already cancelled (→ 409 "already_cancelled")
     */
    @Transactional(readOnly = true)
    public DeregistrationVerifyResult verifyToken(UUID token) {
        // AC3: verify returns 404 for both "not found" AND "already cancelled"
        // (POST /deregister correctly returns 409 for cancelled — different endpoint, different spec)
        Registration registration = registrationRepository.findByDeregistrationToken(token)
                .orElseThrow(() -> new NoSuchElementException("invalid_token"));
        if ("cancelled".equalsIgnoreCase(registration.getStatus())) {
            throw new NoSuchElementException("invalid_token");
        }

        Event event = eventRepository.findById(registration.getEventId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Event not found for registration: " + registration.getRegistrationCode()));

        return new DeregistrationVerifyResult(
                registration.getRegistrationCode(),
                event.getEventCode(),
                event.getTitle(),
                event.getDate(),
                registration.getAttendeeFirstName()
        );
    }

    /**
     * Cancel a registration using its deregistration token.
     * Triggers waitlist promotion via {@link RegistrationService#cancelRegistration(Registration)}.
     *
     * @param token UUID deregistration token
     * @throws NoSuchElementException if token not found (→ 404)
     * @throws IllegalStateException  if already cancelled (→ 409 "already_cancelled")
     */
    @Transactional
    public void deregisterByToken(UUID token) {
        Registration registration = findActiveByToken(token);
        Event event = eventRepository.findById(registration.getEventId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Event not found for registration: " + registration.getRegistrationCode()));
        log.info("Deregistration by token: registration={}, event={}",
                registration.getRegistrationCode(), event.getEventCode());
        registrationService.cancelRegistration(registration);
    }

    /**
     * Send a deregistration link email if a matching active registration is found.
     * <p>
     * Anti-enumeration: always returns without error regardless of whether a registration exists.
     * Log the outcome internally for audit purposes.
     *
     * @param email     Attendee email to look up
     * @param eventCode Event code to scope the search
     */
    @Async
    @Transactional(readOnly = true)
    public void deregisterByEmail(String email, String eventCode) {
        try {
            boolean found = registrationRepository.findByAttendeeEmailAndEventCode(email, eventCode)
                    .filter(r -> !"cancelled".equalsIgnoreCase(r.getStatus()))
                    .map(registration -> {
                        Event event = eventRepository.findById(registration.getEventId())
                                .orElse(null);
                        if (event == null) {
                            log.warn("Event not found for registration {} during by-email deregistration",
                                    registration.getRegistrationCode());
                            return false;
                        }
                        String deregistrationLink = appBaseUrl + "/deregister?token="
                                + registration.getDeregistrationToken();
                        deregistrationEmailService.sendDeregistrationLinkEmail(registration, event, deregistrationLink);
                        log.info("Deregistration by email: registration={}, event={}, email sent",
                                registration.getRegistrationCode(), event.getEventCode());
                        return true;
                    })
                    .orElse(false);

            log.info("Deregistration by email requested for {}/{}: found={}", email, eventCode, found);
        } catch (Exception e) {
            // Anti-enumeration: never surface errors. Log for investigation.
            log.error("Unexpected error during deregisterByEmail for {}/{}: {}", email, eventCode, e.getMessage(), e);
        }
    }

    /**
     * Look up an active (non-cancelled) registration by deregistration token.
     * Throws standard exceptions that map to HTTP 404 and 409.
     */
    private Registration findActiveByToken(UUID token) {
        Registration registration = registrationRepository.findByDeregistrationToken(token)
                .orElseThrow(() -> new NoSuchElementException("invalid_token"));

        if ("cancelled".equalsIgnoreCase(registration.getStatus())) {
            throw new IllegalStateException("already_cancelled");
        }
        return registration;
    }

    /**
     * Immutable result DTO for the verify endpoint.
     * Story 10.12 (AC3): Returned to the frontend to show confirmation details.
     */
    public record DeregistrationVerifyResult(
            String registrationCode,
            String eventCode,
            String eventTitle,
            Instant eventDate,
            String attendeeFirstName
    ) {}
}
