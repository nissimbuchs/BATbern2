package ch.batbern.gateway.security;

import ch.batbern.gateway.auth.model.UserContext;
import ch.batbern.gateway.security.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final RateLimitStorage rateLimitStorage;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * Per-IP anonymous rate limits for SES-triggering POST endpoints (T1.2).
     *
     * Each entry costs at most N sends through SES per IP per window. Anything
     * above the limit returns 429 from {@link RateLimitingFilter}. These limits
     * sit IN FRONT of the global anon limit (50/min for everything else) and
     * are intentionally tight — the public abuse vector is small in legitimate
     * traffic (a real user registers once, not five times in five minutes).
     *
     * Cluster-wide accounting is handled by AWS WAF (Tier 2). Per-pod state
     * here is already tight enough — even at N pods, the cross-pod ceiling
     * remains N × the configured per-pod limit, well below what triggered the
     * 2026-05-18 incident (1152 POSTs in 5 minutes).
     */
    private static final List<PathPolicy> ANON_PATH_POLICIES = List.of(
            new PathPolicy("POST", "/api/v1/events/*/registrations", 5, Duration.ofMinutes(5)),
            new PathPolicy("POST", "/api/v1/newsletter/subscribe", 5, Duration.ofMinutes(5)),
            new PathPolicy("POST", "/api/v1/registrations/deregister/by-email", 3, Duration.ofMinutes(5))
    );

    private record PathPolicy(String method, String pattern, int limit, Duration window) {
        boolean matches(String method, String path) {
            return this.method.equalsIgnoreCase(method)
                    && PATH_MATCHER.match(this.pattern, path);
        }
    }

    public boolean isRequestAllowed(UserContext userContext, HttpServletRequest request) {
        String userId = userContext.getUserId();
        String role = userContext.getRole();
        String endpoint = request.getRequestURI();

        log.debug("Checking rate limit for user: {} role: {} endpoint: {}", userId, role, endpoint);

        int currentCount = rateLimitStorage.getCurrentRequestCount(userId, endpoint, role);
        int rateLimit = getRateLimitForRole(role, endpoint);

        if (currentCount >= rateLimit) {
            log.warn("Rate limit exceeded for user: {} current: {} limit: {}", userId, currentCount, rateLimit);
            return false;
        }

        rateLimitStorage.incrementRequestCount(userId, endpoint, role);
        return true;
    }

    public void enforceRateLimit(UserContext userContext, HttpServletRequest request) {
        String userId = userContext.getUserId();
        String role = userContext.getRole();
        String endpoint = request.getRequestURI();

        int currentCount = rateLimitStorage.getCurrentRequestCount(userId, endpoint, role);
        int rateLimit = getRateLimitForRole(role, endpoint);

        if (currentCount >= rateLimit) {
            throw new RateLimitExceededException("Rate limit exceeded for user: " + userId);
        }
    }

    public boolean isAnonymousRequestAllowed(HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String endpoint = request.getRequestURI();
        String method = request.getMethod();

        // T1.2 — per-IP, per-path SES-abuse defense. Matches before the broad
        // global anon path so registration/newsletter/deregister POSTs are NOT
        // accounted on the shared "anonymous" bucket.
        for (PathPolicy policy : ANON_PATH_POLICIES) {
            if (policy.matches(method, endpoint)) {
                boolean allowed = rateLimitStorage.tryAcquireForPath(
                        clientIp, method, endpoint, policy.limit(), policy.window());
                if (!allowed) {
                    log.warn("Anonymous per-IP path limit reached: ip={} method={} path={} limit={}/{}",
                            clientIp, method, endpoint, policy.limit(), policy.window());
                }
                return allowed;
            }
        }

        // Story 11.C.1 / D3: bucket per client IP so one attacker cannot exhaust the
        // shared "anonymous" quota for every other anonymous visitor. The previous
        // implementation used a single global "anonymous" bucket, which made the
        // configured 50/min limit effectively a global throttle, not a per-client one.
        String bucketKey = "anonymous:" + clientIp;
        int currentCount = rateLimitStorage.getCurrentRequestCount(bucketKey, endpoint, "anonymous");
        int rateLimit = getRateLimitForRole("anonymous", endpoint);

        if (currentCount >= rateLimit) {
            log.warn("Rate limit exceeded for anonymous request from IP: {} current: {} limit: {}",
                clientIp, currentCount, rateLimit);
            return false;
        }

        rateLimitStorage.incrementRequestCount(bucketKey, endpoint, "anonymous");
        return true;
    }

    public boolean isBurstAllowed(UserContext userContext, HttpServletRequest request) {
        String role = userContext.getRole();
        int burstLimit = rateLimitStorage.getBurstLimit(role);

        // For testing, assume burst is tracked separately
        int currentBurstCount = rateLimitStorage.getCurrentRequestCount(
            userContext.getUserId(), "burst", role);

        return currentBurstCount < burstLimit;
    }

    public int getRateLimitForRole(String role, String endpoint) {
        // Return the rate limit from storage (which will handle endpoint modifiers)
        return rateLimitStorage.getRateLimit(role, endpoint);
    }

    public void resetRateLimit(String userId, String endpoint) {
        log.info("Resetting rate limit for user: {} endpoint: {}", userId, endpoint);
        rateLimitStorage.resetRequestCount(userId, endpoint);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}