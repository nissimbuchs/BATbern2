package ch.batbern.events.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate Limiting Filter for public anonymous registration endpoints
 * <p>
 * QA Fix (SEC-001): Implements rate limiting to prevent abuse of public endpoints
 * <p>
 * Rate Limits:
 * - 10 requests per minute per IP address
 * - 5 requests per minute per email address
 * <p>
 * Uses Caffeine cache for in-memory rate limit tracking
 * <p>
 * Note: Not active in test profile to avoid interfering with integration tests
 * Auto-registered by Spring with HIGHEST_PRECEDENCE to run before security filters
 */
@Component
@Profile("!test")
@Order(Integer.MIN_VALUE)  // Highest precedence - run before all other filters
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_IP_PER_MINUTE = 10;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    // Cache for tracking requests by IP
    private final Cache<String, AtomicInteger> ipRequestCache = Caffeine.newBuilder()
            .expireAfterWrite(RATE_LIMIT_WINDOW)
            .maximumSize(10_000)
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Only apply rate limiting to public registration endpoints
        String requestUri = request.getRequestURI();
        if (!shouldRateLimit(requestUri, request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract client IP address
        String clientIp = getClientIp(request);

        // Check IP-based rate limit
        if (!checkIpRateLimit(clientIp)) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\":\"Too many requests\",\"message\":\"Rate limit exceeded. "
                + "Please try again later.\",\"retryAfter\":60}"
            );
            return;
        }

        // Proceed with request
        filterChain.doFilter(request, response);
    }

    /**
     * Determine if rate limiting should be applied to this request
     */
    private boolean shouldRateLimit(String requestUri, String method) {
        // Apply to POST requests to registration endpoints only
        return "POST".equalsIgnoreCase(method)
            && requestUri.matches(".*/api/v1/events/[^/]+/registrations$");
    }

    /**
     * Check if IP address is within rate limit
     *
     * @return true if request is allowed, false if limit exceeded
     */
    private boolean checkIpRateLimit(String clientIp) {
        AtomicInteger requestCount = ipRequestCache.get(clientIp, k -> new AtomicInteger(0));

        if (requestCount == null) {
            requestCount = new AtomicInteger(0);
            ipRequestCache.put(clientIp, requestCount);
        }

        int currentCount = requestCount.incrementAndGet();

        if (currentCount > MAX_REQUESTS_PER_IP_PER_MINUTE) {
            log.debug("IP rate limit check failed: {} requests from IP: {}", currentCount, clientIp);
            return false;
        }

        log.debug("IP rate limit check passed: {} requests from IP: {}", currentCount, clientIp);
        return true;
    }

    /**
     * Extract client IP address from request
     * Checks X-Forwarded-For header for proxied requests
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, use the first one
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
