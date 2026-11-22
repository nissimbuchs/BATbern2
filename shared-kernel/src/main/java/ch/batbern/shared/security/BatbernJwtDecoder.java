package ch.batbern.shared.security;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The one {@link JwtDecoder} of every BATbern service (gateway, CUMS, EMS, PCS): it accepts Cognito,
 * Watch and agent tokens, each kind only where the service enables it.
 *
 * <p><b>Routing.</b> The unverified {@code iss} (parsed with {@link JWTParser}, never sniffed as a
 * string) only picks the verifier; the signature is always checked by that verifier, so a forged
 * {@code iss} only selects a verifier that then fails:
 * <ul>
 *   <li>{@code batbern-watch} → {@link TokenKind#WATCH}: HS256 with {@code watch.jwt.secret}
 *       (default: the dev secret, as before), Spring's default validators;</li>
 *   <li>{@code batbern-agent} → {@link TokenKind#AGENT}: RS256 only, with the RSA key in
 *       {@code agent.jwt.public-key} (PEM or bare base64 X.509 DER), issuer {@code batbern-agent},
 *       default timestamp checks plus {@link AgentTokenValidator};</li>
 *   <li>anything else → {@link TokenKind#COGNITO}: the JWK set of
 *       {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}, or, without it, discovery from
 *       {@code ...jwt.issuer-uri}; Spring's default validators.</li>
 * </ul>
 * A token of a kind the service does not accept is refused with {@link BadJwtException} and never
 * reaches another verifier (PCS refuses Watch tokens outright). A token that is not a parseable JWT
 * is refused the same way.
 *
 * <p><b>Configuration.</b> {@link #fromEnvironment} enables the service's default kinds, or the list
 * in {@code batbern.security.token-kinds} (comma-separated or a YAML list, e.g. {@code cognito,agent})
 * when set. {@code AGENT} also needs {@code agent.jwt.public-key}; without it, blank, or the deploy
 * placeholder {@value #AGENT_PUBLIC_KEY_OFF} (trimmed, any case: what the CDK-created SSM parameter
 * holds until the owner writes the real key) the kind stays off. An unparseable key, an unknown
 * kind, or {@code COGNITO} without a JWK set or issuer fails at startup.
 */
public final class BatbernJwtDecoder implements JwtDecoder {

    /** {@code iss} of a Watch pairing token. */
    public static final String WATCH_ISSUER = "batbern-watch";
    /** {@code iss} of an agent token. */
    public static final String AGENT_ISSUER = "batbern-agent";

    /** Overrides the service's default kinds. */
    public static final String TOKEN_KINDS_PROPERTY = "batbern.security.token-kinds";
    /** RSA public key that verifies agent tokens; the agent kind is off without it. */
    public static final String AGENT_PUBLIC_KEY_PROPERTY = "agent.jwt.public-key";
    /**
     * Placeholder value of {@value #AGENT_PUBLIC_KEY_PROPERTY} that means "no key yet": the CDK
     * SecretsStack creates the parameter with it, so a deploy before the key exists starts safely.
     */
    public static final String AGENT_PUBLIC_KEY_OFF = "none";
    /** HMAC secret of Watch tokens. */
    public static final String WATCH_SECRET_PROPERTY = "watch.jwt.secret";
    /** Cognito JWK set, checked first. */
    public static final String JWK_SET_URI_PROPERTY = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri";
    /** Cognito issuer, used when no JWK set URI is configured. */
    public static final String ISSUER_URI_PROPERTY = "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    static final String WATCH_DEV_SECRET = "batbern-watch-dev-secret-key-min-32-chars";

    private static final Logger LOG = LoggerFactory.getLogger(BatbernJwtDecoder.class);

    private final Map<TokenKind, JwtDecoder> verifiers;

    private BatbernJwtDecoder(Map<TokenKind, JwtDecoder> verifiers) {
        this.verifiers = Collections.unmodifiableMap(new EnumMap<>(verifiers));
    }

    /**
     * The decoder for a service: {@code serviceDefault} kinds unless {@value #TOKEN_KINDS_PROPERTY}
     * says otherwise; see the class comment for the properties each kind reads.
     */
    public static BatbernJwtDecoder fromEnvironment(Environment environment, Set<TokenKind> serviceDefault) {
        Set<TokenKind> configured = Binder.get(environment)
                .bind(TOKEN_KINDS_PROPERTY, Bindable.setOf(TokenKind.class))
                .orElse(Set.of());
        Set<TokenKind> kinds = configured.isEmpty() ? serviceDefault : configured;

        Builder builder = builder();
        if (kinds.contains(TokenKind.COGNITO)) {
            builder.cognito(cognitoVerifier(environment));
        }
        if (kinds.contains(TokenKind.WATCH)) {
            builder.watch(environment.getProperty(WATCH_SECRET_PROPERTY, WATCH_DEV_SECRET));
        }
        if (kinds.contains(TokenKind.AGENT)) {
            String key = environment.getProperty(AGENT_PUBLIC_KEY_PROPERTY);
            if (key == null || key.isBlank()) {
                LOG.info("Agent tokens are off: {} is not set", AGENT_PUBLIC_KEY_PROPERTY);
            } else if (AGENT_PUBLIC_KEY_OFF.equalsIgnoreCase(key.trim())) {
                LOG.info("Agent tokens are off: {} holds the placeholder {}",
                        AGENT_PUBLIC_KEY_PROPERTY, AGENT_PUBLIC_KEY_OFF);
            } else {
                builder.agent(parsePublicKey(key));
            }
        }
        BatbernJwtDecoder decoder = builder.build();
        LOG.info("JWT decoder accepts token kinds {}", decoder.enabledKinds());
        return decoder;
    }

    /** The kinds this decoder accepts. */
    public Set<TokenKind> enabledKinds() {
        return verifiers.isEmpty() ? EnumSet.noneOf(TokenKind.class) : EnumSet.copyOf(verifiers.keySet());
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        TokenKind kind = kindOf(peekIssuer(token));
        JwtDecoder verifier = verifiers.get(kind);
        if (verifier == null) {
            throw new BadJwtException("Token kind " + kind + " is not accepted by this service");
        }
        return verifier.decode(token);
    }

    private static String peekIssuer(String token) {
        try {
            JWT jwt = JWTParser.parse(token);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims == null) {
                throw new BadJwtException("Token has no readable claims");
            }
            return claims.getIssuer();
        } catch (java.text.ParseException | RuntimeException e) {
            if (e instanceof BadJwtException bad) {
                throw bad;
            }
            throw new BadJwtException("Malformed token", e);
        }
    }

    private static TokenKind kindOf(String issuer) {
        if (WATCH_ISSUER.equals(issuer)) {
            return TokenKind.WATCH;
        }
        if (AGENT_ISSUER.equals(issuer)) {
            return TokenKind.AGENT;
        }
        return TokenKind.COGNITO;
    }

    private static JwtDecoder cognitoVerifier(Environment environment) {
        String jwkSetUri = environment.getProperty(JWK_SET_URI_PROPERTY);
        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        }
        String issuerUri = environment.getProperty(ISSUER_URI_PROPERTY);
        if (issuerUri != null && !issuerUri.isBlank()) {
            return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        }
        throw new IllegalStateException(
                "Cognito tokens are on: configure " + JWK_SET_URI_PROPERTY + " or " + ISSUER_URI_PROPERTY);
    }

    /** Parses an RSA public key given as PEM (header, footer, newlines or {@code \n}) or bare base64 DER. */
    static RSAPublicKey parsePublicKey(String value) {
        String base64 = value
                .replace("\\n", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException(
                    AGENT_PUBLIC_KEY_PROPERTY + " is not an RSA public key (PEM or base64 DER)", e);
        }
    }

    /** Builds a decoder from explicit verifiers; tests inject a local "Cognito" here. */
    static Builder builder() {
        return new Builder();
    }

    /** Collects the verifier of each enabled kind. */
    static final class Builder {
        private final Map<TokenKind, JwtDecoder> verifiers = new EnumMap<>(TokenKind.class);

        Builder cognito(JwtDecoder cognitoVerifier) {
            verifiers.put(TokenKind.COGNITO, cognitoVerifier);
            return this;
        }

        Builder watch(String secret) {
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            verifiers.put(TokenKind.WATCH, NimbusJwtDecoder.withSecretKey(key).build());
            return this;
        }

        Builder agent(RSAPublicKey publicKey) {
            NimbusJwtDecoder agent = NimbusJwtDecoder.withPublicKey(publicKey)
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            // Spring's default claim converter invents iat = exp - 1 s when iat is missing; an agent
            // token must carry its own iat, so the invented one is dropped and the validator refuses.
            MappedJwtClaimSetConverter defaults = MappedJwtClaimSetConverter.withDefaults(Map.of());
            agent.setClaimSetConverter(raw -> {
                Map<String, Object> claims = defaults.convert(raw);
                if (raw.get(JwtClaimNames.IAT) == null && claims.containsKey(JwtClaimNames.IAT)) {
                    claims = new HashMap<>(claims);
                    claims.remove(JwtClaimNames.IAT);
                }
                return claims;
            });
            agent.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(AGENT_ISSUER), new AgentTokenValidator()));
            verifiers.put(TokenKind.AGENT, agent);
            return this;
        }

        BatbernJwtDecoder build() {
            return new BatbernJwtDecoder(verifiers);
        }
    }
}
