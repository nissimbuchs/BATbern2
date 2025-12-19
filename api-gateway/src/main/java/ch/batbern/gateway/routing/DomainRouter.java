package ch.batbern.gateway.routing;

import ch.batbern.gateway.routing.exception.RoutingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.UriComponentsBuilder;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainRouter {

    private final RestTemplate restTemplate;

    @Value("${services.event-management.url:http://localhost:8081}")
    private String eventManagementUrl;

    @Value("${services.speaker-coordination.url:http://localhost:8082}")
    private String speakerCoordinationUrl;

    @Value("${services.partner-coordination.url:http://localhost:8083}")
    private String partnerCoordinationUrl;

    @Value("${services.attendee-experience.url:http://localhost:8084}")
    private String attendeeExperienceUrl;

    @Value("${services.company-user-management.url:http://localhost:8085}")
    private String companyUserManagementUrl;

    /**
     * Determines the target microservice based on the request path.
     * Uses path-based routing: /api/v1/{domain} → {domain}-service
     */
    public String determineTargetService(String requestPath) {
        if (requestPath == null || requestPath.trim().isEmpty()) {
            throw new RoutingException("Request path cannot be null or empty");
        }

        // Remove query parameters for routing decisions
        String cleanPath = requestPath.split("\\?")[0];

        log.debug("Determining target service for path: {}", cleanPath);

        // Route based on path patterns - /api/v1/{domain}
        // Note: Check speaker-specific endpoints BEFORE general events pattern
        // Story 5.4: Speaker status management endpoints go to event-management-service
        // (moved from speaker-coordination-service for Epic 5 architecture alignment)
        if (cleanPath.matches("/api/v1/events/[^/]+/speakers/[^/]+/status(/.*)?")
                || cleanPath.matches("/api/v1/events/[^/]+/speakers/status-summary")) {
            return "event-management-service";
        } else if (cleanPath.startsWith("/api/v1/events")
                || cleanPath.startsWith("/api/v1/registrations")
                || cleanPath.startsWith("/api/v1/topics")) {
            return "event-management-service";
        } else if (cleanPath.startsWith("/api/v1/speakers")) {
            return "speaker-coordination-service";
        } else if (cleanPath.startsWith("/api/v1/partners")) {
            return "partner-coordination-service";
        } else if (cleanPath.startsWith("/api/v1/content")) {
            return "attendee-experience-service";
        } else if (cleanPath.startsWith("/api/v1/companies")
                || cleanPath.startsWith("/api/v1/users")
                || cleanPath.startsWith("/api/v1/logos")) {
            return "company-user-management-service";
        } else {
            throw new RoutingException("No route found for path: " + cleanPath);
        }
    }

    /**
     * Gets the service URL for a given target service name.
     */
    private String getServiceUrl(String targetService) {
        return switch (targetService) {
            case "event-management-service" -> eventManagementUrl;
            case "speaker-coordination-service" -> speakerCoordinationUrl;
            case "partner-coordination-service" -> partnerCoordinationUrl;
            case "attendee-experience-service" -> attendeeExperienceUrl;
            case "company-user-management-service" -> companyUserManagementUrl;
            default -> throw new RoutingException("Unknown target service: " + targetService);
        };
    }

    /**
     * Removes security headers from backend response to prevent duplication.
     * The API Gateway's SecurityHeadersFilter will add these headers.
     */
    private HttpHeaders removeSecurityHeaders(HttpHeaders headers) {
        if (headers == null) {
            return new HttpHeaders();
        }

        HttpHeaders cleaned = new HttpHeaders();
        cleaned.putAll(headers);

        // Remove security headers that will be added by SecurityHeadersFilter
        cleaned.remove("Content-Security-Policy");
        cleaned.remove("Strict-Transport-Security");
        cleaned.remove("X-Frame-Options");
        cleaned.remove("X-Content-Type-Options");
        cleaned.remove("X-XSS-Protection");
        cleaned.remove("Referrer-Policy");
        cleaned.remove("Permissions-Policy");

        // Remove cache control headers that will be added by SecurityHeadersFilter
        cleaned.remove("Cache-Control");
        cleaned.remove("Pragma");
        cleaned.remove("Expires");

        // Remove transfer-encoding to prevent chunked encoding issues
        // Spring will set Content-Length automatically
        cleaned.remove("Transfer-Encoding");
        cleaned.remove("transfer-encoding");

        return cleaned;
    }

    /**
     * Routes the incoming HTTP request to the target microservice.
     * Forwards all headers (except Host), query parameters, and request body.
     */
    public CompletableFuture<ResponseEntity<String>> routeRequest(String targetService, HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String method = request.getMethod();

        log.info("Routing {} request to service: {} for path: {}", method, targetService, requestUri);

        // Read request body BEFORE async execution (input stream can only be read once)
        String requestBody = null;
        try {
            if (request.getContentLength() > 0) {
                requestBody = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
                log.debug("Read request body: {} bytes", requestBody.length());
            }
        } catch (IOException e) {
            log.error("Failed to read request body: {}", e.getMessage());
            throw new RoutingException("Failed to read request body: " + e.getMessage(), e);
        }

        // Capture body in final variable for use in lambda
        final String finalRequestBody = requestBody;

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get target service URL
                String serviceUrl = getServiceUrl(targetService);

                // Build URI using UriComponentsBuilder to properly handle query parameters
                // This prevents double-encoding and URI template variable expansion issues
                UriComponentsBuilder uriBuilder = UriComponentsBuilder
                        .fromUriString(serviceUrl + requestUri);

                // Add query parameters from request (already decoded by servlet container)
                // UriComponentsBuilder will encode them properly
                request.getParameterMap().forEach((key, values) -> {
                    for (String value : values) {
                        uriBuilder.queryParam(key, value);
                    }
                });

                // build() encodes the parameters, toUri() creates the URI object
                URI targetUri = uriBuilder.build().toUri();

                // Copy headers from original request (excluding Host header)
                HttpHeaders headers = new HttpHeaders();
                Enumeration<String> headerNames = request.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    if (!"host".equalsIgnoreCase(headerName)) {
                        headers.put(headerName, Collections.list(request.getHeaders(headerName)));
                    }
                }

                // Create HTTP entity with headers and body
                HttpEntity<String> entity = new HttpEntity<>(finalRequestBody, headers);

                // Forward request to target service using URI (not String) to avoid template expansion
                log.debug("Forwarding {} request to: {} (body: {} bytes)",
                    method, targetUri, finalRequestBody != null ? finalRequestBody.length() : 0);
                ResponseEntity<String> response = restTemplate.exchange(
                    targetUri,
                    HttpMethod.valueOf(method),
                    entity,
                    String.class
                );

                log.info("Received response from {}: status={}", targetService, response.getStatusCode());

                // Remove security headers from backend response to prevent duplication
                // API Gateway's SecurityHeadersFilter will add these headers
                HttpHeaders cleanedHeaders = removeSecurityHeaders(response.getHeaders());

                return ResponseEntity
                    .status(response.getStatusCode())
                    .headers(cleanedHeaders)
                    .body(response.getBody());

            } catch (HttpStatusCodeException e) {
                // Forward error responses from downstream services
                log.warn("Downstream service {} returned error: {} - {}",
                    targetService, e.getStatusCode(), e.getResponseBodyAsString());

                // Remove security headers from error responses too
                HttpHeaders cleanedHeaders = removeSecurityHeaders(e.getResponseHeaders());

                return ResponseEntity
                    .status(e.getStatusCode())
                    .headers(cleanedHeaders)
                    .body(e.getResponseBodyAsString());

            } catch (Exception e) {
                // Handle unexpected errors
                log.error("Error routing request to {}: {}", targetService, e.getMessage(), e);
                throw new RoutingException("Failed to route request to " + targetService + ": " + e.getMessage(), e);
            }
        });
    }
}