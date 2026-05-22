package ch.batbern.events.controller;

import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.dto.ValidateTokenRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for speaker portal token validation.
 * Story 6.1a: Magic Link Infrastructure (AC5).
 *
 * @deprecated Story 11.E.3 disconnected this controller from the frontend; the magic-link flow
 *     is gone. Code review 2026-05-18 (P3): the prior arrangement (class-level
 *     {@code @PreAuthorize("hasRole('SPEAKER')")} on a controller that validates magic-link
 *     tokens) was nonsensical — an authenticated speaker has no reason to validate a
 *     pre-authentication artefact. The endpoint now returns {@code 410 Gone} unconditionally
 *     so any straggler caller (cached SPA bundle, stale curl script) gets a clear signal
 *     instead of executing dead validation logic. {@code MagicLinkService} is no longer
 *     injected here; Story 11.F.1 deletes this file along with {@code MagicLinkService} and
 *     the {@code magic_link_tokens} table.
 */
@Deprecated
@RestController
@RequestMapping("/api/v1/speaker-portal")
public class SpeakerPortalTokenController {

    /**
     * Always returns {@code 410 Gone}. The magic-link auth flow was removed in Story 11.E.3.
     * The request body is still typed for compatibility with any client that hasn't been
     * recompiled, but its content is ignored.
     */
    @PostMapping("/validate-token")
    public ResponseEntity<TokenValidationResult> validateToken(
            @Valid @RequestBody ValidateTokenRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }
}
