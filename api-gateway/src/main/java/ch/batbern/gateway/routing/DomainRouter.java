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
import java.util.Map;
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
        // Note: Check specific patterns BEFORE general prefix matches

        // W2.2: Watch pairing (unauthenticated) → company-user-management-service
        // W2.4+: Watch event/arrivals endpoints → event-management-service
        // Must check /api/v1/watch/pair specifically before /api/v1/watch/events fallthrough
        if (cleanPath.equals("/api/v1/watch/pair") || cleanPath.equals("/api/v1/watch/authenticate")) {
            return "company-user-management-service";
        } else if (cleanPath.startsWith("/api/v1/watch")) {
            return "event-management-service";

        // Story 5.4: Speaker status management endpoints go to event-management-service
        // (moved from speaker-coordination-service for Epic 5 architecture alignment)
        } else if (cleanPath.matches("/api/v1/events/[^/]+/speakers/[^/]+/status(/.*)?")
                || cleanPath.matches("/api/v1/events/[^/]+/speakers/status-summary")) {
            return "event-management-service";
        } else if (cleanPath.startsWith("/api/v1/events")
                || cleanPath.startsWith("/api/v1/registrations")
                || cleanPath.startsWith("/api/v1/topics")
                || cleanPath.startsWith("/api/v1/tasks")
                || cleanPath.startsWith("/api/v1/notifications")
                || cleanPath.startsWith("/api/v1/materials") // Story 5.9: Materials upload
                || cleanPath.startsWith("/api/v1/speaker-portal") // Story 6.2a: Speaker portal
                || cleanPath.startsWith("/api/v1/sessions") // GlobalSessionController lives in EMS
                || cleanPath.startsWith("/api/v1/e2e-test") // Story 6.3: E2E test endpoints
                || cleanPath.startsWith("/api/v1/email-templates") // Story 10.2: Email template management
                || cleanPath.startsWith("/api/v1/analytics") // Story 10.5: Analytics dashboard
                || cleanPath.startsWith("/api/v1/newsletter") // Story 10.7: Newsletter
                || cleanPath.startsWith("/api/v1/ai") // Story 10.16: AI content generation
                || cleanPath.equals("/api/v1/public/settings/features")) { // Story 10.16: Feature flags
            return "event-management-service";
        } else if (cleanPath.startsWith("/api/v1/partners")
                || cleanPath.startsWith("/api/v1/partner-meetings")) {
            return "partner-coordination-service";
        } else if (cleanPath.startsWith("/api/v1/content")) {
            return "attendee-experience-service";
        } else if (cleanPath.startsWith("/api/v1/companies")
                || cleanPath.startsWith("/api/v1/users")
                || cleanPath.startsWith("/api/v1/logos")
                || cleanPath.startsWith("/api/v1/public")
                || cleanPath.startsWith("/api/v1/settings")) { // Story 10.8a: Presentation settings
            return "company-user-management-service";
        } else if (cleanPath.startsWith("/api/v1/admin/test-fixtures/cums")) {
            // Bruno test-fixture cleanup endpoint per service (PR 1 staging-hardening).
            // Per-service paths because /api/v1/admin generically falls through to EMS below
            // and cleanup needs blast-radius isolation per docs/plans/bruno-staging-hardening.md §B2.
            return "company-user-management-service";
        } else if (cleanPath.startsWith("/api/v1/admin/test-fixtures/ems")) {
            return "event-management-service";
        } else if (cleanPath.startsWith("/api/v1/admin/test-fixtures/pcs")) {
            return "partner-coordination-service";
        } else if (cleanPath.startsWith("/api/v1/admin")) { // Admin endpoints (e.g. AdminSettingsController)
            return "event-management-service";
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

        // NOTE: Cache-Control/Pragma/Expires intentionally NOT stripped here.
        // The previous "will be added by SecurityHeadersFilter" comment was wrong —
        // SecurityHeadersHandler exists but is never wired in as an interceptor, so
        // stripping these headers caused Spring Security's default
        // `no-cache, no-store, max-age=0, must-revalidate` to apply to every response,
        // overriding the controller's explicit `cachePublic + 24h` directive on
        // `/api/v1/public/users/*` (caught by Bruno test users-api/20). Controllers
        // (e.g. PublicUserController) set Cache-Control explicitly when they want
        // a caching directive; when they don't, Spring Security's default still
        // applies — which is the correct behaviour for authenticated endpoints.

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
    public CompletableFuture<ResponseEntity<byte[]>> routeRequest(String targetService, HttpServletRequest request) {
        final String rawUri = request.getRequestURI();
        String method = request.getMethod();

        // Normalize trailing slashes: /api/v1/events/ → /api/v1/events to prevent downstream 500 errors
        final String requestUri = (rawUri.length() > 1 && rawUri.endsWith("/"))
                ? rawUri.substring(0, rawUri.length() - 1)
                : rawUri;
        if (!requestUri.equals(rawUri)) {
            log.debug("Normalized trailing slash: {} -> {}", rawUri, requestUri);
        }

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

                // Construct target URI preserving the path's original wire encoding.
                // getRequestURI() returns the URL-encoded path (e.g. `%40` for `@`);
                // UriComponentsBuilder.fromUriString(...).build() defaults to encoded=false
                // and re-encodes percent signs (`%40` → `%2540`), which Spring Security's
                // StrictHttpFirewall on the downstream service then rejects as
                // "potentially malicious String '%25'" (caught by Bruno test
                // users-api/17-delete-additional-email).
                //
                // Path: keep getRequestURI() bytes verbatim.
                // Query params: getParameterMap() returns DECODED values, so we still need
                // UriComponentsBuilder's RFC 3986 query encoding (verified by the JSON-in-
                // query-string test below).
                Map<String, String[]> params = request.getParameterMap();
                String encodedQuery = null;
                if (!params.isEmpty()) {
                    UriComponentsBuilder queryBuilder = UriComponentsBuilder.newInstance();
                    params.forEach((key, values) -> {
                        for (String value : values) {
                            queryBuilder.queryParam(key, value);
                        }
                    });
                    // .encode() converts decoded queryParam values into their RFC 3986
                    // wire form ({ → %7B, " → %22, etc.) so the assembled URI parses.
                    encodedQuery = queryBuilder.build().encode().getQuery();
                }
                URI targetUri = URI.create(serviceUrl + requestUri
                        + (encodedQuery != null ? "?" + encodedQuery : ""));

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
                ResponseEntity<byte[]> response = restTemplate.exchange(
                    targetUri,
                    HttpMethod.valueOf(method),
                    entity,
                    byte[].class
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
                    .body(e.getResponseBodyAsByteArray());

            } catch (Exception e) {
                // Handle unexpected errors
                log.error("Error routing request to {}: {}", targetService, e.getMessage(), e);
                throw new RoutingException("Failed to route request to " + targetService + ": " + e.getMessage(), e);
            }
        });
    }
}