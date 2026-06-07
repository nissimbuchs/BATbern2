package ch.batbern.companyuser.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Additional-email verification (v2): generates and validates signed verification
 * tokens for the additional-email ownership-proof flow.
 *
 * <p>Tokens are stateless HMAC-SHA256 JWTs — no database row stores them. The
 * {@code additionalEmailId} claim binds each token to the exact
 * {@code user_additional_emails} row UUID it was issued for, so deleting (or
 * deleting-then-re-adding, which mints a fresh UUID) the row hard-invalidates the
 * token without any server-side bookkeeping. The {@code email} claim is a defence
 * in depth so a confirm only succeeds if the row still carries the same address.
 *
 * <p>Validity is configurable via
 * {@code batbern.user.additional-emails.verification.token-validity-hours}
 * (default 48h). Mirrors the JJWT pattern in event-management-service's
 * {@code ConfirmationTokenService}.
 */
@Service
@Slf4j
public class AdditionalEmailVerificationTokenService {

    /** Default token validity: 48 hours. */
    public static final long DEFAULT_VALIDITY_HOURS = 48;

    private static final String TOKEN_TYPE = "additional-email-verification";

    private final SecretKey signingKey;
    private final long validityMs;

    @org.springframework.beans.factory.annotation.Autowired
    public AdditionalEmailVerificationTokenService(
            @Value("${jwt.secret}") String secret,
            @Value("${batbern.user.additional-emails.verification.token-validity-hours:48}")
                    long validityHours) {
        this.validityMs = validityHours * 60L * 60L * 1000L;
        // Use provided secret or generate a secure random key for dev/test.
        if (secret == null || secret.isEmpty() || "changeme".equals(secret)) {
            this.signingKey = Jwts.SIG.HS256.key().build();
            log.warn("Using randomly generated JWT secret for additional-email verification. "
                    + "Configure jwt.secret in production!");
        } else {
            this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Convenience constructor using the default 48h validity. Intended for unit tests.
     */
    public AdditionalEmailVerificationTokenService(String secret) {
        this(secret, DEFAULT_VALIDITY_HOURS);
    }

    /**
     * Generate a verification token for a given additional-email row.
     *
     * @param additionalEmailId UUID of the {@code user_additional_emails} row
     * @param email             the additional email address (lower-cased)
     * @return signed JWT string valid for the configured window (default 48h)
     */
    public String generateToken(UUID additionalEmailId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMs);

        return Jwts.builder()
                .claim("additionalEmailId", additionalEmailId.toString())
                .claim("email", email)
                .claim("type", TOKEN_TYPE)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validate and parse a verification token.
     *
     * @param token JWT token from the email link
     * @return parsed claims (additionalEmailId, email, type)
     * @throws io.jsonwebtoken.ExpiredJwtException if the token is expired
     * @throws io.jsonwebtoken.JwtException        if the signature is invalid or malformed
     * @throws IllegalArgumentException            if the token type does not match, or the
     *                                             {@code additionalEmailId} / {@code email}
     *                                             claims are missing or malformed
     */
    public Claims validateToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String type = claims.get("type", String.class);
        if (!TOKEN_TYPE.equals(type)) {
            throw new IllegalArgumentException("Invalid token type: " + type);
        }

        // Validate the payload claims here (inside the parse path) so a signed-but-malformed
        // token — missing/non-UUID additionalEmailId or missing email — surfaces as an
        // IllegalArgumentException that the caller maps to a 400 TOKEN_INVALID, rather than
        // blowing up later as a generic 500 during getAdditionalEmailId()/getEmail() extraction.
        getAdditionalEmailId(claims);
        String email = getEmail(claims);
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Missing email claim in verification token");
        }

        return claims;
    }

    /**
     * Extract the additional-email row UUID from validated claims.
     *
     * @throws IllegalArgumentException if the claim is absent or not a valid UUID
     *                                  (a null claim would otherwise NPE inside UUID.fromString)
     */
    public UUID getAdditionalEmailId(Claims claims) {
        String value = claims.get("additionalEmailId", String.class);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing additionalEmailId claim in verification token");
        }
        return UUID.fromString(value);
    }

    /**
     * Extract the email from validated claims.
     */
    public String getEmail(Claims claims) {
        return claims.get("email", String.class);
    }
}
