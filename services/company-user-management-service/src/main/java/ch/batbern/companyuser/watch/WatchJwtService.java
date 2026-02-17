package ch.batbern.companyuser.watch;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Generates HMAC-SHA256 signed JWTs for Watch organizer authentication.
 * W2.2: NFR16 — JWT lifespan 1 hour, auto-refresh 10 min before expiry.
 *
 * JWT Claims:
 * - sub: organizer username
 * - role: ORGANIZER
 * - iat: issued at
 * - exp: expiry (1 hour from issuance)
 */
@Service
@Slf4j
public class WatchJwtService {

    private static final long JWT_TTL_HOURS = 1;

    private final byte[] signingKey;

    public WatchJwtService(
            @Value("${watch.jwt.secret:batbern-watch-dev-secret-key-min-32-chars}") String secret) {
        // HMAC-SHA256 requires at least 256-bit (32-byte) key
        this.signingKey = secret.getBytes();
        if (this.signingKey.length < 32) {
            log.warn("Watch JWT secret is shorter than recommended 32 bytes");
        }
    }

    /**
     * Generate a signed JWT for the given organizer username.
     *
     * @param username organizer username (the subject claim)
     * @return signed JWT string
     */
    public String generateToken(String username) {
        try {
            Date issuedAt = new Date();
            Date expiresAt = new Date(issuedAt.getTime() + JWT_TTL_HOURS * 3600 * 1000);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(username)
                    .claim("role", "ORGANIZER")
                    .issueTime(issuedAt)
                    .expirationTime(expiresAt)
                    .issuer("batbern-watch")
                    .build();

            JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
            SignedJWT signedJWT = new SignedJWT(header, claims);

            JWSSigner signer = new MACSigner(signingKey);
            signedJWT.sign(signer);

            return signedJWT.serialize();

        } catch (JOSEException e) {
            log.error("Failed to generate watch JWT for user: {}", username, e);
            throw new IllegalStateException("Watch JWT generation failed", e);
        }
    }

    /**
     * Returns the expiry timestamp for a token generated at the current time.
     */
    public LocalDateTime getExpiresAt() {
        return LocalDateTime.now().plusHours(JWT_TTL_HOURS);
    }
}
