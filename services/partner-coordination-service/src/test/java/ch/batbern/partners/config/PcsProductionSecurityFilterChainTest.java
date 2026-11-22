package ch.batbern.partners.config;

import ch.batbern.shared.test.security.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the PRODUCTION PCS filter chain ({@code productionFilterChain}) with the real shared
 * decoder (agentic interaction 0.1): PCS accepts Cognito and agent tokens and refuses Watch tokens.
 *
 * <p>Only {@link SecurityConfig} and a catch-all probe controller are loaded: a request the chain
 * lets through reaches the probe (200), a blocked one is rejected with 401 before it. The JWK URI is
 * never contacted: agent and Watch tokens are routed by their issuer and never reach the Cognito
 * verifier.
 */
@SpringJUnitWebConfig(PcsProductionSecurityFilterChainTest.WebConfig.class)
@ActiveProfiles("staging")
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwks.example.invalid/keys",
    "watch.jwt.secret=" + PcsProductionSecurityFilterChainTest.WATCH_TEST_SECRET
})
@DisplayName("Production PCS filter chain")
class PcsProductionSecurityFilterChainTest {

    static final String WATCH_TEST_SECRET = "production-chain-test-secret-at-least-32-bytes";

    /** Falls through to {@code anyRequest().authenticated()}: no role rule, a token is enough. */
    private static final String AUTHENTICATED_ONLY_PATH = "/api/v1/partners/acme";

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

    @DynamicPropertySource
    static void agentKey(DynamicPropertyRegistry registry) {
        registry.add("agent.jwt.public-key", TestTokens::agentPublicKeyPem);
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("should_reachService_when_anonymousRequestsPublicPartnerList")
    void should_reachService_when_anonymousRequestsPublicPartnerList() throws Exception {
        // Control: the chain under test is loaded and lets its public path through.
        mockMvc.perform(get("/api/v1/partners")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("should_return401_when_anonymousRequestsAuthenticatedPath")
    void should_return401_when_anonymousRequestsAuthenticatedPath() throws Exception {
        mockMvc.perform(get(AUTHENTICATED_ONLY_PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should_reachService_when_callerHasValidAgentToken")
    void should_reachService_when_callerHasValidAgentToken() throws Exception {
        String token = TestTokens.agentToken("agent-sub-1", "agent.planner", "run-1", "organizer");
        mockMvc.perform(get(AUTHENTICATED_ONLY_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should_return401_when_agentTokenSignedWithOtherKey")
    void should_return401_when_agentTokenSignedWithOtherKey() throws Exception {
        String token = TestTokens.agent("agent-sub-1", "agent.planner", "run-1", "test.organizer")
                .signedWith(TestTokens.newRsaKeyPair()).build();
        mockMvc.perform(get(AUTHENTICATED_ONLY_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should_return401_when_callerSendsValidWatchToken")
    void should_return401_when_callerSendsValidWatchToken() throws Exception {
        // Valid HS256 with the configured secret: gateway, CUMS and EMS accept it, PCS does not.
        String token = TestTokens.watchToken(WATCH_TEST_SECRET, "test.organizer", "ORGANIZER");
        String agent = TestTokens.agentToken("agent-sub-1", "agent.planner", "run-1", "organizer");
        mockMvc.perform(get(AUTHENTICATED_ONLY_PATH).header("Authorization", "Bearer " + agent))
                .andExpect(status().isOk()); // positive control: the path is reachable with a token
        mockMvc.perform(get(AUTHENTICATED_ONLY_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
