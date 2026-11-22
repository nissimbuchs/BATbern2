package ch.batbern.shared.security;

import ch.batbern.shared.test.security.TestTokens;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The production signer of agent tokens (agentic interaction, story 0.6): the claim set CUMS mints is
 * fixed here, and the shared decoder accepts it. The key pair is generated at runtime.
 */
@DisplayName("AgentTokenSigner")
class AgentTokenSignerTest {

    private static final KeyPair KEYS = TestTokens.newRsaKeyPair();
    /** Now, with a sub-second part, so second truncation is visible. */
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusMillis(250);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant IAT = NOW.truncatedTo(ChronoUnit.SECONDS);

    private static AgentTokenSigner signer() {
        return new AgentTokenSigner((RSAPrivateKey) KEYS.getPrivate(), CLOCK);
    }

    private static SignedJWT parseVerified(String token) throws Exception {
        SignedJWT jwt = SignedJWT.parse(token);
        assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) KEYS.getPublic()))).isTrue();
        return jwt;
    }

    private static String pkcs8Pem(KeyPair keys) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keys.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    @Nested
    @DisplayName("signing")
    class Signing {

        @Test
        @DisplayName("the token decodes with the shared decoder built for the matching public key")
        void should_decodeWithSharedDecoder_when_signed() {
            AgentTokenSigner.SignedAgentToken signed = signer().sign(
                    "a0000000-0000-4000-8000-000000000601", "hongan.meier", "run-601", "requester",
                    NOW.plusSeconds(3600));

            BatbernJwtDecoder decoder = BatbernJwtDecoder.builder()
                    .agent((RSAPublicKey) KEYS.getPublic())
                    .build();
            var jwt = decoder.decode(signed.token());

            assertThat(jwt.getSubject()).isEqualTo("a0000000-0000-4000-8000-000000000601");
            assertThat(jwt.getClaimAsString("custom:username")).isEqualTo("hongan.meier");
            assertThat(Actors.forJwt(jwt)).isEqualTo("agent:run-601 for hongan.meier");
        }

        @Test
        @DisplayName("exact header and claim set: RS256, JWT, iss, sub, custom:username, act, iat, exp")
        void should_carryExactClaims_when_signed() throws Exception {
            AgentTokenSigner.SignedAgentToken signed = signer().sign(
                    "sub-1", "sean.oneill", "run-1", "organizer_delegate", NOW.plusSeconds(3600));

            SignedJWT jwt = parseVerified(signed.token());
            assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
            assertThat(jwt.getHeader().getType()).isEqualTo(JOSEObjectType.JWT);
            assertThat(jwt.getHeader().toJSONObject().keySet()).containsExactlyInAnyOrder("alg", "typ");

            Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();
            assertThat(claims.keySet())
                    .isEqualTo(Set.of("iss", "sub", "custom:username", "act", "iat", "exp"));
            assertThat(claims.get("iss")).isEqualTo(BatbernJwtDecoder.AGENT_ISSUER);
            assertThat(claims.get("sub")).isEqualTo("sub-1");
            assertThat(claims.get("custom:username")).isEqualTo("sean.oneill");
            assertThat(claims.get("act")).isEqualTo(Map.of("run", "run-1", "principal", "organizer_delegate"));
            assertThat(jwt.getJWTClaimsSet().getIssueTime().toInstant()).isEqualTo(IAT);
            assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant())
                    .isEqualTo(IAT.plus(AgentTokenValidator.MAX_LIFETIME));
            assertThat(signed.issuedAt()).isEqualTo(IAT);
            assertThat(signed.expiresAt()).isEqualTo(IAT.plus(AgentTokenValidator.MAX_LIFETIME));
        }

        @Test
        @DisplayName("exp is capped by notAfter (a grant that ends sooner than 300 s)")
        void should_capExpiry_when_notAfterIsSooner() throws Exception {
            Instant notAfter = NOW.plusSeconds(90);

            AgentTokenSigner.SignedAgentToken signed = signer().sign("s", "u", "r", "requester", notAfter);

            Instant exp = parseVerified(signed.token()).getJWTClaimsSet().getExpirationTime().toInstant();
            assertThat(exp).isEqualTo(notAfter.truncatedTo(ChronoUnit.SECONDS)).isBefore(notAfter.plusMillis(1));
            assertThat(signed.expiresAt()).isEqualTo(exp);
        }

        @Test
        @DisplayName("positive control: a notAfter beyond 300 s does not stretch the lifetime")
        void should_keepMaxLifetime_when_notAfterIsLater() {
            AgentTokenSigner.SignedAgentToken signed = signer().sign("s", "u", "r", "requester", NOW.plusSeconds(301));

            assertThat(signed.expiresAt()).isEqualTo(IAT.plus(AgentTokenValidator.MAX_LIFETIME));
        }

        @ParameterizedTest
        @ValueSource(longs = {0, -1, -3600})
        @DisplayName("a notAfter not after now is refused")
        void should_refuse_when_notAfterNotAfterNow(long seconds) {
            assertThatThrownBy(() -> signer().sign("s", "u", "r", "requester", NOW.plusSeconds(seconds)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a notAfter within the current second (no whole second left) is refused")
        void should_refuse_when_noWholeSecondLeft() {
            Instant sameSecond = IAT.plusMillis(900);

            assertThatThrownBy(() -> signer().sign("s", "u", "r", "requester", sameSecond))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a blank claim value is refused, never signed")
        void should_refuse_when_claimBlank() {
            Instant later = NOW.plusSeconds(60);
            assertThatThrownBy(() -> signer().sign(" ", "u", "r", "p", later)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> signer().sign("s", null, "r", "p", later)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> signer().sign("s", "u", "", "p", later)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> signer().sign("s", "u", "r", null, later)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> signer().sign("s", "u", "r", "p", null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("key from the property")
    class KeyParsing {

        @Test
        @DisplayName("PKCS#8 PEM with header, footer and newlines parses to the same key")
        void should_parse_when_pem() {
            RSAPrivateKey parsed = AgentTokenSigner.parsePrivateKey(pkcs8Pem(KEYS));

            assertThat(parsed.getEncoded()).isEqualTo(KEYS.getPrivate().getEncoded());
        }

        @Test
        @DisplayName("PEM with literal \\n (as an environment variable may hold it) parses")
        void should_parse_when_pemWithEscapedNewlines() {
            String escaped = pkcs8Pem(KEYS).replace("\n", "\\n");

            assertThat(AgentTokenSigner.parsePrivateKey(escaped).getEncoded()).isEqualTo(KEYS.getPrivate().getEncoded());
        }

        @Test
        @DisplayName("bare base64 DER parses")
        void should_parse_when_bareDer() {
            String der = Base64.getEncoder().encodeToString(KEYS.getPrivate().getEncoded());

            assertThat(AgentTokenSigner.parsePrivateKey(der).getEncoded()).isEqualTo(KEYS.getPrivate().getEncoded());
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   ", "none", " NONE ", "None"})
        @DisplayName("unset, blank or the placeholder none: signing is off")
        void should_beOff_when_unsetBlankOrPlaceholder(String value) {
            assertThat(AgentTokenSigner.fromKey(value, CLOCK)).isEmpty();
        }

        @Test
        @DisplayName("positive control: a real key turns signing on")
        void should_beOn_when_keyGiven() {
            assertThat(AgentTokenSigner.fromKey(pkcs8Pem(KEYS), CLOCK)).isPresent();
        }

        @ParameterizedTest
        @ValueSource(strings = {"garbage", "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----", "bm90IGEga2V5"})
        @DisplayName("garbage fails with IllegalStateException, naming the property, never the value")
        void should_failStartup_when_garbage(String value) {
            assertThatThrownBy(() -> AgentTokenSigner.fromKey(value, CLOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(AgentTokenSigner.AGENT_PRIVATE_KEY_PROPERTY)
                    .hasMessageNotContaining(value);
        }

        @Test
        @DisplayName("parsePrivateKey(null) fails with IllegalStateException, naming the property")
        void should_failStartup_when_parseNull() {
            assertThatThrownBy(() -> AgentTokenSigner.parsePrivateKey(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(AgentTokenSigner.AGENT_PRIVATE_KEY_PROPERTY);
        }

        @Test
        @DisplayName("an RSA-1024 key fails with IllegalStateException, naming the property")
        void should_failStartup_when_keyTooShort() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024);
            String shortKey = pkcs8Pem(generator.generateKeyPair());

            assertThat(AgentTokenSigner.parsePrivateKey(shortKey)).isNotNull(); // it parses; signing refuses it
            assertThatThrownBy(() -> AgentTokenSigner.fromKey(shortKey, CLOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(AgentTokenSigner.AGENT_PRIVATE_KEY_PROPERTY);
        }

        @Test
        @DisplayName("a public key in the private-key property is refused")
        void should_failStartup_when_publicKeyGiven() {
            String publicPem = TestTokens.toPem(KEYS);

            assertThatThrownBy(() -> AgentTokenSigner.fromKey(publicPem, CLOCK))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
