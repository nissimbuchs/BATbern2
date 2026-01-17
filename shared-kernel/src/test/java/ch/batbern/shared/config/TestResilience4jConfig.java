package ch.batbern.shared.config;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * Test configuration for Resilience4j registries.
 * Provides mock circuit breaker, bulkhead, and retry registries for test environments.
 * NOTE: No @Profile annotation - this config is always active when explicitly imported in @ContextConfiguration
 */
@TestConfiguration
public class TestResilience4jConfig {

    @Bean
    @Primary
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(5))
            .permittedNumberOfCallsInHalfOpenState(3)
            .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);

        // Pre-register the eventBridgePublisher circuit breaker for tests
        registry.circuitBreaker("eventBridgePublisher");

        return registry;
    }

    @Bean
    @Primary
    public BulkheadRegistry bulkheadRegistry() {
        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(10)
            .maxWaitDuration(Duration.ofMillis(100))
            .build();

        BulkheadRegistry registry = BulkheadRegistry.of(config);

        // Pre-register the eventBridgePublisher bulkhead for tests
        registry.bulkhead("eventBridgePublisher");

        return registry;
    }

    @Bean
    @Primary
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(100))
            .build();

        RetryRegistry registry = RetryRegistry.of(config);

        // Pre-register the eventBridgePublisher retry for tests
        registry.retry("eventBridgePublisher");

        return registry;
    }
}
