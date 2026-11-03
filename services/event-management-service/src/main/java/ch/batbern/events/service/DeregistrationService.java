package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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
    private final UserApiClient userApiClient; // #1183
    private final PersonAddresses personAddresses; // #1183

    @Value("${app.base-url:https://batbern.ch}")
    private String appBaseUrl;

    /** Story 16.0 AC9: at most one deregistration-link mail per registration in this window. */
    static final Duration LINK_RESEND_WINDOW = Duration.ofDays(7);

    /** Outcome of {@link #requestDeregistrationLinkByReply} and {@link #sendDeregistrationLinkOnce}. */
    public enum DeregistrationLinkResult {
        SENT,
        /** Sent within the last 7 days; nothing sent now. */
        ALREADY_SENT,
        /** No active registration (none, or already cancelled); nothing sent. */
        NOT_FOUND
    }

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
            Optional<Registration> direct = registrationRepository.findByAttendeeEmailAndEventCode(email, eventCode)
                    .filter(r -> !"cancelled".equalsIgnoreCase(r.getStatus()));
            // #1183: else a registration of the person who owns the address, on another profile or
            // under another of their verified addresses; the link then goes to the typed address
            String recipient = direct.map(Registration::getAttendeeEmail).orElse(email);
            boolean found = direct.or(() -> ownersRegistration(email, eventCode))
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
                        deregistrationEmailService.sendDeregistrationLinkEmail(registration, event, deregistrationLink,
                                recipient);
                        log.info("Deregistration by email: registration={}, event={}, email sent",
                                registration.getRegistrationCode(), event.getEventCode());
                        return true;
                    })
                    .orElse(false);

            log.info("Deregistration by email requested for {}/{}: found={}",
                    LoggingUtils.maskEmail(email), eventCode, found);
        } catch (Exception e) {
            // Anti-enumeration: never surface errors. Log for investigation.
            log.error("Unexpected error during deregisterByEmail for {}/{}: {}",
                    LoggingUtils.maskEmail(email), eventCode, e.getMessage(), e);
        }
    }

    /** #1183: the best active registration for the event of whoever owns {@code email}. */
    private Optional<Registration> ownersRegistration(String email, String eventCode) {
        return userApiClient.getEmailOwners(email).stream()
                .flatMap(owner -> {
                    Set<String> addresses = personAddresses.verified(owner);
                    List<Registration> byAddress = addresses.isEmpty() ? List.of()
                            : registrationRepository.findByEventCodeAndAttendeeEmailIn(eventCode, addresses);
                    return Stream.concat(
                            registrationRepository.findByEventCodeAndAttendeeUsername(eventCode, owner).stream(),
                            byAddress.stream())
                            .filter(r -> !"cancelled".equalsIgnoreCase(r.getStatus()))
                            .sorted(PersonAddresses.best(owner));
                })
                .findFirst();
    }

    /**
     * Story 16.0 AC11 (#1162): a reply "cancel" / "absagen" no longer cancels. It sends the
     * deregistration-link mail, so the attendee confirms with one click (and a misread reply cannot
     * cost anyone their seat). Synchronous, unlike {@link #deregisterByEmail}: the caller is the
     * inbound-mail listener thread and needs the result.
     *
     * @return what happened; callers must not reveal it to the sender (anti-enumeration)
     */
    @Transactional
    public DeregistrationLinkResult requestDeregistrationLinkByReply(String email, String eventCode) {
        return registrationRepository.findByAttendeeEmailAndEventCode(email, eventCode)
                .filter(r -> !"cancelled".equalsIgnoreCase(r.getStatus()))
                .map(this::sendDeregistrationLinkOnce)
                .orElse(DeregistrationLinkResult.NOT_FOUND);
    }

    /**
     * Sends the deregistration-link mail for an active registration, at most once per
     * {@link #LINK_RESEND_WINDOW} (AC9/AC10, {@code registrations.decline_link_sent_at}). Shared by
     * the reply path (#1162) and the calendar-decline path (#1161).
     */
    @Transactional
    public DeregistrationLinkResult sendDeregistrationLinkOnce(Registration registration) {
        if ("cancelled".equalsIgnoreCase(registration.getStatus())) {
            return DeregistrationLinkResult.NOT_FOUND;
        }
        Event event = eventRepository.findById(registration.getEventId()).orElse(null);
        if (event == null) {
            log.warn("Event not found for registration {}; no deregistration link sent",
                    registration.getRegistrationCode());
            return DeregistrationLinkResult.NOT_FOUND;
        }
        Instant now = Instant.now();
        if (registrationRepository.claimDeregistrationLink(registration.getId(), now,
                now.minus(LINK_RESEND_WINDOW)) == 0) {
            log.info("Deregistration link for {} already sent within {} days; not sending again",
                    registration.getRegistrationCode(), LINK_RESEND_WINDOW.toDays());
            return DeregistrationLinkResult.ALREADY_SENT;
        }
        String deregistrationLink = appBaseUrl + "/deregister?token=" + registration.getDeregistrationToken();
        deregistrationEmailService.sendDeregistrationLinkEmail(registration, event, deregistrationLink);
        log.info("Deregistration link sent: registration={}, event={}",
                registration.getRegistrationCode(), event.getEventCode());
        return DeregistrationLinkResult.SENT;
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
