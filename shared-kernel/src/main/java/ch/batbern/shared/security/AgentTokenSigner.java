package ch.batbern.shared.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The one place the claim set of an agent token is defined (agentic interaction, story 0.6). CUMS
 * signs with it ({@code POST /internal/agent-tokens}, key {@value #AGENT_PRIVATE_KEY_PROPERTY}), and
 * the test fixture {@code TestTokens.agentToken} signs with it too, so every service's agent-token
 * tests run on exactly what CUMS mints.
 *
 * <p>Header {@code alg=RS256}, {@code typ=JWT}. Claims, nothing else: {@code iss} =
 * {@value BatbernJwtDecoder#AGENT_ISSUER}, {@code sub}, {@code custom:username},
 * {@code act = {run, principal}}, {@code iat} = now (whole seconds), {@code exp} = the earlier of
 * {@code iat + }{@link AgentTokenValidator#MAX_LIFETIME} and {@code notAfter} (whole seconds, never
 * later than {@code notAfter}). No role claim: a verifier looks the roles up by {@code sub}.
 *
 * <p>Holding this class grants nothing: verifiers hold only the public key, and only CUMS has the
 * private one.
 */
public final class AgentTokenSigner {

    /** RSA private key (PKCS#8) that signs agent tokens; CUMS only. */
    public static final String AGENT_PRIVATE_KEY_PROPERTY = "agent.jwt.private-key";

    private static final String NOT_A_KEY =
            AGENT_PRIVATE_KEY_PROPERTY + " is not an RSA private key (PKCS#8 PEM or base64 DER, at least 2048 bits)";

    private final RSASSASigner signer;
    private final Clock clock;

    /** A signer with {@code privateKey} on {@code clock}. */
    public AgentTokenSigner(RSAPrivateKey privateKey, Clock clock) {
        if (privateKey == null || clock == null) {
            throw new IllegalArgumentException("privateKey and clock are required");
        }
        this.signer = new RSASSASigner(privateKey);
        this.clock = clock;
    }

    /**
     * The signer for the configured key value: empty when it is unset, blank or the deploy placeholder
     * {@value BatbernJwtDecoder#AGENT_PUBLIC_KEY_OFF} (trimmed, any case), as the decoder treats the
     * public key.
     *
     * @throws IllegalStateException when the value is set but is not an RSA private key of at least
     *                               2048 bits
     */
    public static Optional<AgentTokenSigner> fromKey(String value, Clock clock) {
        if (value == null || value.isBlank()
                || BatbernJwtDecoder.AGENT_PUBLIC_KEY_OFF.equalsIgnoreCase(value.trim())) {
            return Optional.empty();
        }
        RSAPrivateKey key = parsePrivateKey(value);
        try {
            return Optional.of(new AgentTokenSigner(key, clock));
        } catch (IllegalArgumentException e) {
            // Nimbus refuses an RSA key under 2048 bits.
            throw new IllegalStateException(NOT_A_KEY, e);
        }
    }

    /**
     * Parses an RSA private key given as PKCS#8 PEM (header, footer, newlines or {@code \n}) or bare
     * base64 DER. The message never contains the value.
     *
     * @throws IllegalStateException when it is not one
     */
    public static RSAPrivateKey parsePrivateKey(String value) {
        if (value == null) {
            throw new IllegalStateException(NOT_A_KEY);
        }
        String base64 = value
                .replace("\\n", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException(NOT_A_KEY, e);
        }
    }

    /**
     * Signs an agent token.
     *
     * @param sub       the principal's Cognito {@code sub}
     * @param username  the principal's username ({@code custom:username})
     * @param run       the run id ({@code act.run})
     * @param principal the principal kind ({@code act.principal})
     * @param notAfter  the token never outlives this instant (the grant's end)
     * @return the serialized token with its {@code iat} and {@code exp}
     * @throws IllegalArgumentException for a blank value, or a {@code notAfter} that leaves no whole
     *                                  second from now
     */
    public SignedAgentToken sign(String sub, String username, String run, String principal, Instant notAfter) {
        requireText(sub, "sub");
        requireText(username, "username");
        requireText(run, "run");
        requireText(principal, "principal");
        if (notAfter == null) {
            throw new IllegalArgumentException("notAfter is required");
        }
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant capped = issuedAt.plus(AgentTokenValidator.MAX_LIFETIME);
        Instant end = notAfter.truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = end.isBefore(capped) ? end : capped;
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("notAfter is not after now");
        }

        Map<String, Object> act = new LinkedHashMap<>();
        act.put("run", run);
        act.put("principal", principal);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(BatbernJwtDecoder.AGENT_ISSUER)
                .subject(sub)
                .claim("custom:username", username)
                .claim("act", act)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(),
                claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign agent token", e);
        }
        return new SignedAgentToken(jwt.serialize(), issuedAt, expiresAt);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    /**
     * A signed agent token.
     *
     * @param token     the serialized JWT; a bearer credential, never log it
     * @param issuedAt  its {@code iat}
     * @param expiresAt its {@code exp}
     */
    public record SignedAgentToken(String token, Instant issuedAt, Instant expiresAt) {

        /** Never prints the token. */
        @Override
        public String toString() {
            return "SignedAgentToken[issuedAt=" + issuedAt + ", expiresAt=" + expiresAt + "]";
        }
    }
}
