package ch.batbern.gateway.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Controller for proxying health and info requests to microservices via Service Connect.
 *
 * This enables /services/{serviceName}/health and /services/{serviceName}/info
 * endpoints without requiring deployment-time knowledge of service URLs.
 *
 * Services are discovered at runtime via ECS Service Connect DNS:
 * - http://event-management.batbern.local:8080
 * - http://speaker-coordination.batbern.local:8080
 * - etc.
 */
@Slf4j
@RestController
@RequestMapping("/services")
@RequiredArgsConstructor
public class ServiceHealthController {

    private final RestTemplate restTemplate;

    /**
     * Proxy health check requests to microservices via Service Connect.
     *
     * @param serviceName The service name (e.g., "event-management")
     * @return Health status from the service, or 503 if service is unavailable
     */
    @GetMapping("/{serviceName}/health")
    public ResponseEntity<Map<String, Object>> getServiceHealth(@PathVariable String serviceName) {
        log.debug("Proxying health check request for service: {}", serviceName);

        // Use Service Connect DNS name
        String serviceUrl = String.format("http://%s:8080/actuator/health", serviceName);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> health = restTemplate.getForObject(serviceUrl, Map.class);
            log.debug("Health check successful for service: {}", serviceName);
            return ResponseEntity.ok(health);
        } catch (RestClientException e) {
            log.error("Health check failed for service {}: {}", serviceName, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "status", "DOWN",
                            "service", serviceName,
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * Proxy info requests to microservices via Service Connect.
     *
     * @param serviceName The service name (e.g., "event-management")
     * @return Service info, or 503 if service is unavailable
     */
    @GetMapping("/{serviceName}/info")
    public ResponseEntity<Map<String, Object>> getServiceInfo(@PathVariable String serviceName) {
        log.debug("Proxying info request for service: {}", serviceName);

        // Use Service Connect DNS name
        String serviceUrl = String.format("http://%s:8080/actuator/info", serviceName);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> info = restTemplate.getForObject(serviceUrl, Map.class);
            log.debug("Info request successful for service: {}", serviceName);
            return ResponseEntity.ok(info);
        } catch (RestClientException e) {
            log.error("Info request failed for service {}: {}", serviceName, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "service", serviceName,
                            "error", e.getMessage()
                    ));
        }
    }
}
