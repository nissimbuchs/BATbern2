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
 * #1183 D: the signed link that lets a person claim an earlier profile under their name. Sent to
 * the earlier profile's address, so opening it proves the person holds that mailbox; bound to the
 * claimer's profile id, so it only works under their login. Stateless HMAC JWT like
 * {@link AdditionalEmailVerificationTokenService}, with its own {@code type} so neither token is
 * accepted in place of the other.
 */
@Service
@Slf4j
public class ProfileClaimTokenService {

    private static final String TOKEN_TYPE = "profile-claim";

    private final SecretKey signingKey;
    private final long validityMs;

    public ProfileClaimTokenService(
            @Value("${jwt.secret}") String secret,
            @Value("${batbern.user.additional-emails.verification.token-validity-hours:48}") long validityHours) {
        this.validityMs = validityHours * 60L * 60L * 1000L;
        if (secret == null || secret.isEmpty() || "changeme".equals(secret)) {
            this.signingKey = Jwts.SIG.HS256.key().build();
            log.warn("Using a random key for profile-claim tokens. Configure jwt.secret in production!");
        } else {
            this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    public String generateToken(UUID claimerId, UUID candidateId, String candidateEmail) {
        Date now = new Date();
        return Jwts.builder()
                .claim("type", TOKEN_TYPE)
                .claim("claimerId", claimerId.toString())
                .claim("candidateId", candidateId.toString())
                .claim("email", candidateEmail)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * @throws io.jsonwebtoken.ExpiredJwtException when expired
     * @throws io.jsonwebtoken.JwtException or IllegalArgumentException when forged, malformed or of another type
     */
    public Claim validate(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        if (!TOKEN_TYPE.equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Invalid token type");
        }
        return new Claim(UUID.fromString(claims.get("claimerId", String.class)),
                UUID.fromString(claims.get("candidateId", String.class)), claims.get("email", String.class));
    }

    public record Claim(UUID claimerId, UUID candidateId, String candidateEmail) {
    }
}
