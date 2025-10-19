package ch.batbern.companyuser.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive health check controller for ECS health monitoring.
 *
 * Provides three types of health checks:
 * - /actuator/health - Overall service health with component details
 * - /actuator/health/ready - Readiness probe (can handle traffic)
 * - /actuator/health/live - Liveness probe (service is running)
 */
@RestController
@RequestMapping("/actuator")
@Slf4j
public class HealthController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CacheManager cacheManager;

    /**
     * Overall health check with component-level details.
     * Returns health status of database and cache.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("service", "company-user-management-service");
        health.put("timestamp", Instant.now().toString());

        // Check database health
        ComponentHealth databaseHealth = checkDatabase();
        health.put("database", Map.of(
            "status", databaseHealth.isHealthy() ? "UP" : "DOWN",
            "details", databaseHealth.getMessage()
        ));

        // Check cache health
        ComponentHealth cacheHealth = checkCaffeineCache();
        health.put("cache", Map.of(
            "status", cacheHealth.isHealthy() ? "UP" : "DOWN",
            "details", cacheHealth.getMessage()
        ));

        // Overall status - DEGRADED if any component is unhealthy
        String overallStatus = (databaseHealth.isHealthy() && cacheHealth.isHealthy()) ? "UP" : "DEGRADED";
        health.put("status", overallStatus);

        HttpStatus httpStatus = "UP".equals(overallStatus) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(health);
    }

    /**
     * Readiness probe - checks if service can handle requests.
     * Used by ECS to determine if service should receive traffic.
     */
    @GetMapping("/health/ready")
    public ResponseEntity<String> readiness() {
        ComponentHealth databaseHealth = checkDatabase();
        ComponentHealth cacheHealth = checkCaffeineCache();

        if (databaseHealth.isHealthy() && cacheHealth.isHealthy()) {
            return ResponseEntity.ok("READY");
        }

        log.warn("Readiness check failed - Database: {}, Cache: {}",
            databaseHealth.isHealthy(), cacheHealth.isHealthy());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("NOT_READY");
    }

    /**
     * Liveness probe - checks if service is running.
     * Used by ECS to determine if container should be restarted.
     */
    @GetMapping("/health/live")
    public ResponseEntity<String> liveness() {
        // Service is alive if it can respond to requests
        return ResponseEntity.ok("LIVE");
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
            "service", "company-user-management-service",
            "version", "1.0.0",
            "description", "Company & User Management Service for BATbern Platform"
        ));
    }

    /**
     * Check database connectivity.
     */
    private ComponentHealth checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(2)) {
                return ComponentHealth.healthy("Database connection successful");
            }
            return ComponentHealth.unhealthy("Database connection invalid");
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return ComponentHealth.unhealthy("Database connection failed: " + e.getMessage());
        }
    }

    /**
     * Check Caffeine cache availability.
     */
    private ComponentHealth checkCaffeineCache() {
        try {
            if (cacheManager != null && cacheManager.getCacheNames() != null) {
                int cacheCount = cacheManager.getCacheNames().size();
                return ComponentHealth.healthy("Caffeine cache available with " + cacheCount + " caches");
            }
            return ComponentHealth.unhealthy("CacheManager not available");
        } catch (Exception e) {
            log.error("Caffeine cache health check failed", e);
            return ComponentHealth.unhealthy("Cache check failed: " + e.getMessage());
        }
    }

    /**
     * Simple value class for component health status.
     */
    private static class ComponentHealth {
        private final boolean healthy;
        private final String message;

        private ComponentHealth(boolean healthy, String message) {
            this.healthy = healthy;
            this.message = message;
        }

        public static ComponentHealth healthy(String message) {
            return new ComponentHealth(true, message);
        }

        public static ComponentHealth unhealthy(String message) {
            return new ComponentHealth(false, message);
        }

        public boolean isHealthy() {
            return healthy;
        }

        public String getMessage() {
            return message;
        }
    }
}
