package ch.batbern.companyuser.config;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
 * Exercises the PRODUCTION CUMS filter chain ({@code filterChain}, {@code @Profile("!test & !local")}),
 * #1136. Every integration test runs the hand-maintained {@code test} chain, so the {@code permitAll}
 * matchers and the {@code VpcInternalAuthorizationManager} rules that guard production had no test.
 *
 * <p>Only {@link SecurityConfig} and a catch-all probe controller are loaded: a request the chain
 * lets through reaches the probe (200), a blocked one is rejected with 401 before it. A request
 * "through the gateway" carries the public hop the ALB appends to {@code X-Forwarded-For} and
 * arrives from 127.0.0.1 (Service Connect), exactly as in production.
 */
@SpringJUnitWebConfig(ProductionSecurityFilterChainTest.WebConfig.class)
@ActiveProfiles("staging")
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwks.example.invalid/keys",
    "watch.jwt.secret=" + ProductionSecurityFilterChainTest.WATCH_TEST_SECRET,
    "vpc.cidr=10.1.0.0/16"
})
@DisplayName("Production CUMS filter chain")
class ProductionSecurityFilterChainTest {

    static final String WATCH_TEST_SECRET = "production-chain-test-secret-at-least-32-bytes";

    /** API Gateway egress address as the ALB appends it (measured on production, see ClientIp). */
    private static final String GATEWAY_HOP = "3.71.123.100";

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

    private static MockHttpServletRequestBuilder viaGateway(MockHttpServletRequestBuilder request) {
        return request.with(r -> {
            r.setRemoteAddr("127.0.0.1");
            return r;
        }).header("X-Forwarded-For", GATEWAY_HOP);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/companies/search",
        "/api/v1/companies/acme",
        "/api/v1/public/organizers",
        "/api/v1/public/users/john.doe",
        "/api/v1/public/settings/presentation",
        "/api/v1/users/additional-emails/verify",
        "/actuator/health"
    })
    @DisplayName("should_reachService_when_anonymousRequestsPublicPath")
    void should_reachService_when_anonymousRequestsPublicPath(String path) throws Exception {
        mockMvc.perform(viaGateway(get(path))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/watch/pair", "/api/v1/watch/authenticate",
        "/api/v1/users/additional-emails/verify"})
    @DisplayName("should_reachService_when_anonymousPostsCredentialedPublicPath")
    void should_reachService_when_anonymousPostsCredentialedPublicPath(String path) throws Exception {
        mockMvc.perform(viaGateway(post(path))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/users",
        "/api/v1/users/john.doe",
        "/api/v1/users/me",
        "/api/v1/users/john.doe/roles",
        "/api/v1/companies/acme/employees",
        "/api/v1/admin/settings",
        "/api/v1/notifications"
    })
    @DisplayName("should_return401_when_anonymousRequestsPersonalDataThroughGateway")
    void should_return401_when_anonymousRequestsPersonalDataThroughGateway(String path) throws Exception {
        mockMvc.perform(viaGateway(get(path))).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.1.0.5", "127.0.0.1", "10.1.0.5, 10.1.0.6"})
    @DisplayName("should_return401_when_clientSpoofsVpcAddressInXForwardedFor")
    void should_return401_when_clientSpoofsVpcAddressInXForwardedFor(String spoofed) throws Exception {
        // #1132: the client's header arrives with the ALB-appended public hop behind it.
        mockMvc.perform(get("/api/v1/users/john.doe")
                .with(r -> {
                    r.setRemoteAddr("127.0.0.1");
                    return r;
                })
                .header("X-Forwarded-For", spoofed + ", " + GATEWAY_HOP))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should_reachService_when_serviceCallsUserInsideVpcWithoutToken")
    void should_reachService_when_serviceCallsUserInsideVpcWithoutToken() throws Exception {
        // EMS/PCS through Service Connect: loopback connection, no X-Forwarded-For, no JWT.
        mockMvc.perform(get("/api/v1/users/john.doe").with(r -> {
            r.setRemoteAddr("127.0.0.1");
            return r;
        })).andExpect(status().isOk());
        mockMvc.perform(get("/internal/users/john.doe")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("should_return401_when_vpcCallerRequestsCurrentUserWithoutToken")
    void should_return401_when_vpcCallerRequestsCurrentUserWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users", "/api/v1/users/john.doe", "/api/v1/users/me"})
    @DisplayName("should_reachService_when_callerHasValidToken")
    void should_reachService_when_callerHasValidToken(String path) throws Exception {
        // The chain only authenticates; the role check is @PreAuthorize on the controller.
        mockMvc.perform(viaGateway(get(path)).header("Authorization", "Bearer " + watchToken()))
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
