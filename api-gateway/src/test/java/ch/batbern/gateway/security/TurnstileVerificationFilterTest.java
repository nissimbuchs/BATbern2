package ch.batbern.gateway.security;

import ch.batbern.gateway.config.TurnstileProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TurnstileVerificationFilter.
 * Story 10.31 — AC1–5, AC11, Task 4
 */
@ExtendWith(MockitoExtension.class)
class TurnstileVerificationFilterTest {

    @Mock
    private RestTemplate restTemplate;

    private TurnstileProperties properties;
    private TurnstileVerificationFilter filter;

    @BeforeEach
    void setUp() {
        properties = new TurnstileProperties();
        properties.setVerifyUrl("https://challenges.cloudflare.com/turnstile/v0/siteverify");
        properties.setSecretKey("test-secret");
        properties.setSiteKey("test-site-key");
        properties.setProtectedEndpoints(List.of(
            "POST:/api/v1/newsletter/subscribe",
            "POST:/api/v1/events/*/registrations"
        ));
        filter = new TurnstileVerificationFilter(properties, restTemplate);
    }

    // ------------------------------------------------------------------ AC5
    @Test
    void disabled_passesAllRequestsThrough() throws Exception {
        properties.setEnabled(false);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/newsletter/subscribe");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull(); // chain was called
        assertThat(response.getStatus()).isEqualTo(200);
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    // ------------------------------------------------------------------ non-protected
    @Test
    void enabledButNonProtectedEndpoint_passesThrough() throws Exception {
        properties.setEnabled(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    // ------------------------------------------------------------------ AC2 (fail-open on missing token)
    @Test
    void missingToken_failsOpen() throws Exception {
        // Widget may be blocked by ad blocker or corporate firewall — fail open so legitimate
        // users are not locked out. Email confirmation acts as a secondary bot filter.
        properties.setEnabled(true);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/newsletter/subscribe");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull(); // chain WAS called
        assertThat(response.getStatus()).isEqualTo(200);
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    // ------------------------------------------------------------------ AC3 (valid)
    @Test
    @SuppressWarnings("unchecked")
    void validToken_passesThrough() throws Exception {
        properties.setEnabled(true);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(Map.of("success", true));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/newsletter/subscribe");
        request.addHeader("X-Turnstile-Token", "valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ AC3 (invalid)
    @Test
    @SuppressWarnings("unchecked")
    void invalidToken_returns403WithTurnstileFailed() throws Exception {
        properties.setEnabled(true);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(Map.of("success", false, "error-codes", List.of("invalid-input-response")));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/newsletter/subscribe");
        request.addHeader("X-Turnstile-Token", "invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("turnstile_failed");
        assertThat(chain.getRequest()).isNull();
    }

    // ------------------------------------------------------------------ AC4 (fail-open)
    @Test
    void cloudflareUnreachable_failsOpen() throws Exception {
        properties.setEnabled(true);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenThrow(new ResourceAccessException("Connection refused"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/newsletter/subscribe");
        request.addHeader("X-Turnstile-Token", "some-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // Fail-open: chain is invoked, no 403
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ wildcard path matching (AC1)
    @Test
    @SuppressWarnings("unchecked")
    void wildcardEndpoint_matchesEventRegistrations() throws Exception {
        properties.setEnabled(true);

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(Map.of("success", true));

        MockHttpServletRequest request = new MockHttpServletRequest("POST",
            "/api/v1/events/BATBERN-2026/registrations");
        request.addHeader("X-Turnstile-Token", "valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ OPTIONS skip
    @Test
    void optionsRequest_skippedRegardlessOfProtectedEndpoint() throws Exception {
        properties.setEnabled(true);

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/newsletter/subscribe");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }
}
