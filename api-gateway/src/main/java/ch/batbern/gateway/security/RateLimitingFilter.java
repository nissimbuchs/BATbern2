package ch.batbern.gateway.security;

import ch.batbern.gateway.auth.model.UserContext;
import ch.batbern.gateway.security.exception.RateLimitExceededException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * RateLimitingFilter enforces rate limiting on all API requests.
 *
 * Features:
 * - Role-based rate limits (organizer: 100/min, speaker: 50/min, etc.)
 * - Returns 429 Too Many Requests when limit exceeded
 * - Adds X-RateLimit-* headers to responses
 * - Uses existing RateLimiter and RateLimitStorage infrastructure
 *
 * Implements AC6: Rate Limiting from Story 1.11
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // After CorrelationIdFilter, before SecurityHeadersFilter
@Slf4j
@RequiredArgsConstructor
public class RateLimitingFilter implements Filter {

    private final RateLimiter rateLimiter;
    private final RateLimitStorage rateLimitStorage;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // Get user context (or anonymous if not authenticated)
            UserContext userContext = getUserContext(httpRequest);
            String userId = userContext != null ? userContext.getUserId() : "anonymous";
            String role = userContext != null ? userContext.getRole() : "anonymous";

            // Get current count and rate limit
            String endpoint = httpRequest.getRequestURI();
            int currentCount = rateLimitStorage.getCurrentRequestCount(userId, endpoint, role);
            int rateLimit = rateLimitStorage.getRateLimit(role, endpoint);
            int remaining = Math.max(0, rateLimit - currentCount);

            // Check if request is allowed
            boolean isAllowed = userContext != null
                ? rateLimiter.isRequestAllowed(userContext, httpRequest)
                : rateLimiter.isAnonymousRequestAllowed(httpRequest);

            // Add rate limit headers before checking if allowed
            // (so client knows their limits even when exceeded)
            addRateLimitHeaders(httpResponse, rateLimit, remaining);

            if (!isAllowed) {
                log.warn("Rate limit exceeded for user: {} role: {} endpoint: {} (count: {}, limit: {})",
                    userId, role, endpoint, currentCount, rateLimit);

                httpResponse.setStatus(429); // HTTP 429 Too Many Requests
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write(String.format(
                    "{\"error\":\"Rate limit exceeded\",\"message\":\"Too many requests. Limit: %d requests per minute.\",\"limit\":%d,\"retryAfter\":60}",
                    rateLimit, rateLimit
                ));
                return;
            }

            log.debug("Rate limit check passed for user: {} role: {} endpoint: {} (count: {}, limit: {})",
                userId, role, endpoint, currentCount, rateLimit);

            // Continue filter chain
            chain.doFilter(request, response);

        } catch (RateLimitExceededException e) {
            log.warn("Rate limit exception: {}", e.getMessage());
            httpResponse.setStatus(429); // HTTP 429 Too Many Requests
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                "{\"error\":\"Rate limit exceeded\",\"message\":\"" + e.getMessage() + "\"}"
            );
        }
    }

    /**
     * Adds X-RateLimit-* headers to the response
     */
    private void addRateLimitHeaders(HttpServletResponse response, int limit, int remaining) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(getNextMinuteTimestamp()));
    }

    /**
     * Gets the timestamp of the next minute (for rate limit reset)
     */
    private long getNextMinuteTimestamp() {
        long currentMinute = System.currentTimeMillis() / 60000;
        return (currentMinute + 1) * 60000;
    }

    /**
     * Gets the user context from the request (or null if anonymous)
     *
     * For now, treats all requests as anonymous. In production, this should
     * integrate with Spring Security to extract authenticated user context.
     */
    private UserContext getUserContext(HttpServletRequest request) {
        // TODO: Integrate with Spring Security OAuth2 to extract authenticated user
        // For now, return null to treat all requests as anonymous (10 req/min limit)
        return null;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("RateLimitingFilter initialized");
    }

    @Override
    public void destroy() {
        log.info("RateLimitingFilter destroyed");
    }
}
