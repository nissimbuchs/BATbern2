package ch.batbern.events.controller;

import ch.batbern.events.dto.SubmitThanksRequest;
import ch.batbern.events.dto.ThanksCountResponse;
import ch.batbern.events.service.OrganizerThanksService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Thank the Organizers" endpoints (Story 7.4).
 *
 * <p>Both endpoints are PUBLIC (no {@code @PreAuthorize}) — anonymous allowed. Authentication is
 * OPTIONAL and read via the injected {@link Authentication} (EventPhotoController null-check
 * pattern): a logged-in caller is deduped + may attach a note tied to their username; an
 * anonymous caller is a rate-limited, Turnstile-guarded clap.
 *
 * <ul>
 *   <li>{@code POST /api/v1/events/{eventCode}/thanks} — submit; returns the new aggregate count.</li>
 *   <li>{@code GET  /api/v1/events/{eventCode}/thanks} — aggregate count (public). Organizers
 *       additionally receive the submitted notes (AC6); the public response is count-only.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class OrganizerThanksController {

    private final OrganizerThanksService thanksService;

    /**
     * Submit a thank-you (public). Logged-in → deduped upsert by username; anonymous → clap row
     * (Turnstile-guarded at the gateway + per-(event,IP) rate-limited at the service).
     */
    @PostMapping("/events/{eventCode}/thanks")
    public ResponseEntity<ThanksCountResponse> submitThanks(
            @PathVariable String eventCode,
            @Valid @RequestBody(required = false) SubmitThanksRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        String username = resolveUsername(authentication);
        String note = request != null ? request.getNote() : null;
        long count = thanksService.submitThanks(eventCode, username, note, getClientIp(httpRequest));
        return ResponseEntity.ok(ThanksCountResponse.ofCount(count));
    }

    /**
     * Read the aggregate count (public). Organizer callers also get the submitted notes (AC6).
     */
    @GetMapping("/events/{eventCode}/thanks")
    public ResponseEntity<ThanksCountResponse> getThanks(
            @PathVariable String eventCode,
            Authentication authentication) {
        boolean isOrganizer = hasRole(authentication, "ROLE_ORGANIZER");
        return ResponseEntity.ok(thanksService.getThanks(eventCode, isOrganizer));
    }

    /**
     * The logged-in username, or {@code null} for anonymous. On this permitAll endpoint an
     * unauthenticated request arrives as either a {@code null} Authentication (this service in
     * isolation) or an {@link AnonymousAuthenticationToken} (via the gateway) — both map to null.
     */
    private String resolveUsername(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (role.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /** Client IP for the anonymous rate limit — first X-Forwarded-For hop, else remote address. */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
