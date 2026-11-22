package ch.batbern.shared.security;

import ch.batbern.shared.test.security.TestTokens;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shared decoder (agentic interaction 0.1): routing by the unverified {@code iss}, verification
 * by the chosen verifier, refusal of a kind the service does not accept, and the agent token rules.
 * No network: "Cognito" is a local RSA key, as the JWK set would be.
 */
@DisplayName("BatbernJwtDecoder")
class BatbernJwtDecoderTest {

    private static final String COGNITO_ISSUER =
            "https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_TestPool";
    private static final String WATCH_SECRET = "decoder-test-watch-secret-at-least-32-bytes";
    private static final KeyPair COGNITO_KEYS = TestTokens.newRsaKeyPair();

    /** A verifier that records each call before it decodes with the local "Cognito" key. */
    private static final class RecordingCognito implements JwtDecoder {
        private final List<String> calls = new ArrayList<>();
        private final JwtDecoder delegate =
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) COGNITO_KEYS.getPublic()).build();

        @Override
        public Jwt decode(String token) {
            calls.add(token);
            return delegate.decode(token);
        }
    }

    private static BatbernJwtDecoder allKinds(RecordingCognito cognito) {
        return BatbernJwtDecoder.builder()
                .cognito(cognito)
                .watch(WATCH_SECRET)
                .agent((RSAPublicKey) TestTokens.agentKeyPair().getPublic())
                .build();
    }

    private static String cognitoToken(Consumer<JWTClaimsSet.Builder> customizer) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(COGNITO_ISSUER)
                .subject("8f2c-sub")
                .claim("custom:role", "ORGANIZER")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)));
        customizer.accept(claims);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(COGNITO_KEYS.getPrivate()));
        return jwt.serialize();
    }

    private static String agentToken() {
        return TestTokens.agentToken("agent-sub-1", "agent.planner", "run-42", "organizer");
    }

    @Nested
    @DisplayName("routing")
    class Routing {

        @Test
        void should_decodeWithCognitoVerifier_when_issIsCognitoPool() throws Exception {
            RecordingCognito cognito = new RecordingCognito();
            Jwt jwt = allKinds(cognito).decode(cognitoToken(c -> { }));
            assertThat(jwt.getSubject()).isEqualTo("8f2c-sub");
            assertThat(cognito.calls).hasSize(1);
        }

        @Test
        void should_refuse_when_cognitoTokenSignedWithOtherKey() throws Exception {
            KeyPair other = TestTokens.newRsaKeyPair();
            JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(COGNITO_ISSUER).subject("x")
                    .expirationTime(Date.from(Instant.now().plusSeconds(60))).build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(other.getPrivate()));
            assertThatThrownBy(() -> allKinds(new RecordingCognito()).decode(jwt.serialize()))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        void should_decodeWatchToken_when_signedWithWatchSecret() {
            RecordingCognito cognito = new RecordingCognito();
            Jwt jwt = allKinds(cognito).decode(TestTokens.watchToken(WATCH_SECRET, "marco.organizer", "ORGANIZER"));
            assertThat(jwt.getSubject()).isEqualTo("marco.organizer");
            assertThat(jwt.getClaimAsString("role")).isEqualTo("ORGANIZER");
            assertThat(cognito.calls).isEmpty();
        }

        @Test
        void should_refuseWatchToken_when_secretIsWrong() {
            String token = TestTokens.watchToken("another-secret-that-is-also-32-bytes-long", "m", "ORGANIZER");
            assertThatThrownBy(() -> allKinds(new RecordingCognito()).decode(token))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        void should_routeWatchByParsedIss_when_payloadSerialisedWithWhitespace() throws Exception {
            // The old String.contains sniff mis-routed such a token to Cognito.
            JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("batbern-watch").subject("m")
                    .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(60))).build();
            String header = b64("{\"alg\":\"HS256\"}");
            String payload = b64(claims.toString().replace(":", " : "));
            com.nimbusds.jose.crypto.MACSigner signer =
                    new com.nimbusds.jose.crypto.MACSigner(WATCH_SECRET.getBytes());
            String signature = signer.sign(new JWSHeader(JWSAlgorithm.HS256),
                    (header + "." + payload).getBytes()).toString();
            RecordingCognito cognito = new RecordingCognito();

            Jwt jwt = allKinds(cognito).decode(header + "." + payload + "." + signature);

            assertThat(jwt.getSubject()).isEqualTo("m");
            assertThat(cognito.calls).isEmpty();
        }

        @Test
        void should_refuseWatchWithoutAskingCognito_when_watchKindOff() {
            RecordingCognito cognito = new RecordingCognito();
            BatbernJwtDecoder pcsLike = BatbernJwtDecoder.builder()
                    .cognito(cognito)
                    .agent((RSAPublicKey) TestTokens.agentKeyPair().getPublic())
                    .build();
            String watch = TestTokens.watchToken(WATCH_SECRET, "m", "ORGANIZER");

            assertThatThrownBy(() -> pcsLike.decode(watch)).isInstanceOf(BadJwtException.class);
            assertThat(cognito.calls).isEmpty();
            // Positive control: the same decoder still accepts the agent token.
            assertThat(pcsLike.decode(agentToken()).getSubject()).isEqualTo("agent-sub-1");
        }

        @ParameterizedTest
        @ValueSource(strings = {"not-a-jwt", "a.b.c", "", "eyJhbGciOiJIUzI1NiJ9.bm90LWpzb24.c2ln"})
        void should_throwBadJwt_when_tokenMalformed(String token) {
            RecordingCognito cognito = new RecordingCognito();
            assertThatThrownBy(() -> allKinds(cognito).decode(token)).isInstanceOf(BadJwtException.class);
            assertThat(cognito.calls).isEmpty();
        }
    }

    @Nested
    @DisplayName("agent tokens")
    class AgentTokens {

        @Test
        void should_decodeAgentToken_when_validAndSignedWithAgentKey() {
            RecordingCognito cognito = new RecordingCognito();
            Jwt jwt = allKinds(cognito).decode(agentToken());

            assertThat(jwt.getSubject()).isEqualTo("agent-sub-1");
            assertThat(jwt.getClaimAsString("custom:username")).isEqualTo("agent.planner");
            assertThat(jwt.getClaimAsMap("act")).containsEntry("run", "run-42")
                    .containsEntry("principal", "organizer");
            assertThat(cognito.calls).isEmpty();
        }

        @Test
        void should_refuseAgentToken_when_agentKindOff() {
            BatbernJwtDecoder noAgent = BatbernJwtDecoder.builder()
                    .cognito(new RecordingCognito()).watch(WATCH_SECRET).build();
            assertThatThrownBy(() -> noAgent.decode(agentToken())).isInstanceOf(BadJwtException.class);
        }

        @Test
        void should_refuse_when_signedWithOtherKey() {
            String token = TestTokens.agent("s", "u", "r", "p").signedWith(TestTokens.newRsaKeyPair()).build();
            assertRefused(token);
        }

        @Test
        void should_refuse_when_hs256SignedWithPublicKeyBytes() {
            // Key confusion: the RSA public key used as an HMAC secret.
            String token = TestTokens.agent("s", "u", "r", "p")
                    .signedWithHmac(TestTokens.agentKeyPair().getPublic().getEncoded()).build();
            assertRefused(token);
        }

        @Test
        void should_refuse_when_expired() {
            Instant past = Instant.now().minusSeconds(3600);
            assertRefused(TestTokens.agent("s", "u", "r", "p").claims(c -> c
                    .issueTime(Date.from(past)).expirationTime(Date.from(past.plusSeconds(200)))).build());
        }

        @Test
        void should_refuse_when_lifetimeLongerThan300Seconds() {
            Instant now = Instant.now();
            assertRefused(TestTokens.agent("s", "u", "r", "p").claims(c -> c
                    .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(301)))).build());
        }

        @Test
        void should_refuse_when_iatInTheFuture() {
            // A year ahead with a 300 s window: without the iat-vs-now check it would pass for a year.
            Instant future = Instant.now().plus(java.time.Duration.ofDays(365));
            assertRefused(TestTokens.agent("s", "u", "r", "p").claims(c -> c
                    .issueTime(Date.from(future)).expirationTime(Date.from(future.plusSeconds(300)))).build());
        }

        @Test
        void should_refuse_when_iatAfterExp() {
            Instant now = Instant.now();
            assertRefused(TestTokens.agent("s", "u", "r", "p").claims(c -> c
                    .issueTime(Date.from(now.plusSeconds(30))).expirationTime(Date.from(now.plusSeconds(20))))
                    .build());
        }

        @Test
        void should_accept_when_lifetimeExactly300Seconds() {
            Instant now = Instant.now();
            String token = TestTokens.agent("s", "u", "r", "p").claims(c -> c
                    .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))).build();
            assertThat(allKinds(new RecordingCognito()).decode(token).getSubject()).isEqualTo("s");
        }

        @ParameterizedTest
        @ValueSource(strings = {"iat", "exp", "sub", "custom:username", "act"})
        void should_refuse_when_requiredClaimMissing(String claim) {
            assertRefused(TestTokens.agent("s", "u", "r", "p").claims(c -> c.claim(claim, null)).build());
        }

        @Test
        void should_refuse_when_actRunMissing() {
            assertRefused(TestTokens.agent("s", "u", "r", "p")
                    .claims(c -> c.claim("act", Map.of("principal", "p"))).build());
        }

        @Test
        void should_refuse_when_actPrincipalMissing() {
            assertRefused(TestTokens.agent("s", "u", "r", "p")
                    .claims(c -> c.claim("act", Map.of("run", "r"))).build());
        }

        @Test
        void should_refuse_when_actIsNotAnObject() {
            assertRefused(TestTokens.agent("s", "u", "r", "p").claims(c -> c.claim("act", "run-42")).build());
        }

        @ParameterizedTest
        @ValueSource(strings = {"custom:role", "role"})
        void should_refuse_when_tokenCarriesRoleClaim(String claim) {
            assertRefused(TestTokens.agent("s", "u", "r", "p").claims(c -> c.claim(claim, "ORGANIZER")).build());
        }

        private void assertRefused(String token) {
            RecordingCognito cognito = new RecordingCognito();
            assertThatThrownBy(() -> allKinds(cognito).decode(token)).isInstanceOf(JwtException.class);
            assertThat(cognito.calls).isEmpty();
        }
    }

    @Nested
    @DisplayName("configuration")
    class Configuration {

        private MockEnvironment env() {
            return new MockEnvironment()
                    .withProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                            "https://jwks.example.invalid/keys")
                    .withProperty("watch.jwt.secret", WATCH_SECRET);
        }

        @Test
        void should_enableServiceDefaultKinds_when_noOverride() {
            BatbernJwtDecoder decoder = BatbernJwtDecoder.fromEnvironment(
                    env().withProperty("agent.jwt.public-key", TestTokens.agentPublicKeyPem()),
                    EnumSet.allOf(TokenKind.class));
            assertThat(decoder.enabledKinds()).containsExactlyInAnyOrder(TokenKind.values());
            assertThat(decoder.decode(agentToken()).getSubject()).isEqualTo("agent-sub-1");
            assertThat(decoder.decode(TestTokens.watchToken(WATCH_SECRET, "m", "ORGANIZER")).getSubject())
                    .isEqualTo("m");
        }

        @Test
        void should_followOverride_when_tokenKindsConfigured() {
            BatbernJwtDecoder decoder = BatbernJwtDecoder.fromEnvironment(
                    env().withProperty("agent.jwt.public-key", TestTokens.agentPublicKeyPem())
                            .withProperty("batbern.security.token-kinds", "cognito, agent"),
                    EnumSet.allOf(TokenKind.class));
            assertThat(decoder.enabledKinds()).containsExactlyInAnyOrder(TokenKind.COGNITO, TokenKind.AGENT);
            assertThatThrownBy(() -> decoder.decode(TestTokens.watchToken(WATCH_SECRET, "m", "ORGANIZER")))
                    .isInstanceOf(BadJwtException.class);
        }

        @Test
        void should_turnAgentOff_when_publicKeyMissingOrBlank() {
            for (MockEnvironment env : List.of(env(), env().withProperty("agent.jwt.public-key", "  "))) {
                BatbernJwtDecoder decoder = BatbernJwtDecoder.fromEnvironment(env, EnumSet.allOf(TokenKind.class));
                assertThat(decoder.enabledKinds()).doesNotContain(TokenKind.AGENT);
                assertThatThrownBy(() -> decoder.decode(agentToken())).isInstanceOf(BadJwtException.class);
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"none", " NONE ", "None"})
        void should_turnAgentOff_when_publicKeyIsDeployPlaceholder(String placeholder) {
            // positive control: the same setup with a real key accepts the agent token
            BatbernJwtDecoder on = BatbernJwtDecoder.fromEnvironment(
                    env().withProperty("agent.jwt.public-key", TestTokens.agentPublicKeyPem()),
                    EnumSet.allOf(TokenKind.class));
            assertThat(on.decode(agentToken()).getSubject()).isEqualTo("agent-sub-1");

            BatbernJwtDecoder off = BatbernJwtDecoder.fromEnvironment(
                    env().withProperty("agent.jwt.public-key", placeholder), EnumSet.allOf(TokenKind.class));
            assertThat(off.enabledKinds()).doesNotContain(TokenKind.AGENT).contains(TokenKind.COGNITO);
            assertThatThrownBy(() -> off.decode(agentToken())).isInstanceOf(BadJwtException.class);
        }

        @Test
        void should_readPublicKey_when_givenAsAgentJwtPublicKeyEnvironmentVariable() {
            // The CDK injects the key as the ECS container secret AGENT_JWT_PUBLIC_KEY; no yml maps
            // it, so the property must resolve from the OS environment by itself.
            StandardEnvironment environment = new StandardEnvironment();
            environment.getPropertySources().replace(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    new SystemEnvironmentPropertySource(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                            Map.of("AGENT_JWT_PUBLIC_KEY", TestTokens.agentPublicKeyPem())));
            BatbernJwtDecoder decoder = BatbernJwtDecoder.fromEnvironment(environment, EnumSet.of(TokenKind.AGENT));
            assertThat(decoder.decode(agentToken()).getSubject()).isEqualTo("agent-sub-1");
        }

        @Test
        void should_acceptBareBase64Key_when_noPemHeader() {
            String bare = Base64.getEncoder().encodeToString(TestTokens.agentKeyPair().getPublic().getEncoded());
            BatbernJwtDecoder decoder = BatbernJwtDecoder.fromEnvironment(
                    env().withProperty("agent.jwt.public-key", bare), EnumSet.of(TokenKind.AGENT));
            assertThat(decoder.decode(agentToken()).getSubject()).isEqualTo("agent-sub-1");
        }

        @Test
        void should_acceptPemWithEscapedNewlines_when_keyComesFromEnvironmentVariable() {
            String escaped = TestTokens.agentPublicKeyPem().replace("\n", "\\n");
            BatbernJwtDecoder decoder = BatbernJwtDecoder.fromEnvironment(
                    env().withProperty("agent.jwt.public-key", escaped), EnumSet.of(TokenKind.AGENT));
            assertThat(decoder.decode(agentToken()).getSubject()).isEqualTo("agent-sub-1");
        }

        @Test
        void should_failAtStartup_when_publicKeyUnparseable() {
            MockEnvironment env = env().withProperty("agent.jwt.public-key", "-----BEGIN PUBLIC KEY-----\nnope\n");
            assertThatThrownBy(() -> BatbernJwtDecoder.fromEnvironment(env, EnumSet.of(TokenKind.AGENT)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("agent.jwt.public-key");
        }

        @Test
        void should_failAtStartup_when_cognitoOnButNoJwkSetOrIssuer() {
            MockEnvironment env = new MockEnvironment();
            assertThatThrownBy(() -> BatbernJwtDecoder.fromEnvironment(env, EnumSet.of(TokenKind.COGNITO)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void should_failAtStartup_when_unknownTokenKindConfigured() {
            MockEnvironment env = env().withProperty("batbern.security.token-kinds", "cognito,saml");
            assertThatThrownBy(() -> BatbernJwtDecoder.fromEnvironment(env, EnumSet.allOf(TokenKind.class)))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }
}
