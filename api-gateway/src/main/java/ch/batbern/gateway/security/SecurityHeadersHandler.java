package ch.batbern.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@Component
public class SecurityHeadersHandler {

    public void addSecurityHeaders(HttpServletRequest request, HttpServletResponse response) {
        // Strict Transport Security (HSTS) - only for HTTPS
        if ("https".equals(request.getScheme())) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        }

        // Content Security Policy
        String csp = buildContentSecurityPolicy(request);
        response.setHeader("Content-Security-Policy", csp);

        // X-Frame-Options
        response.setHeader("X-Frame-Options", "DENY");

        // X-Content-Type-Options
        response.setHeader("X-Content-Type-Options", "nosniff");

        // X-XSS-Protection
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Referrer Policy
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions Policy
        String permissionsPolicy = buildPermissionsPolicy();
        response.setHeader("Permissions-Policy", permissionsPolicy);

        // Remove server information
        response.setHeader("Server", null);

        // Handle caching based on endpoint sensitivity
        handleCacheControl(request, response);

        log.debug("Security headers added for request: {}", request.getRequestURI());
    }

    private String buildContentSecurityPolicy(HttpServletRequest request) {
        StringBuilder csp = new StringBuilder();

        // Default source
        csp.append("default-src 'self'; ");

        // Script sources
        csp.append("script-src 'self' 'unsafe-inline'; ");

        // Style sources
        csp.append("style-src 'self' 'unsafe-inline'; ");

        // Image sources
        csp.append("img-src 'self' data: https:; ");

        // Connect sources (for API calls)
        if (isLocalhost(request)) {
            csp.append("connect-src 'self' http://localhost:* https://api.batbern.ch; ");
        } else {
            csp.append("connect-src 'self' https://api.batbern.ch; ");
        }

        // Font sources
        csp.append("font-src 'self'; ");

        // Object sources
        csp.append("object-src 'none'; ");

        // Base URI
        csp.append("base-uri 'self'; ");

        // Form action
        csp.append("form-action 'self'; ");

        // Frame ancestors
        csp.append("frame-ancestors 'none'; ");

        return csp.toString().trim();
    }

    private String buildPermissionsPolicy() {
        return "geolocation=(), microphone=(), camera=(), payment=(), usb=(), " +
               "magnetometer=(), gyroscope=(), speaker=(), vibrate=(), fullscreen=(self)";
    }

    private void handleCacheControl(HttpServletRequest request, HttpServletResponse response) {
        String uri = request.getRequestURI();

        if (uri.contains("/admin") || uri.contains("/auth") || uri.contains("/events/admin")) {
            // No caching for sensitive endpoints
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        } else if (uri.startsWith("/api/content")) {
            // Allow caching for public content
            response.setHeader("Cache-Control", "public, max-age=300"); // 5 minutes
        } else {
            // Default caching policy
            response.setHeader("Cache-Control", "private, max-age=60"); // 1 minute
        }
    }

    private boolean isLocalhost(HttpServletRequest request) {
        String serverName = request.getServerName();
        return "localhost".equals(serverName) || "127.0.0.1".equals(serverName);
    }
}