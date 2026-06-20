package ch.batbern.events.watch;

import ch.batbern.events.watch.dto.LiveTimingActionRequest;
import ch.batbern.events.watch.dto.LiveTimingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Story 15.1: REST polling endpoints for live agenda timing — the replacement for the
 * STOMP {@code /topic/events/{eventCode}/state} broadcast and
 * {@code /app/watch/events/{eventCode}/action} publish.
 *
 * <p>{@code GET /api/v1/events/{eventCode}/live-timing} is anonymous-readable (the presenter
 * screen is public, mirroring the old anonymous topic visibility) and supports
 * {@code If-None-Match} → {@code 304}. {@code POST .../live-timing/actions} requires an
 * authenticated organizer.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/events/{eventCode}/live-timing")
@RequiredArgsConstructor
public class LiveTimingController {

    private static final String ORGANIZER_AUTHORITY = "ROLE_ORGANIZER";

    private final LiveTimingService liveTimingService;

    /**
     * Polls the live-timing snapshot. Returns {@code 304} when the caller's {@code If-None-Match}
     * matches the current version-derived ETag. An authenticated organizer's poll also refreshes
     * their presence heartbeat (anonymous presenter polls do not).
     */
    @GetMapping
    public ResponseEntity<LiveTimingResponse> getLiveTiming(
            @PathVariable String eventCode,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            Authentication authentication) {

        if (isOrganizer(authentication)) {
            liveTimingService.recordOrganizerPoll(eventCode, authentication.getName());
        }

        long version = liveTimingService.getVersion(eventCode);
        String etag = etagFor(eventCode, version);

        if (ifNoneMatch != null && etagMatches(ifNoneMatch, etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }

        LiveTimingResponse snapshot = liveTimingService.getLiveTiming(eventCode);
        return ResponseEntity.ok()
                .eTag(etagFor(eventCode, snapshot.version()))
                .body(snapshot);
    }

    /**
     * Applies a timing action (END/EXTEND/DELAY) and returns the post-cascade snapshot
     * with the bumped version. Requires an authenticated organizer.
     */
    @PostMapping("/actions")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<LiveTimingResponse> applyAction(
            @PathVariable String eventCode,
            @Valid @RequestBody LiveTimingActionRequest request,
            Authentication authentication) {

        LiveTimingResponse snapshot =
                liveTimingService.applyAction(eventCode, request, authentication.getName());
        return ResponseEntity.ok()
                .eTag(etagFor(eventCode, snapshot.version()))
                .body(snapshot);
    }

    /** Version-derived strong ETag: {@code "evt-<eventCode>-<version>"}. */
    private String etagFor(String eventCode, long version) {
        return "\"evt-" + eventCode + "-" + version + "\"";
    }

    /** Tolerant match: handles the weak prefix and missing quotes from clients/proxies. */
    private boolean etagMatches(String ifNoneMatch, String etag) {
        String candidate = ifNoneMatch.trim();
        if (candidate.startsWith("W/")) {
            candidate = candidate.substring(2).trim();
        }
        String bare = etag.replace("\"", "");
        return candidate.equals(etag) || candidate.replace("\"", "").equals(bare);
    }

    private boolean isOrganizer(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                        .anyMatch(a -> ORGANIZER_AUTHORITY.equals(a.getAuthority()));
    }
}
