package ch.batbern.events.controller;

import ch.batbern.events.dto.FeaturedThanksResponse;
import ch.batbern.events.dto.SubmitThanksRequest;
import ch.batbern.events.dto.ThanksCountResponse;
import ch.batbern.events.dto.ThanksFeaturePatchRequest;
import ch.batbern.events.dto.ThanksNoteResponse;
import ch.batbern.events.service.OrganizerThanksService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     * PUBLIC featured marquee (Story 7.7). Up to {@code limit} (capped at 9) random organizer-
     * featured, logged-in thank-yous across ALL events, enriched with first name + company logo.
     * Anonymous notes are structurally excluded; the raw username is never returned.
     */
    @GetMapping("/thanks/featured")
    public ResponseEntity<List<FeaturedThanksResponse>> getFeaturedThanks(
            @RequestParam(name = "limit", defaultValue = "9") int limit) {
        return ResponseEntity.ok(thanksService.getFeaturedThanks(limit));
    }

    /**
     * ORGANIZER feature-toggle (Story 7.7). Marks/un-marks a thank-you for the public marquee.
     * Featuring an anonymous note is rejected (409 THANKS_NOT_FEATURABLE).
     */
    @PatchMapping("/events/{eventCode}/thanks/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ThanksNoteResponse> setFeatured(
            @PathVariable String eventCode,
            @PathVariable UUID id,
            @Valid @RequestBody ThanksFeaturePatchRequest request) {
        return ResponseEntity.ok(thanksService.setFeatured(eventCode, id, request.featured()));
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
            String firstHop = xForwardedFor.split(",")[0].trim();
            // A blank first hop (e.g. "X-Forwarded-For: , 1.2.3.4") would collapse every caller into
            // one rate-limit bucket — fall back to the remote address instead.
            if (!firstHop.isEmpty()) {
                return firstHop;
            }
        }
        return request.getRemoteAddr();
    }
}
