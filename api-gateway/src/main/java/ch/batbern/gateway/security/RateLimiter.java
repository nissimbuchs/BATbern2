package ch.batbern.gateway.security;

import ch.batbern.gateway.auth.model.UserContext;
import ch.batbern.gateway.security.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final RateLimitStorage rateLimitStorage;

    // Rate limits per role (requests per minute)
    private static final Map<String, Integer> ROLE_RATE_LIMITS = Map.of(
        "organizer", 100,
        "speaker", 50,
        "partner", 75,
        "attendee", 20,
        "anonymous", 10
    );

    // Burst limits per role (requests per 10 seconds)
    private static final Map<String, Integer> ROLE_BURST_LIMITS = Map.of(
        "organizer", 20,
        "speaker", 15,
        "partner", 15,
        "attendee", 10,
        "anonymous", 5
    );

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

        int currentCount = rateLimitStorage.getCurrentRequestCount("anonymous", endpoint, "anonymous");
        int rateLimit = getRateLimitForRole("anonymous", endpoint);

        if (currentCount >= rateLimit) {
            log.warn("Rate limit exceeded for anonymous request from IP: {} current: {} limit: {}",
                clientIp, currentCount, rateLimit);
            return false;
        }

        rateLimitStorage.incrementRequestCount("anonymous", endpoint, "anonymous");
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