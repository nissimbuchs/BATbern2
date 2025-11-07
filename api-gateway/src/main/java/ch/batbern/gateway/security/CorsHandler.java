package ch.batbern.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;

@Slf4j
@Component
public class CorsHandler {

    private static final Set<String> ALLOWED_ORIGINS = Set.of(
        "https://www.batbern.ch",
        "https://staging.batbern.ch",
        "http://localhost:3000",
        "http://localhost:3001"
    );

    private static final Set<String> ALLOWED_METHODS = Set.of(
        "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"
    );

    // Accept all headers to ensure case-insensitivity (RFC 7230)
    // Previously used explicit list which was case-sensitive
    private static final boolean ALLOW_ALL_HEADERS = true;

    private static final Set<String> EXPOSED_HEADERS = Set.of(
        "X-Request-Id",
        "X-Correlation-ID",
        "X-Rate-Limit-Remaining",
        "X-Rate-Limit-Reset"
    );

    public void handleCorsRequest(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");

        if (origin != null && isOriginAllowed(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Expose-Headers", String.join(", ", EXPOSED_HEADERS));
            response.setHeader("Vary", "Origin");

            // Add security headers
            addSecurityHeaders(response);

            log.debug("CORS headers added for origin: {}", origin);
        } else if (origin != null) {
            log.warn("CORS request denied for origin: {}", origin);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // Handle caching based on endpoint
        handleCaching(request, response);
    }

    public void handlePreflightRequest(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");

        if (origin != null && isOriginAllowed(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Methods", String.join(", ", ALLOWED_METHODS));
            // Allow all headers to support case-insensitivity (RFC 7230)
            response.setHeader("Access-Control-Allow-Headers", "*");
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Max-Age", "3600"); // 1 hour
            response.setHeader("Vary", "Origin");

            // Add security headers
            addSecurityHeaders(response);

            response.setStatus(HttpServletResponse.SC_OK);
            log.debug("CORS preflight handled for origin: {}", origin);
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    public boolean isOriginAllowed(String origin) {
        if (origin == null) {
            return false;
        }

        // Check exact matches first
        if (ALLOWED_ORIGINS.contains(origin)) {
            return true;
        }

        // Check localhost patterns for development
        if (origin.startsWith("http://localhost:") || origin.startsWith("https://localhost:")) {
            return true;
        }

        return false;
    }

    public boolean areHeadersAllowed(HttpServletRequest request) {
        // Allow all headers to ensure case-insensitivity per RFC 7230
        // HTTP header names are case-insensitive, so we shouldn't reject based on case
        return ALLOW_ALL_HEADERS;
    }

    private void addSecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
    }

    private void handleCaching(HttpServletRequest request, HttpServletResponse response) {
        String uri = request.getRequestURI();

        if (uri.contains("/admin") || uri.contains("/auth")) {
            // No caching for sensitive endpoints
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        } else if (uri.startsWith("/api/content")) {
            // Allow caching for public content
            response.setHeader("Cache-Control", "public, max-age=300"); // 5 minutes
        }
    }
}