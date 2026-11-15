package ch.batbern.events.controller;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.RegistrationResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.registrations.api.generated.RegistrationsApi;
import ch.batbern.events.registrations.dto.generated.ConfirmRegistration200Response;
import ch.batbern.events.registrations.dto.generated.CreateMyRegistration201Response;
import ch.batbern.events.registrations.dto.generated.CreateRegistrationRequest;
import ch.batbern.events.registrations.dto.generated.CreateRegistrationResponse;
import ch.batbern.events.registrations.dto.generated.ListRegistrations200Response;
import ch.batbern.events.registrations.dto.generated.MyRegistrationResponse;
import ch.batbern.events.registrations.dto.generated.ResendConfirmationResponse;
import ch.batbern.events.registrations.dto.generated.UpdateRegistrationStatusRequest;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.ConfirmationTokenService;
import ch.batbern.events.service.RegistrationEmailService;
import ch.batbern.events.service.RegistrationService;
import ch.batbern.events.service.WaitlistPromotionService;
import ch.batbern.shared.api.PaginationMetadata;
import ch.batbern.shared.utils.LoggingUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The registrations of one event: anonymous registration and its e-mail confirmation, the signed-in
 * attendee's own registration ({@code /my-registration}), and the organizer's list, status change,
 * delete, waitlist promotion and confirmation resend. Story 17.13, moved out of
 * {@code EventController}, which may map none of these paths ({@code EmsEndpointPlacementTest}).
 *
 * <p>{@code implements} the generated {@link RegistrationsApi} interface (event-registrations spec,
 * ADR-006 contract-first): it supplies verb, path, parameters and their validation (event and
 * registration code patterns, {@code page >= 1}, {@code limit <= 100}); the class-level
 * {@code @RequestMapping("/api/v1")} supplies the version prefix. Errors map in
 * {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class RegistrationController implements RegistrationsApi {

    private final RegistrationService registrationService;
    private final EventRepository eventRepository;
    private final ConfirmationTokenService confirmationTokenService;
    private final RegistrationEmailService registrationEmailService;
    private final WaitlistPromotionService waitlistPromotionService;
    private final UserApiClient userApiClient;
    private final SecurityContextHelper securityContextHelper;

    @Value("${app.base-url:https://batbern.ch}")
    private String appBaseUrl;

    /**
     * Anonymous registration (Story 2.2a, ADR-005): the attendee needs no account; the registration
     * starts as {@code registered} and is confirmed by the e-mailed link. A waitlisted registration
     * gets its waitlist mail from {@link RegistrationService} instead. A repeated waitlist
     * registration answers 200 (its mail was sent again), any other repeat 409.
     */
    @Override
    @PreAuthorize("permitAll()")
    public ResponseEntity<CreateRegistrationResponse> createRegistration(
            String eventCode, CreateRegistrationRequest request) {
        log.debug("POST /api/v1/events/{}/registrations", eventCode);

        // QA Fix (VALID-001): the service returns the existing registration of a repeat.
        Instant beforeCreate = Instant.now();
        Registration registration = registrationService.createRegistration(eventCode, request);
        boolean isRepeat = registration.getCreatedAt() != null
                && registration.getCreatedAt().isBefore(beforeCreate);
        boolean waitlisted = "waitlist".equals(registration.getStatus());

        if (!waitlisted) {
            sendConfirmation(registration, findEvent(eventCode));
        }

        if (isRepeat) {
            if (waitlisted) {
                // Story 10.11 (T10.4): the waitlist mail was sent again by RegistrationService.
                log.info("Duplicate waitlist registration for event: {} by user: {}, returning existing waitlist entry",
                        eventCode, registration.getAttendeeUsername());
                return ResponseEntity.ok(new CreateRegistrationResponse(
                        "You are already on the waitlist for this event. A new confirmation email has been sent.",
                        request.getEmail(), registration.getCompanyUpdated()));
            }
            log.info("Duplicate registration attempt for event: {} by user: {} (status: {})",
                    eventCode, registration.getAttendeeUsername(), registration.getStatus());
            throw new IllegalStateException("User " + registration.getAttendeeUsername()
                    + " is already registered for event " + eventCode);
        }

        // BATbern59 badge fix: companyUpdated drives the UI's "we updated your company" notice.
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateRegistrationResponse(
                "Registration submitted successfully. Check your email to confirm.",
                request.getEmail(), registration.getCompanyUpdated()));
    }

    /**
     * Confirms a pending registration with the token of the confirmation mail (48 h valid; its event
     * code must match the path). Idempotent: a confirmed registration answers "already confirmed"
     * and is not mailed again; one that was processed since (cancelled, waitlisted, attended,
     * no-show) answers 409 and stays as it is (#1315).
     */
    @Override
    @PreAuthorize("permitAll()")
    public ResponseEntity<ConfirmRegistration200Response> confirmRegistration(String eventCode, String token) {
        log.debug("POST /api/v1/events/{}/registrations/confirm", eventCode);

        Claims claims;
        try {
            claims = confirmationTokenService.validateConfirmationToken(token);
        } catch (JwtException e) {
            log.warn("Invalid confirmation token: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid or expired confirmation token");
        }
        String tokenEventCode = confirmationTokenService.getEventCode(claims);
        if (!eventCode.equals(tokenEventCode)) {
            throw new IllegalArgumentException("Event code mismatch: URL has " + eventCode
                    + " but token has " + tokenEventCode);
        }

        UUID registrationId = confirmationTokenService.getRegistrationId(claims);
        String message = switch (registrationService.confirmRegistration(eventCode, registrationId)) {
            case CONFIRMED -> "Registration confirmed successfully!";
            case ALREADY_CONFIRMED -> "Registration already confirmed";
        };
        return ResponseEntity.ok(new ConfirmRegistration200Response()
                .message(message)
                .status(ConfirmRegistration200Response.StatusEnum.CONFIRMED));
    }

    /**
     * One page of the event's registrations for the organizer (Story 3.3): filtered by status,
     * search and company, enriched with the attendee from CUMS.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ListRegistrations200Response> listRegistrations(
            String eventCode, List<String> status, String search, String companyId, Integer page, Integer limit) {
        log.debug("GET /api/v1/events/{}/registrations - page: {}, limit: {}, status: {}, search: {}, companyId: {}",
                eventCode, page, limit, status, search, companyId);

        Event event = findEvent(eventCode);
        Page<RegistrationResponse> registrations =
                registrationService.listForEvent(event, status, search, companyId, page, limit);

        return ResponseEntity.ok(new ListRegistrations200Response()
                .data(registrations.getContent())
                .pagination(PaginationMetadata.builder()
                        .page(page)
                        .limit(limit)
                        .totalItems(registrations.getTotalElements())
                        .totalPages(registrations.getTotalPages())
                        .hasNext(registrations.hasNext())
                        .hasPrev(registrations.hasPrevious())
                        .build()));
    }

    /**
     * Organizer status change by the transition table of {@link RegistrationService#changeStatus}
     * (#1315), in one transaction with its waitlist effects. Attendees cancel through the
     * deregistration link or {@code DELETE /events/{eventCode}/my-registration}.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<RegistrationResponse> updateRegistration(
            String eventCode, String registrationCode, UpdateRegistrationStatusRequest updates) {
        log.debug("PATCH /api/v1/events/{}/registrations/{}", eventCode, registrationCode);

        String target = updates.getStatus() != null ? updates.getStatus().getValue() : null;
        Registration registration = registrationService.changeStatus(findEvent(eventCode), registrationCode, target);
        registration.setEventCode(eventCode);
        return ResponseEntity.ok(registrationService.enrichRegistrationWithUserData(registration));
    }

    /**
     * Organizer delete of a registration; a freed place goes to the waitlist, a waitlisted one
     * closes its gap ({@link RegistrationService#deleteByOrganizer}, #1315).
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteRegistration(String eventCode, String registrationCode) {
        log.debug("DELETE /api/v1/events/{}/registrations/{}", eventCode, registrationCode);

        registrationService.deleteByOrganizer(findEvent(eventCode), registrationCode);
        return ResponseEntity.noContent().build();
    }

    /** Organizer promotion of a waitlisted registration (Story 10.11): 204, 409 when not on the waitlist. */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> promoteFromWaitlist(String eventCode, String registrationCode) {
        log.debug("POST /api/v1/events/{}/registrations/{}/promote", eventCode, registrationCode);

        registrationOfEvent(eventCode, registrationCode);
        waitlistPromotionService.manuallyPromote(registrationCode);
        return ResponseEntity.noContent().build();
    }

    /**
     * Organizer resend of the confirmation mail with a fresh token, for a registration that is
     * still pending ({@code registered}); any other status answers 409.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ResendConfirmationResponse> resendConfirmationEmail(
            String eventCode, String registrationCode) {
        log.info("POST /api/v1/events/{}/registrations/{}/resend-confirmation", eventCode, registrationCode);

        Event event = findEvent(eventCode);
        Registration registration = registrationOfEvent(event, registrationCode);
        if (!"registered".equalsIgnoreCase(registration.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ResendConfirmationResponse().message(
                            "Confirmation email can only be resent for pending registrations. "
                            + "Current status: " + registration.getStatus()));
        }

        UserResponse attendee = sendConfirmation(registration, event);
        log.info("Confirmation email resent for registration {}: attendee={}", registrationCode,
                LoggingUtils.maskEmail(attendee.getEmail()));
        return ResponseEntity.ok(new ResendConfirmationResponse().message(
                "Confirmation email resent to " + attendee.getEmail()));
    }

    /**
     * The signed-in user's registration for the event (Story 10.10). Always 200: {@code registered}
     * says whether there is one, so the browser logs no 404.
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MyRegistrationResponse> getMyRegistration(String eventCode) {
        log.debug("GET /api/v1/events/{}/my-registration", eventCode);

        // ADR-001: the custom:username claim, not the sub UUID.
        String username = securityContextHelper.getCurrentUsername();
        return ResponseEntity.ok(registrationService.getMyRegistration(eventCode, username));
    }

    /**
     * Registration of the signed-in user from their profile, confirmed at once (or waitlisted),
     * without form or confirmation mail.
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreateMyRegistration201Response> createMyRegistration(String eventCode) {
        log.debug("POST /api/v1/events/{}/my-registration", eventCode);

        String username = securityContextHelper.getCurrentUsername();
        String email = securityContextHelper.getCurrentUserEmail();
        Registration registration = registrationService.createRegistrationForAuthenticatedUser(
                eventCode, username, email);

        String message = "waitlist".equals(registration.getStatus())
                ? "You have been added to the waitlist for this event."
                : "Registration confirmed.";
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateMyRegistration201Response(message, email));
    }

    /** Cancels the signed-in user's active registration (no mail; the waitlist moves up). */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteMyRegistration(String eventCode) {
        log.debug("DELETE /api/v1/events/{}/my-registration", eventCode);

        String username = securityContextHelper.getCurrentUsername();
        Event event = findEvent(eventCode);
        Registration registration = registrationService.findActiveOfUser(event, username);

        registrationService.cancelRegistration(registration);
        log.info("Authenticated cancellation: registration {} cancelled for user {} at event {}",
                registration.getRegistrationCode(), username, eventCode);
        return ResponseEntity.noContent().build();
    }

    private Event findEvent(String eventCode) {
        return eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));
    }

    private Registration registrationOfEvent(String eventCode, String registrationCode) {
        return registrationOfEvent(findEvent(eventCode), registrationCode);
    }

    /** 404 for an unknown code and for a code of another event alike ({@link RegistrationService#findOfEvent}). */
    private Registration registrationOfEvent(Event event, String registrationCode) {
        return registrationService.findOfEvent(event, registrationCode);
    }

    /**
     * Mails the confirmation link (fresh 48 h token, Story 4.1.5c) with the deregistration link
     * (Story 10.12) to the attendee, in German. Returns the attendee as CUMS knows them.
     */
    private UserResponse sendConfirmation(Registration registration, Event event) {
        String confirmationToken = confirmationTokenService.generateConfirmationToken(
                registration.getId(), event.getEventCode());
        UserResponse attendee = userApiClient.getUserByUsername(registration.getAttendeeUsername());
        String deregistrationUrl = registration.getDeregistrationToken() != null
                ? appBaseUrl + "/deregister?token=" + registration.getDeregistrationToken()
                : null;
        registrationEmailService.sendRegistrationConfirmation(
                registration, attendee, event, confirmationToken, deregistrationUrl, Locale.GERMAN);
        log.info("Confirmation email queued for registration {}", registration.getRegistrationCode());
        return attendee;
    }
}
