package ch.batbern.gateway.routing;

import ch.batbern.gateway.auth.model.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class RequestTransformer {

    public HttpServletRequest addUserContext(HttpServletRequest request, UserContext userContext) {
        if (userContext == null) {
            log.debug("No user context provided, returning original request");
            return request;
        }

        log.debug("Adding user context headers for user: {}", userContext.getUserId());

        Map<String, String> additionalHeaders = new HashMap<>();
        additionalHeaders.put("X-User-Id", userContext.getUserId());
        additionalHeaders.put("X-User-Email", userContext.getEmail());
        additionalHeaders.put("X-User-Role", userContext.getRole());

        if (userContext.getCompanyId() != null) {
            additionalHeaders.put("X-Company-Id", userContext.getCompanyId());
        }

        return new EnhancedHttpServletRequestWrapper(request, additionalHeaders);
    }

    public HttpServletRequest addRequestId(HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.debug("Adding request ID: {}", requestId);

        Map<String, String> additionalHeaders = new HashMap<>();
        additionalHeaders.put("X-Request-Id", requestId);

        return new EnhancedHttpServletRequestWrapper(request, additionalHeaders);
    }

    public HttpServletRequest addCorrelationHeaders(HttpServletRequest request) {
        log.debug("Adding correlation headers");

        Map<String, String> additionalHeaders = new HashMap<>();
        additionalHeaders.put("X-Correlation-Id", UUID.randomUUID().toString());
        additionalHeaders.put("X-Gateway-Timestamp", Instant.now().toString());
        additionalHeaders.put("X-Request-Source", "api-gateway");

        return new EnhancedHttpServletRequestWrapper(request, additionalHeaders);
    }

    public String transformPath(String originalPath, String targetService) {
        log.debug("Transforming path: {} for service: {}", originalPath, targetService);

        // Remove /api prefix for microservices
        if (originalPath.startsWith("/api/")) {
            return originalPath.substring(4);
        }

        return originalPath;
    }

    public Map<String, Object> extractRequestMetadata(HttpServletRequest request) {
        Map<String, Object> metadata = new HashMap<>();

        metadata.put("method", request.getMethod());
        metadata.put("path", request.getRequestURI());
        metadata.put("userAgent", request.getHeader("User-Agent"));
        metadata.put("clientIp", request.getRemoteAddr());
        metadata.put("timestamp", Instant.now().toString());

        log.debug("Extracted request metadata: {}", metadata);
        return metadata;
    }

    private static class EnhancedHttpServletRequestWrapper extends HttpServletRequestWrapper {
        private final Map<String, String> additionalHeaders;

        EnhancedHttpServletRequestWrapper(HttpServletRequest request, Map<String, String> additionalHeaders) {
            super(request);
            this.additionalHeaders = additionalHeaders != null ? additionalHeaders : new HashMap<>();
        }

        @Override
        public String getHeader(String name) {
            String additionalValue = additionalHeaders.get(name);
            if (additionalValue != null) {
                return additionalValue;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String additionalValue = additionalHeaders.get(name);
            if (additionalValue != null) {
                List<String> values = new ArrayList<>();
                values.add(additionalValue);

                // Add original values if they exist
                Enumeration<String> originalHeaders = super.getHeaders(name);
                while (originalHeaders.hasMoreElements()) {
                    values.add(originalHeaders.nextElement());
                }

                return Collections.enumeration(values);
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> headerNames = new HashSet<>(additionalHeaders.keySet());

            Enumeration<String> originalNames = super.getHeaderNames();
            while (originalNames.hasMoreElements()) {
                headerNames.add(originalNames.nextElement());
            }

            return Collections.enumeration(headerNames);
        }
    }
}