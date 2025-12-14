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
 * Service for generating and validating email confirmation tokens.
 *
 * Generates short-lived JWT tokens (48h validity) for email-based registration confirmation.
 * Tokens contain registration details and cannot be forged without the secret key.
 *
 * Security Features:
 * - JWT signature validation (HMAC-SHA256)
 * - Time-based expiry (48 hours)
 * - Type validation (only "email-confirmation" tokens accepted)
 * - One-time use tracking (via confirmation timestamp in database)
 */
@Service
public class ConfirmationTokenService {

    private final SecretKey signingKey;
    private final long validityMs = 48 * 60 * 60 * 1000; // 48 hours

    public ConfirmationTokenService(@Value("${jwt.secret}") String secret) {
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
     * Generate confirmation token for registration.
     *
     * @param registrationId  UUID of the registration
     * @param eventCode       Event code (e.g., "BATbern57")
     * @return JWT token string (valid for 48 hours)
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
