package ch.batbern.securitychain;

import ch.batbern.events.config.SecurityConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the PRODUCTION EMS filter chain ({@code filterChain}, {@code @Profile("!test")}), #1136.
 * Every integration test runs {@code TestSecurityConfig}, whose chain is {@code anyRequest().permitAll()},
 * so the {@code permitAll} matchers that guard production had no test.
 *
 * <p>Lives outside {@code ch.batbern.events}/{@code ch.batbern.shared} on purpose: EMS scans those
 * packages with a plain {@code @ComponentScan} (no test-type exclusion), so a probe controller there
 * would be registered in every other EMS integration test and swallow their routes.
 *
 * <p>Only {@link SecurityConfig} and a catch-all probe controller are loaded: a request the chain
 * lets through reaches the probe (200), a blocked one is rejected with 401 before it.
 */
@SpringJUnitWebConfig(EmsProductionSecurityFilterChainTest.WebConfig.class)
@ActiveProfiles("staging")
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwks.example.invalid/keys",
    "watch.jwt.secret=" + EmsProductionSecurityFilterChainTest.WATCH_TEST_SECRET
})
@DisplayName("Production EMS filter chain")
class EmsProductionSecurityFilterChainTest {

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

    /** The intended anonymous surface: public website, Watch, registration, newsletter, thanks. */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET,  /actuator/health",
        "GET,  /api/v1/events",
        "GET,  /api/v1/events/current",
        "GET,  /api/v1/events/BATbern60",
        "GET,  /api/v1/public/events",
        "GET,  /api/v1/public/events/BATbern60",
        "GET,  /api/v1/events/BATbern60/sessions",
        "GET,  /api/v1/events/BATbern60/sessions/keynote",
        "GET,  /api/v1/events/BATbern60/sessions/keynote/speakers",
        "GET,  /api/v1/events/BATbern60/sessions/keynote/qna",
        "GET,  /api/v1/events/BATbern60/sessions/keynote/materials/m1/download",
        "GET,  /api/v1/events/BATbern60/live-timing",
        "GET,  /api/v1/events/BATbern60/photos",
        "GET,  /api/v1/events/recent-photos",
        "GET,  /api/v1/events/_global/teaser-images",
        "GET,  /api/v1/topics",
        "POST, /api/v1/events/BATbern60/registrations",
        "GET,  /api/v1/events/BATbern60/registrations/ABC123",
        "POST, /api/v1/events/BATbern60/registrations/confirm",
        "POST, /api/v1/newsletter/subscribe",
        "GET,  /api/v1/newsletter/unsubscribe/verify",
        "POST, /api/v1/newsletter/unsubscribe",
        "POST, /api/v1/events/BATbern60/thanks",
        "GET,  /api/v1/events/BATbern60/thanks",
        "GET,  /api/v1/thanks/featured",
        "GET,  /api/v1/registrations/deregister/verify",
        "POST, /api/v1/registrations/deregister",
        "POST, /api/v1/registrations/deregister/by-email",
        "GET,  /api/v1/public/settings/features"
    })
    @DisplayName("should_reachService_when_anonymousRequestsPublicPath")
    void should_reachService_when_anonymousRequestsPublicPath(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isOk());
    }

    /** Personal data and every write outside the public surface need a token. */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET,    /api/v1/events/BATbern60/registrations",
        "DELETE, /api/v1/events/BATbern60/registrations/ABC123",
        "GET,    /api/v1/events/BATbern60/distribution-list/participants",
        "GET,    /api/v1/events/BATbern60/participants/export",
        "GET,    /api/v1/newsletter/subscribers",
        "GET,    /api/v1/newsletter/my-subscription",
        "GET,    /api/v1/admin/settings/email-forwarding.support-contacts",
        "GET,    /api/v1/speaker-portal/profile",
        "GET,    /api/v1/events/BATbern60/speakers/pool",
        "POST,   /api/v1/events",
        "PUT,    /api/v1/events/BATbern60",
        "DELETE, /api/v1/events/BATbern60",
        "POST,   /api/v1/events/BATbern60/sessions",
        "POST,   /api/v1/topics",
        "PATCH,  /api/v1/events/BATbern60/thanks/t1",
        "POST,   /api/v1/events/BATbern60/live-timing/actions",
        // GHSA-qhqv: organizer paths the public GET wildcards would match as event code / slug
        "GET,    /api/v1/events/attendance-summary",
        "GET,    /api/v1/events/registrations",
        "GET,    /api/v1/events/BATbern60/sessions/unassigned",
        "GET,    /api/v1/events/BATbern60/sessions/conflicts"
    })
    @DisplayName("should_return401_when_anonymousRequestsProtectedPath")
    void should_return401_when_anonymousRequestsProtectedPath(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET,  /api/v1/events/BATbern60/registrations",
        "GET,  /api/v1/newsletter/subscribers",
        "POST, /api/v1/events"
    })
    @DisplayName("should_reachService_when_callerHasValidToken")
    void should_reachService_when_callerHasValidToken(String method, String path) throws Exception {
        // The chain only authenticates; the role check is @PreAuthorize on the controller.
        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                .header("Authorization", "Bearer " + watchToken()))
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
}
