package ch.batbern.gateway.security;

import ch.batbern.gateway.config.TurnstileProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;

/**
 * TurnstileVerificationFilter intercepts protected endpoints and validates
 * Cloudflare Turnstile tokens before allowing requests through.
 *
 * AC1: Intercepts POST /api/v1/newsletter/subscribe and POST /api/v1/events/{code}/registrations
 * AC2: Missing X-Turnstile-Token -&gt; 403 with turnstile_required
 * AC3: Invalid token -&gt; 403 with turnstile_failed
 * AC4: Cloudflare unreachable -&gt; fail-open (log warning, let through)
 * AC5: turnstile.enabled=false -&gt; no-op
 *
 * Story 10.31
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1) // Runs just before RateLimitingFilter (@Order(LOWEST_PRECEDENCE))
@Slf4j
public class TurnstileVerificationFilter implements Filter {

    private final TurnstileProperties turnstileProperties;
    private final RestTemplate restTemplate;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public TurnstileVerificationFilter(
            TurnstileProperties turnstileProperties,
            @Qualifier("turnstileRestTemplate") RestTemplate restTemplate) {
        this.turnstileProperties = turnstileProperties;
        this.restTemplate = restTemplate;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // AC5: disabled → pass through
        if (!turnstileProperties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        // Skip OPTIONS (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // AC1: Check if this is a protected endpoint
        if (!isProtectedEndpoint(httpRequest.getMethod(), httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        // AC2: Require X-Turnstile-Token header
        String token = httpRequest.getHeader("X-Turnstile-Token");
        if (token == null || token.isBlank()) {
            log.debug("Turnstile token missing for {} {}", httpRequest.getMethod(),
                httpRequest.getRequestURI());
            addCorsHeaders(httpRequest, httpResponse);
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                "{\"error\":\"turnstile_required\",\"message\":\"Turnstile token required\"}"
            );
            return;
        }

        // AC3/AC4: Verify token with Cloudflare
        try {
            boolean valid = verifyToken(token, getClientIp(httpRequest));
            if (!valid) {
                log.debug("Turnstile token invalid for {} {}", httpRequest.getMethod(),
                    httpRequest.getRequestURI());
                addCorsHeaders(httpRequest, httpResponse);
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write(
                    "{\"error\":\"turnstile_failed\","
                    + "\"message\":\"Bot protection check failed. Please try again.\"}"
                );
                return;
            }
        } catch (Exception e) {
            // AC4: fail-open — Cloudflare unreachable
            log.warn("Turnstile verification failed (fail-open): {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }

    /**
     * Returns true if the method+URI matches any protected endpoint pattern.
     */
    private boolean isProtectedEndpoint(String method, String uri) {
        if (turnstileProperties.getProtectedEndpoints() == null) {
            return false;
        }
        return turnstileProperties.getProtectedEndpoints().stream()
            .anyMatch(pattern -> {
                String[] parts = pattern.split(":", 2);
                return parts.length == 2
                    && parts[0].equalsIgnoreCase(method)
                    && pathMatcher.match(parts[1], uri);
            });
    }

    /**
     * Calls Cloudflare siteverify and returns true if success=true.
     * Throws on network errors (caller handles fail-open, AC4).
     */
    @SuppressWarnings("unchecked")
    private boolean verifyToken(String token, String clientIp) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("secret", turnstileProperties.getSecretKey());
        body.add("response", token);
        body.add("remoteip", clientIp);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

        Map<String, Object> result = restTemplate.postForObject(
            turnstileProperties.getVerifyUrl(),
            requestEntity,
            Map.class
        );

        if (result == null) {
            return false;
        }
        Object success = result.get("success");
        return Boolean.TRUE.equals(success);
    }

    /**
     * Extracts the real client IP from X-Forwarded-For (set by ALB).
     * Falls back to getRemoteAddr() when header is absent.
     */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Adds CORS headers — copied from RateLimitingFilter to ensure 403 responses
     * are not blocked by the browser's CORS policy.
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
                + "X-Correlation-ID, Accept, Accept-Language, X-Turnstile-Token");
            response.setHeader("Access-Control-Expose-Headers",
                "X-Request-Id, X-Correlation-ID");
            response.setHeader("Vary", "Origin");
        }
    }

    private boolean isOriginAllowed(String origin) {
        if (origin == null) {
            return false;
        }
        if (origin.startsWith("http://localhost:") || origin.startsWith("https://localhost:")) {
            return true;
        }
        return origin.equals("https://staging.batbern.ch") || origin.equals("https://www.batbern.ch");
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("TurnstileVerificationFilter initialized (enabled={})",
            turnstileProperties.isEnabled());
    }

    @Override
    public void destroy() {
        log.info("TurnstileVerificationFilter destroyed");
    }
}
