package ch.batbern.gateway.config;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the PRODUCTION gateway filter chain ({@code defaultSecurityFilterChain},
 * {@code @Profile("!test")}). Every other gateway test runs under the {@code test} profile,
 * whose chain permits everything, so the rules that actually guard {@code api.batbern.ch}
 * had no coverage.
 *
 * <p>Only {@link SecurityConfig} and a catch-all probe controller are loaded: a request the
 * chain lets through reaches the probe (200), a blocked one is rejected with 401 before it.
 * The JWK URI is never contacted, because no request here carries a token.
 */
@SpringJUnitWebConfig(ProductionSecurityFilterChainTest.WebConfig.class)
@ActiveProfiles("staging")
@TestPropertySource(properties =
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwks.example.invalid/keys")
@DisplayName("Production gateway filter chain")
class ProductionSecurityFilterChainTest {

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, ProbeController.class})
    static class WebConfig {
    }

    @RestController
    static class ProbeController {
        @GetMapping("/**")
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

    @Test
    @DisplayName("should_reachService_when_anonymousRequestsPublicEvent")
    void should_reachService_when_anonymousRequestsPublicEvent() throws Exception {
        // Control: proves the chain under test is loaded and lets public paths through,
        // so the 401s above come from the rules and not from a broken context.
        mockMvc.perform(get("/api/v1/events/BATbern60"))
                .andExpect(status().isOk());
    }
}
