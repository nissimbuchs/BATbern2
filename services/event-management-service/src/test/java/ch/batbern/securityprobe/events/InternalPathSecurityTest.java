package ch.batbern.securityprobe.events;

import ch.batbern.events.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 16.9 AC4 against the PRODUCTION EMS filter chain, which every integration test also runs
 * since 17.0b3. This probe checks the {@code /internal/**} permit that partner-coordination relies
 * on with nothing but the chain loaded. Only {@link SecurityConfig}
 * and a catch-all probe are loaded: a request the chain lets through reaches the probe (200), a
 * blocked one is rejected with 401 before it. The gateway denies {@code /internal/**}, so this path
 * is reachable only inside the VPC.
 *
 * <p>Lives outside {@code ch.batbern.events} on purpose: the catch-all probe controller must not be
 * component-scanned into the application context of the other tests.
 */
@SpringJUnitWebConfig(InternalPathSecurityTest.WebConfig.class)
@ActiveProfiles("staging")
@TestPropertySource(properties = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwks.example.invalid/keys")
@DisplayName("Production EMS filter chain: /internal/**")
class InternalPathSecurityTest {

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

    @Test
    @DisplayName("should_reachService_when_anonymousPostsInternalMail")
    void should_reachService_when_anonymousPostsInternalMail() throws Exception {
        mockMvc.perform(post("/internal/mails")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("should_rejectAnonymous_when_requestingOrganizerMailList")
    void should_rejectAnonymous_when_requestingOrganizerMailList() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern60/mails")).andExpect(status().isUnauthorized());
    }
}
