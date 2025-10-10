package ch.batbern.gateway.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filter that adds API version header to all responses.
 *
 * Extracts the version from the request path (e.g., /api/v1/...) and adds it
 * as an API-Version header to the response.
 *
 * This ensures clients always know which API version they're interacting with,
 * even in error responses.
 *
 * Path pattern: /api/{version}/...
 * Examples:
 * - /api/v1/events → API-Version: v1
 * - /api/v2/speakers → API-Version: v2
 * - /api/events → No version header (invalid path)
 */
@Component
@Order(1) // Run early in filter chain
@Slf4j
public class ApiVersionHeaderFilter implements Filter {

    private static final String VERSION_HEADER = "API-Version";
    private static final Pattern VERSION_PATTERN = Pattern.compile("/api/(v\\d+)/.*");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Extract version from request path
        String version = extractVersion(httpRequest.getRequestURI());

        // Add version header if found
        if (version != null) {
            httpResponse.setHeader(VERSION_HEADER, version);
            log.debug("Added API version header: {}", version);
        }

        // Continue filter chain
        chain.doFilter(request, response);
    }

    /**
     * Extracts API version from request URI.
     *
     * @param requestURI the request URI (e.g., /api/v1/events)
     * @return the version string (e.g., "v1"), or null if no version found
     */
    private String extractVersion(String requestURI) {
        Matcher matcher = VERSION_PATTERN.matcher(requestURI);
        if (matcher.matches()) {
            return matcher.group(1); // Extract captured group (v1, v2, etc.)
        }
        return null;
    }
}
