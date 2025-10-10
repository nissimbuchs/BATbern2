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

    @Value("${services.company-management.url:http://localhost:8085}")
    private String companyManagementUrl;

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
        if (cleanPath.startsWith("/api/v1/events")) {
            return "event-management-service";
        } else if (cleanPath.startsWith("/api/v1/speakers")) {
            return "speaker-coordination-service";
        } else if (cleanPath.startsWith("/api/v1/partners")) {
            return "partner-coordination-service";
        } else if (cleanPath.startsWith("/api/v1/content")) {
            return "attendee-experience-service";
        } else if (cleanPath.startsWith("/api/v1/companies")) {
            return "company-management-service";
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
            case "company-management-service" -> companyManagementUrl;
            default -> throw new RoutingException("Unknown target service: " + targetService);
        };
    }

    /**
     * Routes the incoming HTTP request to the target microservice.
     * Forwards all headers (except Host), query parameters, and request body.
     */
    public CompletableFuture<ResponseEntity<String>> routeRequest(String targetService, HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        String method = request.getMethod();

        log.info("Routing {} request to service: {} for path: {}", method, targetService, requestUri);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get target service URL
                String serviceUrl = getServiceUrl(targetService);

                // Build full target URL with query parameters
                String targetUrl = serviceUrl + requestUri;
                if (queryString != null && !queryString.isEmpty()) {
                    targetUrl += "?" + queryString;
                }

                // Copy headers from original request (excluding Host header)
                HttpHeaders headers = new HttpHeaders();
                Enumeration<String> headerNames = request.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    if (!"host".equalsIgnoreCase(headerName)) {
                        headers.put(headerName, Collections.list(request.getHeaders(headerName)));
                    }
                }

                // Read request body if present
                String requestBody = null;
                if (request.getContentLength() > 0) {
                    try {
                        requestBody = request.getReader().lines()
                            .reduce("", (accumulator, actual) -> accumulator + actual);
                    } catch (Exception e) {
                        log.warn("Failed to read request body: {}", e.getMessage());
                    }
                }

                // Create HTTP entity with headers and body
                HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

                // Forward request to target service
                log.debug("Forwarding {} request to: {}", method, targetUrl);
                ResponseEntity<String> response = restTemplate.exchange(
                    targetUrl,
                    HttpMethod.valueOf(method),
                    entity,
                    String.class
                );

                log.info("Received response from {}: status={}", targetService, response.getStatusCode());
                return response;

            } catch (HttpStatusCodeException e) {
                // Forward error responses from downstream services
                log.warn("Downstream service {} returned error: {} - {}",
                    targetService, e.getStatusCode(), e.getResponseBodyAsString());
                return ResponseEntity
                    .status(e.getStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsString());

            } catch (Exception e) {
                // Handle unexpected errors
                log.error("Error routing request to {}: {}", targetService, e.getMessage(), e);
                throw new RoutingException("Failed to route request to " + targetService + ": " + e.getMessage(), e);
            }
        });
    }
}