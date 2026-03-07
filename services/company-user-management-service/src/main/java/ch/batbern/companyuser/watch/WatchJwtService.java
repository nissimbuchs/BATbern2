package ch.batbern.companyuser.watch;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Generates HMAC-SHA256 signed JWTs for Watch organizer authentication.
 * W2.2: NFR16 — JWT lifespan 1 hour, auto-refresh 10 min before expiry.
 *
 * JWT Claims:
 * - sub: organizer username
 * - role: ORGANIZER
 * - iss: batbern-watch
 * - iat: issued at
 * - exp: expiry (1 hour from issuance)
 */
@Service
@Slf4j
public class WatchJwtService {

    private static final long JWT_TTL_SECONDS = 3600L;
    private static final String DEV_DEFAULT_SECRET = "batbern-watch-dev-secret-key-min-32-chars";

    private final byte[] signingKey;
    private final String rawSecret;
    private final Environment environment;

    public WatchJwtService(
            @Value("${watch.jwt.secret:" + DEV_DEFAULT_SECRET + "}") String secret,
            Environment environment) {
        // M4: Use explicit UTF-8 charset — platform default varies across systems
        this.signingKey = secret.getBytes(StandardCharsets.UTF_8);
        this.rawSecret = secret;
        this.environment = environment;
        if (this.signingKey.length < 32) {
            log.warn("Watch JWT secret is shorter than recommended 32 bytes");
        }
    }

    @PostConstruct
    void validateSecretInProduction() {
        boolean isDevProfile = java.util.Arrays.asList(environment.getActiveProfiles()).contains("local")
                || java.util.Arrays.asList(environment.getActiveProfiles()).contains("test");
        if (!isDevProfile && DEV_DEFAULT_SECRET.equals(rawSecret)) {
            throw new IllegalStateException(
                "Watch JWT secret is set to the well-known development default. "
                + "Inject a strong secret via 'watch.jwt.secret' in non-dev environments.");
        }
    }

    /**
     * Result type pairing the JWT string with its correlated expiry timestamp.
     * The expiry is captured atomically during token generation (H3+M3 fix).
     *
     * @param jwt       signed JWT string
     * @param expiresAt ISO-8601 UTC timestamp (e.g. "2026-02-16T15:30:00Z") parseable by Swift ISO8601DateFormatter
     */
    public record WatchJwtResult(String jwt, String expiresAt) {}

    /**
     * Generate a signed JWT and return it together with its exact expiry timestamp.
     * The expiry is derived from the same Instant used in the JWT exp claim — no drift.
     *
     * @param username organizer username (the subject claim)
     * @return WatchJwtResult containing jwt string and ISO-8601 UTC expiresAt
     */
    public WatchJwtResult generateTokenWithExpiry(String username) {
        try {
            Instant issuedAt = Instant.now();
            Instant expiresAt = issuedAt.plusSeconds(JWT_TTL_SECONDS);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(username)
                    .claim("role", "ORGANIZER")
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(expiresAt))
                    .issuer("batbern-watch")
                    .build();

            JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
            SignedJWT signedJWT = new SignedJWT(header, claims);

            JWSSigner signer = new MACSigner(signingKey);
            signedJWT.sign(signer);

            // ISO-8601 UTC string: "2026-02-16T15:30:00Z" — parseable by Swift ISO8601DateFormatter
            String expiresAtIso = DateTimeFormatter.ISO_INSTANT.format(expiresAt);
            return new WatchJwtResult(signedJWT.serialize(), expiresAtIso);

        } catch (JOSEException e) {
            log.error("Failed to generate watch JWT for user: {}", username, e);
            throw new IllegalStateException("Watch JWT generation failed", e);
        }
    }
}
