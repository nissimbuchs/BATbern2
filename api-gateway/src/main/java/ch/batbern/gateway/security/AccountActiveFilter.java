package ch.batbern.gateway.security;

import ch.batbern.gateway.client.GatewayUserStatusClient;
import ch.batbern.gateway.util.LogSanitizer;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * AccountActiveFilter — request-time {@code is_active} gate at the API gateway (Story 12.2,
 * SSO PR 1 Part A).
 *
 * <p>Runs AFTER Spring Security has authenticated the request (mirrors {@link
 * RateLimitingFilter}'s {@code @Order(LOWEST_PRECEDENCE)} "after Spring Security
 * authentication" precedence). For an authenticated caller it resolves the account's {@code
 * active} status from CUMS (Caffeine-cached ~60s) and returns {@code 403 ACCOUNT_DEACTIVATED}
 * when the account is deactivated. It is invisible to active users.
 *
 * <p>Why the gateway, not the Cognito {@code PreAuthentication} trigger: PreAuthentication is
 * the only is_active enforcement today, but Cognito never fires it for FEDERATED (Google)
 * logins, and it only bites at login — so a deactivated user's still-valid 24h token keeps
 * working. This provider-agnostic, request-time gate closes both gaps (deactivation effective
 * within ~60s) at the single front door.
 *
 * <p>Safety properties:
 * <ul>
 *   <li><b>Authenticated-only:</b> anonymous / non-JWT principals pass straight through, so the
 *       {@code permitAll} public routes are never gated.</li>
 *   <li><b>Fail-open:</b> a CUMS error or 404 allows the request (consistent with every other
 *       is_active path) — a CUMS hiccup must never lock out the platform.</li>
 *   <li><b>403, never 401:</b> 401 would trigger the SPA's JWT-refresh loop; 403 is terminal.</li>
 *   <li><b>Kill-switch:</b> {@code security.active-gate.enabled=false} makes it a pure
 *       pass-through (ships dark; instant revert without redeploy).</li>
 * </ul>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE) // After Spring Security authentication (see RateLimitingFilter)
@Slf4j
public class AccountActiveFilter extends OncePerRequestFilter {

    static final String DEACTIVATED_BODY =
            "{\"error\":\"ACCOUNT_DEACTIVATED\",\"message\":\"Your account has been deactivated.\"}";

    private final GatewayUserStatusClient statusClient;
    private final CorsHandler corsHandler;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final Cache<String, Boolean> activeCache;

    public AccountActiveFilter(
            GatewayUserStatusClient statusClient,
            CorsHandler corsHandler,
            MeterRegistry meterRegistry,
            @Value("${security.active-gate.enabled:false}") boolean enabled,
            @Value("${security.active-gate.ttl-seconds:60}") long ttlSeconds) {
        this.statusClient = statusClient;
        this.corsHandler = corsHandler;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
        this.activeCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(10_000)
                .recordStats()
                .build();
        log.info("AccountActiveFilter initialised — enabled={}, ttlSeconds={}", enabled, ttlSeconds);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Kill-switch: ship dark / instant revert. Pure pass-through, no CUMS call, no cache.
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        Jwt jwt = getAuthenticatedJwt();
        if (jwt == null) {
            // Anonymous / non-JWT principal → public/permitAll route. Never gate it.
            chain.doFilter(request, response);
            return;
        }

        // Key on the meaningful username (PreTokenGen-projected); fall back to the Cognito sub.
        String username = jwt.getClaimAsString("custom:username");
        if (username == null || username.isBlank()) {
            username = jwt.getSubject();
        }

        Boolean active;
        try {
            // Atomic load: Caffeine applies the loader at most once per key, so N concurrent
            // first-requests for the same user collapse to ONE CUMS call (no stampede). A loader
            // returning null (unknown — 404 / no body) is NOT cached and surfaces as null here, so
            // the unknown path still fails open AND retries next request.
            final String bearer = jwt.getTokenValue();
            final String cacheKey = username;
            active = activeCache.get(cacheKey,
                    key -> statusClient.getActiveStatus(key, bearer).orElse(null));
        } catch (Exception e) {
            // Transient CUMS failure (loader threw) → fail-open (allow), WARN, emit metric.
            // Caffeine does not cache a load that throws, so the next request retries.
            meterRegistry.counter("gateway.active_gate.cums_error").increment();
            log.warn("CUMS is_active lookup failed for {} — failing open (allowing request): {}",
                    LogSanitizer.sanitize(username), e.getMessage());
            chain.doFilter(request, response);
            return;
        }

        if (active == null) {
            // Unknown (404 / no body) → fail-open, not cached (retry next request).
            chain.doFilter(request, response);
            return;
        }

        if (Boolean.FALSE.equals(active)) {
            meterRegistry.counter("gateway.active_gate.account_deactivated_blocked").increment();
            log.warn("Blocking deactivated account: {} ({} {})",
                    LogSanitizer.sanitize(username),
                    request.getMethod(), LogSanitizer.sanitize(request.getRequestURI()));
            writeDeactivatedResponse(request, response);
            return; // terminal — do NOT proceed down the chain
        }

        chain.doFilter(request, response);
    }

    /**
     * Returns the authenticated {@link Jwt} principal, or {@code null} for an
     * unauthenticated/anonymous/non-JWT request. Mirrors {@code RateLimitingFilter.getUserContext}.
     */
    private Jwt getAuthenticatedJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())
                && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    /**
     * Writes the terminal {@code 403 ACCOUNT_DEACTIVATED} JSON response. Attaches CORS headers
     * the same way {@link RateLimitingFilter} does on its 429 so the browser can read the error
     * code cross-origin. MUST be 403 (not 401) to avoid the SPA's JWT-refresh loop.
     */
    private void writeDeactivatedResponse(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        addCorsHeaders(request, response);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(DEACTIVATED_BODY);
    }

    /**
     * Mirrors {@code RateLimitingFilter.addCorsHeaders} so an error response is readable
     * cross-origin by the SPA.
     */
    private void addCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin != null && corsHandler.isOriginAllowed(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods",
                    "GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD");
            response.setHeader("Access-Control-Allow-Headers",
                    "Authorization, Content-Type, X-Requested-With, X-Request-Id, "
                    + "X-Correlation-ID, Accept, Accept-Language");
            response.setHeader("Vary", "Origin");
        }
    }
}
