package ch.batbern.shared.test.security;

import ch.batbern.shared.security.BatbernJwtDecoder;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;

/**
 * TEST-ONLY tokens for the shared {@code BatbernJwtDecoder} (agentic interaction, story 0.1): one
 * source of agent and Watch tokens for every service's tests.
 *
 * <p>The agent key pair is generated once per JVM at runtime (RSA-2048); no private key is
 * committed anywhere. {@link #agentPublicKeyPem()} is what a test context registers as
 * {@code agent.jwt.public-key} ({@code AbstractIntegrationTest} does it for every IT), so a token
 * from {@link #agentToken} verifies there and nowhere else.
 */
public final class TestTokens {

    /** Lifetime of a default agent token: the maximum the decoder accepts. */
    public static final long AGENT_TOKEN_LIFETIME_SECONDS = 300;

    private static final KeyPair AGENT_KEYS = newRsaKeyPair();

    private TestTokens() {
    }

    /** A fresh RSA-2048 key pair, e.g. a key the decoder does not know. */
    public static KeyPair newRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    /** The agent key pair of this JVM. */
    public static KeyPair agentKeyPair() {
        return AGENT_KEYS;
    }

    /** The agent public key as PEM ({@code BEGIN PUBLIC KEY}, X.509 SubjectPublicKeyInfo). */
    public static String agentPublicKeyPem() {
        return toPem(AGENT_KEYS);
    }

    /** {@code keyPair}'s public key as PEM with header, footer and 64-character lines. */
    public static String toPem(KeyPair keyPair) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }

    /** A valid agent token signed with the agent key of this JVM. */
    public static String agentToken(String sub, String username, String run, String principal) {
        return agent(sub, username, run, principal).build();
    }

    /** A builder for an agent token that starts valid and can be broken on purpose. */
    public static AgentTokenBuilder agent(String sub, String username, String run, String principal) {
        return new AgentTokenBuilder(sub, username, run, principal);
    }

    /** A Watch token (HS256 with {@code secret}), valid for 5 minutes. */
    public static String watchToken(String secret, String subject, String role) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(BatbernJwtDecoder.WATCH_ISSUER)
                .subject(subject)
                .claim("role", role)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        return sign(new JWSHeader(JWSAlgorithm.HS256), claims, macSigner(secret.getBytes(StandardCharsets.UTF_8)));
    }

    /** Builds an agent token; every method returns the builder. */
    public static final class AgentTokenBuilder {

        private final JWTClaimsSet.Builder claims;
        private PrivateKey signingKey = AGENT_KEYS.getPrivate();
        private byte[] hmacSecret;

        private AgentTokenBuilder(String sub, String username, String run, String principal) {
            Instant now = Instant.now();
            this.claims = new JWTClaimsSet.Builder()
                    .issuer(BatbernJwtDecoder.AGENT_ISSUER)
                    .subject(sub)
                    .claim("custom:username", username)
                    .claim("act", Map.of("run", run, "principal", principal))
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(AGENT_TOKEN_LIFETIME_SECONDS)));
        }

        /** Changes the claims after the defaults; {@code b.claim(name, null)} removes one. */
        public AgentTokenBuilder claims(Consumer<JWTClaimsSet.Builder> customizer) {
            customizer.accept(claims);
            return this;
        }

        /** Signs with another RSA key (RS256). */
        public AgentTokenBuilder signedWith(KeyPair keyPair) {
            this.signingKey = keyPair.getPrivate();
            this.hmacSecret = null;
            return this;
        }

        /** Signs HS256 with {@code secret}, e.g. the public key bytes (key confusion). */
        public AgentTokenBuilder signedWithHmac(byte[] secret) {
            this.hmacSecret = secret.clone();
            return this;
        }

        /** The serialized token. */
        public String build() {
            if (hmacSecret != null) {
                return sign(new JWSHeader(JWSAlgorithm.HS256), claims.build(), macSigner(hmacSecret));
            }
            return sign(new JWSHeader(JWSAlgorithm.RS256), claims.build(), new RSASSASigner(signingKey));
        }
    }

    private static JWSSigner macSigner(byte[] secret) {
        try {
            return new MACSigner(secret);
        } catch (JOSEException e) {
            throw new IllegalArgumentException("HMAC secret too short", e);
        }
    }

    private static String sign(JWSHeader header, JWTClaimsSet claims, JWSSigner signer) {
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign test token", e);
        }
        return jwt.serialize();
    }
}
