package ch.batbern.events.controller;

import ch.batbern.events.registrations.dto.generated.DeregistrationByEmailRequest;
import ch.batbern.events.registrations.dto.generated.DeregistrationRequest;
import ch.batbern.events.registrations.dto.generated.DeregisterByToken200Response;
import ch.batbern.events.registrations.dto.generated.DeregistrationVerifyResponse;
import ch.batbern.events.registrations.dto.generated.RequestDeregistrationByEmail200Response;
import ch.batbern.events.service.DeregistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;

/**
 * Self-service deregistration endpoints.
 * <p>
 * Story 10.12 (AC3, AC10): Three public endpoints for attendee-initiated cancellation.
 * No authentication required — the deregistration token IS the auth mechanism (ADR-005).
 * <p>
 * Exception mapping is delegated to {@link ch.batbern.events.exception.GlobalExceptionHandler}:
 * - NoSuchElementException → 404
 * - IllegalStateException  → 409
 * <p>
 * Rate limiting: No rate limiting infrastructure (bucket4j) exists in this service.
 * TODO: add per-IP rate limiting to /by-email to prevent email spam abuse.
 * Per-IP throttle: max 5 requests/hour per source IP (bucket4j or Spring rate-limiter).
 * Track in backlog as "Rate-limit deregistration-by-email endpoint"
 */
@RestController
@RequestMapping("/api/v1/registrations/deregister")
@RequiredArgsConstructor
@Slf4j
public class DeregistrationController {

    private final DeregistrationService deregistrationService;

    /**
     * Verify a deregistration token and return registration details.
     * Used by the frontend to show a confirmation page before the attendee confirms.
     *
     * <p>The {@code token} param is optional and parsed defensively: a missing, blank, or
     * malformed (non-UUID) value is treated as an invalid link → 404, the same outcome the
     * frontend already handles for an unknown/expired token. Field report (2026-06-16
     * deregistration-call blast): a recipient's mail client truncated the link at
     * {@code ?token=}, so verify was called with no token; the missing {@code @RequestParam}
     * raised {@code MissingServletRequestParameterException} which fell through to the
     * catch-all handler and returned a 500. A bad link is a not-found link, never a 500.
     *
     * @param token UUID deregistration token (from email link)
     * @return 200 with registration summary; 404 if the token is absent, malformed, unknown,
     *         or already cancelled
     */
    @GetMapping("/verify")
    public ResponseEntity<DeregistrationVerifyResponse> verifyToken(
            @RequestParam(required = false) String token) {
        DeregistrationService.DeregistrationVerifyResult result =
                deregistrationService.verifyToken(parseTokenOrNotFound(token));

        DeregistrationVerifyResponse response = new DeregistrationVerifyResponse(
                result.registrationCode(),
                result.eventCode(),
                result.eventTitle(),
                result.eventDate().atOffset(ZoneOffset.UTC),
                result.attendeeFirstName()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Cancel a registration using its deregistration token.
     * Triggers waitlist promotion after successful cancellation.
     *
     * @param request body containing the UUID token
     * @return 200 on success, 404 if token not found, 409 if already cancelled
     */
    @PostMapping
    public ResponseEntity<DeregisterByToken200Response> deregisterByToken(
            @RequestBody DeregistrationRequest request) {
        deregistrationService.deregisterByToken(request.getToken());
        DeregisterByToken200Response response = new DeregisterByToken200Response()
                .message("Registration successfully cancelled.");
        return ResponseEntity.ok(response);
    }

    /**
     * Request a deregistration link email by providing email + eventCode.
     * Always returns 200 to prevent email enumeration (anti-enumeration pattern — AC3).
     * If a matching active registration is found, a deregistration link email is sent asynchronously.
     *
     * @param request body containing email and eventCode
     * @return 200 always
     */
    @PostMapping("/by-email")
    public ResponseEntity<RequestDeregistrationByEmail200Response> deregisterByEmail(
            @RequestBody DeregistrationByEmailRequest request) {
        // Fire and forget — anti-enumeration: never surface whether registration was found
        deregistrationService.deregisterByEmail(request.getEmail(), request.getEventCode());
        RequestDeregistrationByEmail200Response response = new RequestDeregistrationByEmail200Response()
                .message("If you have a registration for this event, you'll receive an email"
                        + " with a cancellation link shortly.");
        return ResponseEntity.ok(response);
    }

    /**
     * Parse a raw token string into a UUID, mapping any absent/blank/malformed value to a
     * {@link java.util.NoSuchElementException} so {@link ch.batbern.events.exception.GlobalExceptionHandler}
     * renders the same 404 "invalid_token" response as an unknown token (never a 400 or 500).
     */
    private java.util.UUID parseTokenOrNotFound(String token) {
        if (token == null || token.isBlank()) {
            throw new java.util.NoSuchElementException("invalid_token");
        }
        try {
            return java.util.UUID.fromString(token.trim());
        } catch (IllegalArgumentException ex) {
            throw new java.util.NoSuchElementException("invalid_token");
        }
    }
}
