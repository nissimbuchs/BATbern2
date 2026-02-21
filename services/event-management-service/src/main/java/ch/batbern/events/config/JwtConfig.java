package ch.batbern.events.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * JWT configuration for speaker portal JWT tokens (Story 9.1).
 * Uses RS256 asymmetric signing for enhanced security.
 *
 * The key pair is generated once and cached to ensure that all callers within
 * a single Spring application context share the same signing/verification keys.
 * Without caching, auto-generated keys would differ between calls when no keys
 * are configured, causing signature verification failures.
 */
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtConfig {

    private String privateKey;
    private String publicKey;
    private String issuer = "batbern";
    private int expiryDays = 30;

    /** Lazily initialised, then cached for the lifetime of this bean. */
    private KeyPair cachedKeyPair;

    public synchronized KeyPair getKeyPair() {
        if (cachedKeyPair != null) {
            return cachedKeyPair;
        }
        if (privateKey == null || privateKey.isBlank()) {
            // Auto-generate for local development / tests - ephemeral but consistent within this bean instance
            try {
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(2048);
                cachedKeyPair = gen.generateKeyPair();
                return cachedKeyPair;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to generate RSA key pair", e);
            }
        }
        try {
            byte[] privateBytes = Base64.getDecoder().decode(privateKey.replaceAll("\\s+", ""));
            byte[] publicBytes = Base64.getDecoder().decode(publicKey.replaceAll("\\s+", ""));
            PrivateKey privKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            PublicKey pubKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(publicBytes));
            cachedKeyPair = new KeyPair(pubKey, privKey);
            return cachedKeyPair;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse RSA keys from config", e);
        }
    }

    public String getIssuer() {
        return issuer;
    }

    public int getExpiryDays() {
        return expiryDays;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public void setExpiryDays(int expiryDays) {
        this.expiryDays = expiryDays;
    }

    /**
     * Test helper - builds a valid JWT for the given speaker pool using this bean's cached key pair.
     * This guarantees the JWT can be verified by any component that uses this same JwtConfig bean.
     */
    public String buildJwtForTest(ch.batbern.events.domain.SpeakerPool speakerPool) {
        java.time.Instant now = java.time.Instant.now();
        return io.jsonwebtoken.Jwts.builder()
                .subject(speakerPool.getId().toString())
                .issuer(getIssuer())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(getExpiryDays(), java.time.temporal.ChronoUnit.DAYS)))
                .claim("email", speakerPool.getEmail())
                .claim("roles", java.util.List.of("SPEAKER"))
                .claim("speakerPoolId", speakerPool.getId().toString())
                .signWith(getKeyPair().getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();
    }
}
