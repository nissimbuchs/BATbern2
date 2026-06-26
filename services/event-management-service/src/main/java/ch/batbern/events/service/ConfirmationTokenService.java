package ch.batbern.events.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Service for generating and validating email confirmation and cancellation tokens.
 *
 * Generates signed JWT tokens for email-based registration confirmation and cancellation.
 * Tokens contain registration details and cannot be forged without the secret key.
 *
 * Validity is configurable via {@code app.registration.confirmation-token-validity-hours}
 * (default 4 days / 96 hours). It MUST stay below the registration cleanup window
 * ({@code app.registration.cleanup-after-hours}) so a still-valid link always has a
 * registration row to confirm — see {@link RegistrationCleanupService}.
 *
 * Security Features:
 * - JWT signature validation (HMAC-SHA256)
 * - Time-based expiry (configurable, default 4 days)
 * - Type validation (only "email-confirmation" or "registration-cancellation" tokens accepted)
 * - One-time use tracking (via confirmation timestamp or deletion in database)
 */
@Service
public class ConfirmationTokenService {

    /**
     * Default token validity: 4 days (96 hours). Widened from the original 48h so attendees
     * have a longer window to click the confirmation link. MUST stay below the registration
     * cleanup window so a still-valid link always has a row to confirm.
     */
    public static final long DEFAULT_VALIDITY_HOURS = 96;

    private final SecretKey signingKey;
    private final long validityMs;

    @org.springframework.beans.factory.annotation.Autowired
    public ConfirmationTokenService(
            @Value("${jwt.secret}") String secret,
            @Value("${app.registration.confirmation-token-validity-hours:96}") long validityHours) {
        this.validityMs = validityHours * 60L * 60L * 1000L;
        // Use provided secret or generate a secure random key for dev/test
        if (secret == null || secret.isEmpty() || "changeme".equals(secret)) {
            this.signingKey = Jwts.SIG.HS256.key().build();
            System.err.println("WARNING: Using randomly generated JWT secret. Configure jwt.secret in production!");
        } else {
            // Use configured secret (must be at least 256 bits for HS256)
            this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Convenience constructor using the default 4-day validity. Intended for unit tests;
     * production uses the {@code @Autowired} constructor with the configurable validity.
     */
    public ConfirmationTokenService(String secret) {
        this(secret, DEFAULT_VALIDITY_HOURS);
    }

    /**
     * Generate confirmation token for registration.
     *
     * @param registrationId  UUID of the registration
     * @param eventCode       Event code (e.g., "BATbern57")
     * @return JWT token string (valid for the configured window, default 4 days)
     */
    public String generateConfirmationToken(UUID registrationId, String eventCode) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMs);

        return Jwts.builder()
                .claim("registrationId", registrationId.toString())
                .claim("eventCode", eventCode)
                .claim("type", "email-confirmation")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validate and parse confirmation token.
     *
     * @param token JWT token from email link
     * @return Claims containing registrationId, eventCode, type
     * @throws io.jsonwebtoken.JwtException if token is invalid, expired, or wrong type
     */
    public Claims validateConfirmationToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // Verify token type
        String type = claims.get("type", String.class);
        if (!"email-confirmation".equals(type)) {
            throw new IllegalArgumentException("Invalid token type: " + type);
        }

        return claims;
    }

    /**
     * Extract registration ID from validated token.
     *
     * @param claims Validated token claims
     * @return Registration UUID
     */
    public UUID getRegistrationId(Claims claims) {
        String registrationIdStr = claims.get("registrationId", String.class);
        return UUID.fromString(registrationIdStr);
    }

    /**
     * Extract event code from validated token.
     *
     * @param claims Validated token claims
     * @return Event code
     */
    public String getEventCode(Claims claims) {
        return claims.get("eventCode", String.class);
    }

}
