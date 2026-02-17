package ch.batbern.events.controller;

import ch.batbern.events.config.JwtConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerAuthResponse;
import ch.batbern.events.dto.SpeakerMagicLoginRequest;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.MagicLinkService;
import ch.batbern.shared.types.TokenAction;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Controller for JWT-based speaker magic link authentication (Story 9.1).
 *
 * Flow: magic link ?jwt=<token> -> validate JWT -> issue VIEW session token -> set cookie
 *
 * Session bridge design: The opaque sessionToken returned in SpeakerAuthResponse lets the
 * frontend redirect to the existing dashboard (/speaker-portal/dashboard?token=<sessionToken>)
 * without any changes to dashboard logic.
 *
 * AC2: Extract JWT from request body, validate, set HTTP-only cookie.
 * AC3: Issue opaque VIEW session token for dashboard redirect.
 * AC5: Return 401 with clear German-language error for invalid/expired JWTs.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class SpeakerMagicLoginController {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerMagicLoginController.class);
    private static final String COOKIE_NAME = "speaker_jwt";
    private static final int COOKIE_MAX_AGE = 30 * 24 * 60 * 60; // 30 days in seconds

    private final JwtConfig jwtConfig;
    private final MagicLinkService magicLinkService;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;

    @Value("${app.cookie.same-site:Strict}")
    private String cookieSameSite;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    public SpeakerMagicLoginController(
            JwtConfig jwtConfig,
            MagicLinkService magicLinkService,
            SpeakerPoolRepository speakerPoolRepository,
            EventRepository eventRepository) {
        this.jwtConfig = jwtConfig;
        this.magicLinkService = magicLinkService;
        this.speakerPoolRepository = speakerPoolRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * POST /api/v1/auth/speaker-magic-login
     *
     * Validates the RS256 JWT from the magic link URL and issues an opaque VIEW session token.
     * Sets an HTTP-only cookie containing the JWT for future cookie-based flows.
     *
     * @param request     body containing the jwtToken string
     * @param response    HTTP response used to append the Set-Cookie header
     * @return 200 with SpeakerAuthResponse on success, 401 on invalid/expired JWT
     */
    @PostMapping("/speaker-magic-login")
    public ResponseEntity<?> speakerMagicLogin(
            @Valid @RequestBody SpeakerMagicLoginRequest request,
            HttpServletResponse response) {

        try {
            // Validate JWT signature and expiry using the application's public key
            Claims claims = Jwts.parser()
                    .verifyWith(jwtConfig.getKeyPair().getPublic())
                    .build()
                    .parseSignedClaims(request.jwtToken())
                    .getPayload();

            // Extract speakerPoolId - try dedicated claim first, fall back to subject
            String speakerPoolIdStr = claims.get("speakerPoolId", String.class);
            if (speakerPoolIdStr == null) {
                speakerPoolIdStr = claims.getSubject();
            }
            UUID speakerPoolId = UUID.fromString(speakerPoolIdStr);

            // Verify SPEAKER role claim is present
            List<?> roles = claims.get("roles", List.class);
            if (roles == null || !roles.contains("SPEAKER")) {
                LOG.warn("JWT is missing SPEAKER role for speakerPool: {}", speakerPoolId);
                return unauthorizedResponse();
            }

            // Look up the SpeakerPool record
            Optional<SpeakerPool> speakerPoolOpt = speakerPoolRepository.findById(speakerPoolId);
            if (speakerPoolOpt.isEmpty()) {
                LOG.warn("SpeakerPool not found for JWT sub: {}", speakerPoolId);
                return unauthorizedResponse();
            }
            SpeakerPool speakerPool = speakerPoolOpt.get();

            // Issue opaque VIEW session token (session bridge: dashboard uses ?token=<sessionToken>)
            String sessionToken = magicLinkService.generateToken(speakerPoolId, TokenAction.VIEW);

            // Set HTTP-only cookie with the original JWT for 30 days
            String cookieHeader = COOKIE_NAME + "=" + request.jwtToken()
                    + "; HttpOnly"
                    + (cookieSecure ? "; Secure" : "")
                    + "; SameSite=" + cookieSameSite
                    + "; Max-Age=" + COOKIE_MAX_AGE
                    + "; Path=/";
            response.addHeader("Set-Cookie", cookieHeader);

            // Fetch event details to enrich the response
            String eventCode = null;
            String eventTitle = null;
            Optional<Event> eventOpt = eventRepository.findById(speakerPool.getEventId());
            if (eventOpt.isPresent()) {
                Event event = eventOpt.get();
                eventCode = event.getEventCode();
                eventTitle = event.getTitle();
            }

            LOG.info("Magic link JWT login successful for speakerPool: {}", speakerPoolId);
            return ResponseEntity.ok(new SpeakerAuthResponse(
                    speakerPoolId,
                    speakerPool.getSpeakerName(),
                    eventCode,
                    eventTitle,
                    sessionToken
            ));

        } catch (JwtException e) {
            LOG.warn("Invalid or expired JWT in magic link login: {}", e.getMessage());
            return unauthorizedResponse();
        } catch (IllegalArgumentException e) {
            LOG.warn("Malformed JWT or invalid speakerPoolId UUID: {}", e.getMessage());
            return unauthorizedResponse();
        }
    }

    private ResponseEntity<Map<String, String>> unauthorizedResponse() {
        return ResponseEntity.status(401).body(Map.of(
                "error", "INVALID_TOKEN",
                "message", "Dieser Link ist nicht mehr gültig. Bitte kontaktiere den Organisator."
        ));
    }
}
