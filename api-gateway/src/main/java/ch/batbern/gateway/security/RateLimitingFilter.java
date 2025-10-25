package ch.batbern.gateway.security;

import ch.batbern.gateway.auth.model.UserContext;
import ch.batbern.gateway.security.exception.RateLimitExceededException;
import ch.batbern.gateway.util.LogSanitizer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
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
@Order(Ordered.LOWEST_PRECEDENCE) // After Spring Security authentication
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

        // Skip rate limiting for OPTIONS requests (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

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
                    LogSanitizer.sanitize(userId), LogSanitizer.sanitize(role),
                    LogSanitizer.sanitize(endpoint), currentCount, rateLimit);

                // Add CORS headers to 429 response
                addCorsHeaders(httpRequest, httpResponse);

                httpResponse.setStatus(429); // HTTP 429 Too Many Requests
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write(String.format(
                    "{\"error\":\"Rate limit exceeded\","
                    + "\"message\":\"Too many requests. Limit: %d requests per minute.\","
                    + "\"limit\":%d,\"retryAfter\":60}",
                    rateLimit, rateLimit
                ));
                return;
            }

            log.debug("Rate limit check passed for user: {} role: {} endpoint: {} (count: {}, limit: {})",
                LogSanitizer.sanitize(userId), LogSanitizer.sanitize(role),
                LogSanitizer.sanitize(endpoint), currentCount, rateLimit);

            // Continue filter chain
            chain.doFilter(request, response);

        } catch (RateLimitExceededException e) {
            log.warn("Rate limit exception: {}", e.getMessage());

            // Add CORS headers to exception response
            addCorsHeaders(httpRequest, httpResponse);

            httpResponse.setStatus(429); // HTTP 429 Too Many Requests
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                "{\"error\":\"Rate limit exceeded\",\"message\":\"" + e.getMessage() + "\"}"
            );
        }
    }

    /**
     * Adds CORS headers to allow cross-origin requests
     */
    private void addCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin != null && isOriginAllowed(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods",
                "GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD");
            response.setHeader("Access-Control-Allow-Headers",
                "Authorization, Content-Type, X-Requested-With, X-Request-Id, "
                + "X-Correlation-ID, Accept, Accept-Language");
            response.setHeader("Access-Control-Expose-Headers",
                "X-Request-Id, X-Correlation-ID, X-RateLimit-Limit, "
                + "X-RateLimit-Remaining, X-RateLimit-Reset");
            response.setHeader("Vary", "Origin");
        }
    }

    /**
     * Checks if the origin is allowed for CORS
     */
    private boolean isOriginAllowed(String origin) {
        if (origin == null) {
            return false;
        }
        // Allow localhost for development
        if (origin.startsWith("http://localhost:") || origin.startsWith("https://localhost:")) {
            return true;
        }
        // Allow staging and production
        return origin.equals("https://staging.batbern.ch") || origin.equals("https://www.batbern.ch");
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
     * Extracts authenticated user from Spring Security JWT authentication
     */
    private UserContext getUserContext(HttpServletRequest request) {
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();

        log.debug("Getting user context - auth: {}, authenticated: {}, principal type: {}",
            auth != null ? auth.getClass().getSimpleName() : "null",
            auth != null ? auth.isAuthenticated() : false,
            auth != null && auth.getPrincipal() != null ? auth.getPrincipal().getClass().getSimpleName() : "null");

        if (auth != null && auth.isAuthenticated()
            && !"anonymousUser".equals(auth.getPrincipal())) {

            // Extract user details from JWT
            if (auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt) {
                org.springframework.security.oauth2.jwt.Jwt jwt =
                    (org.springframework.security.oauth2.jwt.Jwt) auth.getPrincipal();

                String userId = jwt.getSubject(); // Cognito sub claim
                String email = jwt.getClaimAsString("email");
                String rolesString = jwt.getClaimAsString("custom:role");

                // Extract first role (highest priority)
                String role = "attendee"; // default
                if (rolesString != null && !rolesString.isEmpty()) {
                    String[] roles = rolesString.split(",");
                    role = roles[0].trim().toLowerCase();
                }

                log.info("Extracted user context from JWT - userId: {}, email: {}, role: {}, rolesString: {}",
                    userId, email, role, rolesString);

                return UserContext.builder()
                    .userId(userId)
                    .email(email)
                    .emailVerified(true)
                    .role(role)
                    .issuedAt(jwt.getIssuedAt())
                    .expiresAt(jwt.getExpiresAt())
                    .build();
            } else {
                log.warn("Principal is not a JWT but: {}", auth.getPrincipal().getClass().getName());
            }
        } else {
            log.debug("No authenticated user - treating as anonymous");
        }

        return null; // Anonymous user
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
