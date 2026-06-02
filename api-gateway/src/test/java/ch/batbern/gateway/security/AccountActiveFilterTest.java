package ch.batbern.gateway.security;

import ch.batbern.gateway.client.GatewayUserStatusClient;
import ch.batbern.gateway.client.GatewayUserStatusException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AccountActiveFilter} — Story 12.2 (API-gateway is_active gate).
 * Covers AC9 (a)-(g): active→pass, inactive→403, cache-hit, CUMS error→fail-open,
 * CUMS 404→fail-open, anonymous→pass-through, kill-switch→pass-through.
 */
@ExtendWith(MockitoExtension.class)
class AccountActiveFilterTest {

    @Mock
    private GatewayUserStatusClient statusClient;

    private MeterRegistry meterRegistry;
    private AccountActiveFilter filter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // enabled=true so the gate is exercised; ttl long enough that nothing expires mid-test.
        filter = new AccountActiveFilter(statusClient, meterRegistry, true, 60);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username) {
        Jwt jwt = Jwt.withTokenValue("test-token-value")
                .header("alg", "none")
                .claim("custom:username", username)
                .subject("cognito-sub-123")
                .build();
        // 2-arg constructor sets authenticated=true (as Spring's BearerTokenAuthenticationFilter
        // does); the single-arg constructor would leave it UNauthenticated.
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, Collections.emptyList()));
    }

    // ---------------------------------------------------------------- AC9 (a)
    @Test
    void should_passThrough_when_userIsActive() throws Exception {
        authenticateAs("john.doe");
        when(statusClient.getActiveStatus(anyString(), anyString())).thenReturn(Optional.of(true));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull(); // chain proceeded
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ---------------------------------------------------------------- AC9 (b) + AC5
    @Test
    void should_return403AccountDeactivated_when_userInactive() throws Exception {
        authenticateAs("jane.doe");
        when(statusClient.getActiveStatus(anyString(), anyString())).thenReturn(Optional.of(false));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull(); // chain NOT invoked — terminal
        assertThat(response.getStatus()).isEqualTo(403); // NOT 401 (refresh-loop trap)
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("ACCOUNT_DEACTIVATED");
    }

    // ---------------------------------------------------------------- AC9 (c)
    @Test
    void should_callCumsOnlyOnce_when_twoRequestsForSameUser() throws Exception {
        authenticateAs("john.doe");
        when(statusClient.getActiveStatus(anyString(), anyString())).thenReturn(Optional.of(true));

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/events"),
                new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/events"),
                new MockHttpServletResponse(), new MockFilterChain());

        verify(statusClient, times(1)).getActiveStatus(anyString(), anyString());
    }

    // ---------------------------------------------------------------- AC9 (d) + AC6
    @Test
    void should_failOpenAndCountError_when_cumsThrows() throws Exception {
        authenticateAs("john.doe");
        when(statusClient.getActiveStatus(anyString(), anyString()))
                .thenThrow(new GatewayUserStatusException("boom", new RuntimeException()));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull(); // failed open — request allowed
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(meterRegistry.counter("gateway.active_gate.cums_error").count()).isEqualTo(1.0);
    }

    // ---------------------------------------------------------------- AC9 (e) + AC6
    @Test
    void should_failOpen_when_cumsReturns404Unknown() throws Exception {
        authenticateAs("fresh.federated");
        when(statusClient.getActiveStatus(anyString(), anyString())).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull(); // unknown → fail-open
        assertThat(response.getStatus()).isEqualTo(200);
        // Unknown must NOT be cached — a second request retries CUMS.
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/events"),
                new MockHttpServletResponse(), new MockFilterChain());
        verify(statusClient, times(2)).getActiveStatus(anyString(), anyString());
    }

    // ---------------------------------------------------------------- AC9 (f)
    @Test
    void should_passThroughWithoutCumsCall_when_unauthenticated() throws Exception {
        // No authentication set → anonymous / public route.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events/current");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(statusClient, never()).getActiveStatus(anyString(), any());
    }

    // ---------------------------------------------------------------- AC9 (g) + AC7
    @Test
    void should_passThroughWithoutCumsCall_when_killSwitchDisabled() throws Exception {
        AccountActiveFilter disabledFilter =
                new AccountActiveFilter(statusClient, meterRegistry, false, 60);
        authenticateAs("jane.doe"); // even an authenticated (would-be-blocked) user passes

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        disabledFilter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(statusClient, never()).getActiveStatus(anyString(), any());
    }
}
