package ch.batbern.gateway.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the PRODUCTION gateway filter chain ({@code defaultSecurityFilterChain},
 * {@code @Profile("!test")}). Every other gateway test runs under the {@code test} profile,
 * whose chain permits everything, so the rules that actually guard {@code api.batbern.ch}
 * had no coverage.
 *
 * <p>Only {@link SecurityConfig} and a catch-all probe controller are loaded: a request the
 * chain lets through reaches the probe (200), a blocked one is rejected with 401 before it.
 * The JWK URI is never contacted: anonymous requests carry no token, and the positive path uses a
 * Watch token, which the chain verifies locally with the HMAC secret.
 */
@SpringJUnitWebConfig(ProductionSecurityFilterChainTest.WebConfig.class)
@ActiveProfiles("staging")
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwks.example.invalid/keys",
    "watch.jwt.secret=" + ProductionSecurityFilterChainTest.WATCH_TEST_SECRET
})
@DisplayName("Production gateway filter chain")
class ProductionSecurityFilterChainTest {

    static final String WATCH_TEST_SECRET = "production-chain-test-secret-at-least-32-bytes";

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, ProbeController.class})
    static class WebConfig {
    }

    @RestController
    static class ProbeController {
        @RequestMapping("/**")
        String reached() {
            return "reached";
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/events/BATbern60/registrations",
        "/api/v1/events/BATbern60/distribution-list/speakers",
        "/api/v1/events/BATbern60/distribution-list/moderator",
        "/api/v1/events/BATbern60/distribution-list/participants",
        "/api/v1/admin/settings/email-forwarding.support-contacts",
        "/api/v1/users"
    })
    @DisplayName("should_return401_when_anonymousRequestsPersonalDataEndpoint")
    void should_return401_when_anonymousRequestsPersonalDataEndpoint(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/events/attendance-summary",
        "/api/v1/events/registrations",
        "/api/v1/events/BATbern60/sessions/unassigned",
        "/api/v1/events/BATbern60/sessions/conflicts"
    })
    @DisplayName("should_return401_when_anonymousRequestsOrganizerPathMatchedByPublicWildcard")
    void should_return401_when_anonymousRequestsOrganizerPathMatchedByPublicWildcard(String path)
            throws Exception {
        // GHSA-qhqv: GET /events/* and /events/*/sessions/* are public for event codes and
        // session slugs, but these literal segments are organizer endpoints.
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/events/BATbern60",
        "/api/v1/events/BATbern60/sessions/keynote",
        "/api/v1/events/types",
        "/api/v1/events/recent-photos"
    })
    @DisplayName("should_reachService_when_anonymousRequestsPublicEventPath")
    void should_reachService_when_anonymousRequestsPublicEventPath(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("should_return401_when_anonymousCallsRetiredMagicLinkEndpoint")
    void should_return401_when_anonymousCallsRetiredMagicLinkEndpoint() throws Exception {
        // The magic-link portal was removed in 11.F.1; its permitAll rules outlived it.
        mockMvc.perform(get("/api/v1/speaker-portal/dashboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/speaker-portal/validate-token")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/speaker-portal/respond")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/events/BATbern60/registrations",
        "/api/v1/events/BATbern60/distribution-list/participants",
        "/api/v1/admin/settings/email-forwarding.support-contacts",
        "/api/v1/users"
    })
    @DisplayName("should_reachService_when_callerHasValidToken")
    void should_reachService_when_callerHasValidToken(String path) throws Exception {
        // Positive path: the gateway only authenticates; the role check is the service's job.
        // A Watch token (HS256, test-default secret) is the one token type the chain can verify
        // here without contacting Cognito's JWK endpoint.
        mockMvc.perform(get(path).header("Authorization", "Bearer " + watchToken()))
                .andExpect(status().isOk());
    }

    private static String watchToken() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("batbern-watch")
                .subject("test.organizer")
                .claim("role", "ORGANIZER")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(WATCH_TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/internal/users/john.doe", "/internal/users", "/internal/partner-meetings/rsvps"})
    @DisplayName("should_denyInternalPath_when_requestedThroughGateway")
    void should_denyInternalPath_when_requestedThroughGateway(String path) throws Exception {
        // /internal/** is the VPC-only service API; even a valid token must not reach it here.
        mockMvc.perform(get(path).header("Authorization", "Bearer " + watchToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should_reachService_when_anonymousRequestsPublicEvent")
    void should_reachService_when_anonymousRequestsPublicEvent() throws Exception {
        // Control: proves the chain under test is loaded and lets public paths through,
        // so the 401s above come from the rules and not from a broken context.
        mockMvc.perform(get("/api/v1/events/BATbern60"))
                .andExpect(status().isOk());
    }
}
