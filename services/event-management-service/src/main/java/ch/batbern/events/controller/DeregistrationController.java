package ch.batbern.events.controller;

import ch.batbern.events.dto.generated.DeregistrationByEmailRequest;
import ch.batbern.events.dto.generated.DeregistrationRequest;
import ch.batbern.events.dto.generated.DeregisterByToken200Response;
import ch.batbern.events.dto.generated.DeregistrationVerifyResponse;
import ch.batbern.events.dto.generated.RequestDeregistrationByEmail200Response;
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
 * TODO: add per-IP rate limiting to /by-email to prevent email spam abuse — see Issue #XXX
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
     * @param token UUID deregistration token (from email link)
     * @return 200 with registration summary, 404 if token invalid or already cancelled
     */
    @GetMapping("/verify")
    public ResponseEntity<DeregistrationVerifyResponse> verifyToken(@RequestParam String token) {
        DeregistrationService.DeregistrationVerifyResult result =
                deregistrationService.verifyToken(java.util.UUID.fromString(token));

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
}
