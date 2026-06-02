package ch.batbern.gateway.integration;

import ch.batbern.gateway.client.GatewayUserStatusClient;
import ch.batbern.gateway.security.AccountActiveFilter;
import ch.batbern.gateway.security.CorsHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the API-gateway is_active gate (Story 12.2, AC9): a request from a
 * deactivated user is short-circuited with {@code 403 ACCOUNT_DEACTIVATED}; an active user's
 * request is forwarded to the downstream handler.
 *
 * <p>Drives the REAL {@link AccountActiveFilter} through a MockMvc servlet filter chain in front
 * of a probe controller, with the CUMS status client stubbed (the gateway has no DB → no
 * Testcontainers, per the story). A standalone MockMvc is used deliberately so the filter runs
 * deterministically AFTER the authenticated {@link SecurityContextHolder} is populated (the
 * filter's contract is "runs after Spring Security auth"); the full-context wiring of the
 * {@code @Component} filter is exercised by the other {@code @SpringBootTest} gateway tests
 * that boot the application context.
 */
@ExtendWith(MockitoExtension.class)
class AccountActiveGateIntegrationTest {

    @RestController
    static class ProbeController {
        @GetMapping("/api/v1/probe")
        String probe() {
            return "ok";
        }
    }

    @Mock
    private GatewayUserStatusClient statusClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AccountActiveFilter filter = new AccountActiveFilter(
                statusClient, new CorsHandler(), new SimpleMeterRegistry(), true, 60);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController()).addFilters(filter).build();
        authenticate();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
                .header("alg", "none")
                .claim("custom:username", "jane.doe")
                .subject("cognito-sub-123")
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, Collections.emptyList()));
    }

    @Test
    void should_return403AccountDeactivated_when_authenticatedUserIsInactive() throws Exception {
        when(statusClient.getActiveStatus(anyString(), anyString())).thenReturn(Optional.of(false));

        mockMvc.perform(get("/api/v1/probe"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ACCOUNT_DEACTIVATED")));
    }

    @Test
    void should_forwardToDownstream_when_authenticatedUserIsActive() throws Exception {
        when(statusClient.getActiveStatus(anyString(), anyString())).thenReturn(Optional.of(true));

        mockMvc.perform(get("/api/v1/probe"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }
}
