package ch.batbern.events.controller;

import ch.batbern.events.core.api.generated.OrganizerThanksApi;
import ch.batbern.events.core.dto.generated.FeaturedThanksResponse;
import ch.batbern.events.core.dto.generated.SubmitThanksRequest;
import ch.batbern.events.core.dto.generated.ThanksCountResponse;
import ch.batbern.events.core.dto.generated.ThanksFeaturePatchRequest;
import ch.batbern.events.core.dto.generated.ThanksNoteResponse;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.OrganizerThanksService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * "Thank the Organizers" endpoints (Story 7.4 / 7.7).
 *
 * <p>{@code implements} the generated {@link OrganizerThanksApi} interface (events-core spec,
 * ADR-006 contract-first); the class-level {@code @RequestMapping("/api/v1")} supplies the version
 * prefix. Submit + read + featured-marquee are PUBLIC (no {@code @PreAuthorize}); the feature-toggle
 * is organizer-only. The generated interface does not pass {@code Authentication}/{@code
 * HttpServletRequest}, so those are resolved from {@link SecurityContextHolder}/{@link
 * RequestContextHolder} (organizer-note gating + anonymous rate-limit client IP).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class OrganizerThanksController implements OrganizerThanksApi {

    private final OrganizerThanksService thanksService;
    private final SecurityContextHelper securityContextHelper;

    /** Submit a thank-you (public). Logged-in → deduped upsert by username; anonymous → clap row. */
    @Override
    public ResponseEntity<ThanksCountResponse> submitThanks(String eventCode, SubmitThanksRequest request) {
        String username = resolveUsername();
        String note = request != null ? request.getNote() : null;
        long count = thanksService.submitThanks(eventCode, username, note, getClientIp(currentHttpRequest()));
        return ResponseEntity.ok(new ThanksCountResponse(count));
    }

    /** Read the aggregate count (public). Organizer callers also get the submitted notes (AC6). */
    @Override
    public ResponseEntity<ThanksCountResponse> getThanks(String eventCode) {
        boolean isOrganizer = hasRole(SecurityContextHolder.getContext().getAuthentication(), "ROLE_ORGANIZER");
        return ResponseEntity.ok(thanksService.getThanks(eventCode, isOrganizer));
    }

    /** PUBLIC featured marquee (Story 7.7) — up to {@code limit} (capped at 9) random featured notes. */
    @Override
    public ResponseEntity<List<FeaturedThanksResponse>> getFeaturedThanks(Integer limit) {
        return ResponseEntity.ok(thanksService.getFeaturedThanks(limit));
    }

    /** ORGANIZER feature-toggle (Story 7.7). Featuring an anonymous note → 409 THANKS_NOT_FEATURABLE. */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ThanksNoteResponse> setThanksFeatured(
            String eventCode, UUID id, ThanksFeaturePatchRequest request) {
        return ResponseEntity.ok(thanksService.setFeatured(eventCode, id, request.getFeatured()));
    }

    /**
     * The logged-in caller's CANONICAL username (ADR-003 meaningful id), or {@code null} for
     * anonymous — via {@code custom:username} claim (+ Pattern 3b DB fallback), NOT the Cognito sub.
     */
    private String resolveUsername() {
        return securityContextHelper.getCurrentUsernameOrNull();
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

    private static HttpServletRequest currentHttpRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
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
