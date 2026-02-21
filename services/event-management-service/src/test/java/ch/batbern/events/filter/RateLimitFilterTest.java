package ch.batbern.events.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RateLimitFilter
 * Story 6.1a: Magic Link Infrastructure - AC5 (Rate Limiting)
 *
 * Tests rate limiting for speaker portal token validation endpoint.
 * Rate limit: 5 requests per minute per IP (AC5)
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter();
    }

    /**
     * Test 5.5: Should return 429 when rate limit exceeded
     * AC5: Rate limited: 5 requests/minute per IP
     */
    @Test
    void should_return429_when_speakerPortalRateLimitExceeded() throws Exception {
        // Given - Speaker portal endpoint
        StringWriter responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        when(request.getRequestURI()).thenReturn("/api/v1/speaker-portal/validate-token");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        // When - Make 6 requests (limit is 5)
        for (int i = 0; i < 5; i++) {
            rateLimitFilter.doFilter(request, response, filterChain);
        }

        // Then - First 5 requests should pass
        verify(filterChain, times(5)).doFilter(request, response);

        // When - 6th request
        rateLimitFilter.doFilter(request, response, filterChain);

        // Then - 6th request should be rate limited (429)
        verify(response).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        verify(response).setContentType("application/json");
        assertThat(responseWriter.toString()).contains("Too many requests");
        assertThat(responseWriter.toString()).contains("Rate limit exceeded");
    }

    /**
     * Test: Should allow requests within rate limit
     * AC5: 5 requests per minute should be allowed
     */
    @Test
    void should_allowRequests_when_withinSpeakerPortalRateLimit() throws Exception {
        // Given - Speaker portal endpoint from a specific IP
        when(request.getRequestURI()).thenReturn("/api/v1/speaker-portal/validate-token");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        // When - Make exactly 5 requests (at the limit)
        for (int i = 0; i < 5; i++) {
            rateLimitFilter.doFilter(request, response, filterChain);
        }

        // Then - All 5 requests should pass through
        verify(filterChain, times(5)).doFilter(request, response);
        verify(response, never()).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    /**
     * Test: Should track rate limits per IP separately
     * AC5: Rate limit is per IP address
     */
    @Test
    void should_trackRateLimitPerIp_when_differentIpsRequest() throws Exception {
        // Given - Speaker portal endpoint
        when(request.getRequestURI()).thenReturn("/api/v1/speaker-portal/validate-token");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        // When - First IP makes 5 requests
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        for (int i = 0; i < 5; i++) {
            rateLimitFilter.doFilter(request, response, filterChain);
        }

        // Then - Second IP should still be able to make requests
        when(request.getRemoteAddr()).thenReturn("192.168.1.2");
        rateLimitFilter.doFilter(request, response, filterChain);

        // All 6 requests should have passed (5 from IP1 + 1 from IP2)
        verify(filterChain, times(6)).doFilter(request, response);
    }

    /**
     * Test: Should extract IP from X-Forwarded-For header
     * AC5: Handle proxied requests correctly
     */
    @Test
    void should_extractIpFromXForwardedFor_when_headerPresent() throws Exception {
        // Given - Speaker portal endpoint with X-Forwarded-For
        StringWriter responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        when(request.getRequestURI()).thenReturn("/api/v1/speaker-portal/validate-token");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.50, 10.0.0.1");

        // When - Make 6 requests (should use first IP from X-Forwarded-For)
        for (int i = 0; i < 6; i++) {
            rateLimitFilter.doFilter(request, response, filterChain);
        }

        // Then - 5 should pass, 6th should be rate limited
        verify(filterChain, times(5)).doFilter(request, response);
        verify(response).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    /**
     * Test: Should not apply rate limiting to non-rate-limited endpoints
     * Only registration and speaker portal endpoints are rate limited
     */
    @Test
    void should_notRateLimit_when_endpointNotRateLimited() throws Exception {
        // Given - A non-rate-limited endpoint
        when(request.getRequestURI()).thenReturn("/api/v1/events/current");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("192.168.1.200");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        // When - Make many requests
        for (int i = 0; i < 20; i++) {
            rateLimitFilter.doFilter(request, response, filterChain);
        }

        // Then - All requests should pass through
        verify(filterChain, times(20)).doFilter(request, response);
        verify(response, never()).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    }
}
