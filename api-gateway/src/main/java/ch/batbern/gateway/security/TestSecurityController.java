package ch.batbern.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test Controller for Security Integration Testing
 *
 * Only active in test profile - provides endpoints for security feature testing:
 * - Rate limiting tests
 * - Security headers tests
 * - Health checks
 */
@RestController
@RequestMapping("/api/v1")
@Profile("test")
@Slf4j
public class TestSecurityController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("Health check endpoint called");
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "API Gateway is healthy"
        ));
    }

    @GetMapping("/test-rate-limit")
    public ResponseEntity<Map<String, Object>> testRateLimit() {
        log.debug("Rate limit test endpoint called");
        return ResponseEntity.ok(Map.of(
                "message", "Rate limit test endpoint",
                "timestamp", System.currentTimeMillis()
        ));
    }

    @GetMapping("/test-rate-limit-allowed")
    public ResponseEntity<Map<String, Object>> testRateLimitAllowed() {
        log.debug("Rate limit allowed test endpoint called");
        return ResponseEntity.ok(Map.of(
                "message", "Rate limit allowed test endpoint",
                "timestamp", System.currentTimeMillis()
        ));
    }

    @GetMapping("/test-rate-limit-headers")
    public ResponseEntity<Map<String, Object>> testRateLimitHeaders() {
        log.debug("Rate limit headers test endpoint called");
        return ResponseEntity.ok(Map.of(
                "message", "Rate limit headers test endpoint",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
