package ch.batbern.gateway.security;

import ch.batbern.gateway.auth.model.UserContext;
import ch.batbern.gateway.security.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitingTest {

    private RateLimiter rateLimiter;

    @Mock
    private RateLimitStorage rateLimitStorage;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(rateLimitStorage);
    }

    @Test
    @DisplayName("should_allowRequest_when_rateLimitNotExceeded")
    void should_allowRequest_when_rateLimitNotExceeded() {
        // Given
        UserContext organizerContext = UserContext.builder()
            .userId("org-123")
            .role("organizer")
            .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/events/create");

        when(rateLimitStorage.getCurrentRequestCount(any(), any(), any())).thenReturn(5);
        when(rateLimitStorage.getRateLimit(any(), any())).thenReturn(100); // 100 requests per minute for organizers

        // When
        boolean allowed = rateLimiter.isRequestAllowed(organizerContext, request);

        // Then
        assertThat(allowed).isTrue();
        verify(rateLimitStorage).incrementRequestCount(organizerContext.getUserId(), request.getRequestURI(), "organizer");
    }

    @Test
    @DisplayName("should_denyRequest_when_rateLimitExceeded")
    void should_denyRequest_when_rateLimitExceeded() {
        // Given
        UserContext attendeeContext = UserContext.builder()
            .userId("att-456")
            .role("attendee")
            .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/content/search");

        when(rateLimitStorage.getCurrentRequestCount(any(), any(), any())).thenReturn(21);
        when(rateLimitStorage.getRateLimit(any(), any())).thenReturn(20); // 20 requests per minute for attendees

        // When
        boolean allowed = rateLimiter.isRequestAllowed(attendeeContext, request);

        // Then
        assertThat(allowed).isFalse();
        verify(rateLimitStorage, never()).incrementRequestCount(any(), any(), any());
    }

    @Test
    @DisplayName("should_enforceRoleBasedLimits_when_differentRolesAccess")
    void should_enforceRoleBasedLimits_when_differentRolesAccess() {
        // Given - Organizer gets 100 req/min, Speaker gets 50 req/min, Attendee gets 20 req/min
        when(rateLimitStorage.getRateLimit("organizer", "/api/events")).thenReturn(100);
        when(rateLimitStorage.getRateLimit("speaker", "/api/speakers")).thenReturn(50);
        when(rateLimitStorage.getRateLimit("attendee", "/api/content")).thenReturn(20);

        // When
        int organizerLimit = rateLimiter.getRateLimitForRole("organizer", "/api/events");
        int speakerLimit = rateLimiter.getRateLimitForRole("speaker", "/api/speakers");
        int attendeeLimit = rateLimiter.getRateLimitForRole("attendee", "/api/content");

        // Then
        assertThat(organizerLimit).isEqualTo(100);
        assertThat(speakerLimit).isEqualTo(50);
        assertThat(attendeeLimit).isEqualTo(20);
    }

    @Test
    @DisplayName("should_resetRateLimit_when_timeWindowExpires")
    void should_resetRateLimit_when_timeWindowExpires() {
        // Given
        UserContext userContext = UserContext.builder()
            .userId("usr-789")
            .role("speaker")
            .build();

        String endpoint = "/api/speakers/profile";

        // When
        rateLimiter.resetRateLimit(userContext.getUserId(), endpoint);

        // Then
        verify(rateLimitStorage).resetRequestCount(userContext.getUserId(), endpoint);
    }

    @Test
    @DisplayName("should_enforceStrictLimits_when_strictModeEnabled")
    void should_enforceStrictLimits_when_strictModeEnabled() {
        // Given
        UserContext userContext = UserContext.builder()
            .userId("usr-strict")
            .role("attendee")
            .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/content/search");

        when(rateLimitStorage.getCurrentRequestCount(any(), any(), any())).thenReturn(21);
        when(rateLimitStorage.getRateLimit(any(), any())).thenReturn(20);

        // When / Then
        assertThatThrownBy(() -> rateLimiter.enforceRateLimit(userContext, request))
            .isInstanceOf(RateLimitExceededException.class)
            .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    @DisplayName("should_allowBurstTraffic_when_withinBurstLimits")
    void should_allowBurstTraffic_when_withinBurstLimits() {
        // Given
        UserContext userContext = UserContext.builder()
            .userId("usr-burst")
            .role("partner")
            .build();

        MockHttpServletRequest request = new MockHttpServletRequest();

        when(rateLimitStorage.getCurrentRequestCount(any(), any(), any())).thenReturn(5);
        when(rateLimitStorage.getBurstLimit("partner")).thenReturn(10);

        // When
        boolean allowed = rateLimiter.isBurstAllowed(userContext, request);

        // Then
        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("should_trackPerEndpointLimits_when_differentEndpointsAccessed")
    void should_trackPerEndpointLimits_when_differentEndpointsAccessed() {
        // Given
        UserContext userContext = UserContext.builder()
            .userId("usr-multi")
            .role("organizer")
            .build();

        String[] endpoints = {"/api/events/create", "/api/events/list", "/api/speakers/invite"};

        // When
        for (String endpoint : endpoints) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI(endpoint);

            when(rateLimitStorage.getCurrentRequestCount(userContext.getUserId(), endpoint, "organizer")).thenReturn(1);
            when(rateLimitStorage.getRateLimit("organizer", endpoint)).thenReturn(100);

            boolean allowed = rateLimiter.isRequestAllowed(userContext, request);
            assertThat(allowed).isTrue();
        }

        // Then
        verify(rateLimitStorage, times(3)).incrementRequestCount(eq("usr-multi"), any(), eq("organizer"));
    }

    @Test
    @DisplayName("should_handleAnonymousRequests_when_noUserContext")
    void should_handleAnonymousRequests_when_noUserContext() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/content/search");
        request.setRemoteAddr("192.168.1.100");

        // Story 11.C.1 / D3: anonymous bucket key is now "anonymous:{clientIp}" so one
        // attacker cannot exhaust the shared anonymous quota for every other visitor.
        String anonBucket = "anonymous:192.168.1.100";
        when(rateLimitStorage.getCurrentRequestCount(anonBucket, "/api/content/search", "anonymous")).thenReturn(5);
        when(rateLimitStorage.getRateLimit("anonymous", "/api/content/search")).thenReturn(10);

        // When
        boolean allowed = rateLimiter.isAnonymousRequestAllowed(request);

        // Then
        assertThat(allowed).isTrue();
        verify(rateLimitStorage).incrementRequestCount(anonBucket, "/api/content/search", "anonymous");
    }

    // ════════════════════════════════════════════════════════════════════════════
    // T1.2 — per-IP, per-path anonymous limits on SES-triggering POSTs.
    // Goal: stop OWASP ZAP / scripted abuse from burning SES quota on
    //   /events/*/registrations, /newsletter/subscribe, /registrations/deregister/by-email
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("anonPathLimit — POST /events/{code}/registrations is per-IP, not global")
    void should_callPathPolicy_when_postToRegistrations() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/events/BATbern59/registrations");
        request.setRemoteAddr("203.0.113.10");
        // Path policy is keyed by clientIp + path; the existing global anon
        // path (keyed by "anonymous") MUST NOT be touched for these requests.
        when(rateLimitStorage.tryAcquireForPath(eq("203.0.113.10"),
                eq("POST"), eq("/api/v1/events/BATbern59/registrations"),
                any(), any())).thenReturn(true);

        boolean allowed = rateLimiter.isAnonymousRequestAllowed(request);

        assertThat(allowed).isTrue();
        verify(rateLimitStorage).tryAcquireForPath(eq("203.0.113.10"),
                eq("POST"), eq("/api/v1/events/BATbern59/registrations"),
                any(), any());
        verify(rateLimitStorage, never()).incrementRequestCount(eq("anonymous"), any(), any());
    }

    @Test
    @DisplayName("anonPathLimit — POST /newsletter/subscribe blocks after limit per IP")
    void should_blockNewsletterSubscribe_when_perIpLimitReached() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/newsletter/subscribe");
        request.setRemoteAddr("203.0.113.11");
        when(rateLimitStorage.tryAcquireForPath(eq("203.0.113.11"),
                eq("POST"), eq("/api/v1/newsletter/subscribe"),
                any(), any())).thenReturn(false);

        boolean allowed = rateLimiter.isAnonymousRequestAllowed(request);

        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("anonPathLimit — POST /registrations/deregister/by-email matched")
    void should_callPathPolicy_when_deregisterByEmail() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/registrations/deregister/by-email");
        request.setRemoteAddr("203.0.113.12");
        when(rateLimitStorage.tryAcquireForPath(any(), any(), any(), any(), any()))
                .thenReturn(true);

        boolean allowed = rateLimiter.isAnonymousRequestAllowed(request);

        assertThat(allowed).isTrue();
        verify(rateLimitStorage).tryAcquireForPath(eq("203.0.113.12"),
                eq("POST"), eq("/api/v1/registrations/deregister/by-email"),
                any(), any());
    }

    @Test
    @DisplayName("anonPathLimit — X-Forwarded-For is used as the client IP")
    void should_useXForwardedFor_when_behindProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/newsletter/subscribe");
        request.setRemoteAddr("10.0.0.1"); // internal ALB hop
        request.addHeader("X-Forwarded-For", "198.51.100.42, 10.0.0.1");
        when(rateLimitStorage.tryAcquireForPath(any(), any(), any(), any(), any()))
                .thenReturn(true);

        rateLimiter.isAnonymousRequestAllowed(request);

        verify(rateLimitStorage).tryAcquireForPath(eq("198.51.100.42"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("anonPathLimit — GET on a watched path falls back to global anon limit")
    void should_notMatch_when_methodIsNotPost() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/v1/newsletter/subscribe");
        request.setRemoteAddr("203.0.113.20");
        when(rateLimitStorage.getCurrentRequestCount("anonymous", "/api/v1/newsletter/subscribe", "anonymous"))
                .thenReturn(0);
        when(rateLimitStorage.getRateLimit("anonymous", "/api/v1/newsletter/subscribe")).thenReturn(50);

        boolean allowed = rateLimiter.isAnonymousRequestAllowed(request);

        assertThat(allowed).isTrue();
        verify(rateLimitStorage, never()).tryAcquireForPath(any(), any(), any(), any(), any());
        verify(rateLimitStorage).incrementRequestCount("anonymous", "/api/v1/newsletter/subscribe", "anonymous");
    }

    @Test
    @DisplayName("anonPathLimit — POST on a non-watched path falls back to global anon limit")
    void should_notMatch_when_pathNotWatched() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/content/search");
        request.setRemoteAddr("203.0.113.21");
        when(rateLimitStorage.getCurrentRequestCount("anonymous", "/api/v1/content/search", "anonymous"))
                .thenReturn(0);
        when(rateLimitStorage.getRateLimit("anonymous", "/api/v1/content/search")).thenReturn(50);

        boolean allowed = rateLimiter.isAnonymousRequestAllowed(request);

        assertThat(allowed).isTrue();
        verify(rateLimitStorage, never()).tryAcquireForPath(any(), any(), any(), any(), any());
    }
}