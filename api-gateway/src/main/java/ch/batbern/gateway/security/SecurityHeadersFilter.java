package ch.batbern.gateway.security;

import ch.batbern.gateway.util.LogSanitizer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * SecurityHeadersFilter adds essential security headers to all HTTP responses.
 *
 * Security Headers Applied:
 * - Content-Security-Policy (CSP): Prevents XSS attacks
 * - Strict-Transport-Security (HSTS): Forces HTTPS
 * - X-Frame-Options: Prevents clickjacking
 * - X-Content-Type-Options: Prevents MIME sniffing
 * - X-XSS-Protection: Browser XSS protection
 * - Referrer-Policy: Controls referrer information
 * - Permissions-Policy: Controls browser features
 *
 * Implements AC1: Security Headers from Story 1.11
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2) // After CorrelationIdFilter
@Slf4j
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Content Security Policy - prevent XSS attacks
        String csp = buildContentSecurityPolicy();
        httpResponse.setHeader("Content-Security-Policy", csp);

        // HTTP Strict Transport Security - force HTTPS
        String hsts = "max-age=31536000; includeSubDomains";
        httpResponse.setHeader("Strict-Transport-Security", hsts);

        // X-Frame-Options - prevent clickjacking
        httpResponse.setHeader("X-Frame-Options", "DENY");

        // X-Content-Type-Options - prevent MIME sniffing
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");

        // X-XSS-Protection - enable browser XSS protection
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

        // Referrer-Policy - control referrer information
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions-Policy - control browser features
        String permissionsPolicy = buildPermissionsPolicy();
        httpResponse.setHeader("Permissions-Policy", permissionsPolicy);

        log.debug("Security headers applied to request: {}", LogSanitizer.sanitize(httpRequest.getRequestURI()));

        // Continue filter chain
        chain.doFilter(request, response);
    }

    /**
     * Builds Content Security Policy header value
     */
    private String buildContentSecurityPolicy() {
        StringBuilder csp = new StringBuilder();

        // Default source - only from same origin
        csp.append("default-src 'self'; ");

        // Script sources - self and inline (for React apps)
        csp.append("script-src 'self' 'unsafe-inline' 'unsafe-eval'; ");

        // Style sources - self and inline (for Material-UI)
        csp.append("style-src 'self' 'unsafe-inline'; ");

        // Image sources - self, data URIs, and HTTPS
        csp.append("img-src 'self' data: https:; ");

        // Font sources - self and data URIs
        csp.append("font-src 'self' data:; ");

        // Connect sources (for API calls) - self and AWS Cognito
        csp.append("connect-src 'self' https://cognito-idp.eu-central-1.amazonaws.com; ");

        // Object sources - none (prevent Flash/Java/ActiveX)
        csp.append("object-src 'none'; ");

        // Base URI - self only
        csp.append("base-uri 'self'; ");

        // Form action - self only
        csp.append("form-action 'self'; ");

        // Frame ancestors - none (equivalent to X-Frame-Options: DENY)
        csp.append("frame-ancestors 'none'; ");

        // Upgrade insecure requests to HTTPS
        csp.append("upgrade-insecure-requests");

        return csp.toString().trim();
    }

    /**
     * Builds Permissions Policy header value
     */
    private String buildPermissionsPolicy() {
        return "geolocation=(), "
                + "microphone=(), "
                + "camera=(), "
                + "payment=(), "
                + "usb=(), "
                + "magnetometer=(), "
                + "gyroscope=(), "
                + "speaker=(), "
                + "vibrate=(), "
                + "fullscreen=(self)";
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("SecurityHeadersFilter initialized");
    }

    @Override
    public void destroy() {
        log.info("SecurityHeadersFilter destroyed");
    }
}
