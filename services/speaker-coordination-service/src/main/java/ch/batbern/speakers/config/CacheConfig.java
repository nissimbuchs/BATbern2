package ch.batbern.speakers.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache Configuration for Speaker Coordination Service
 * Story 5.4: Speaker Status Management - In-memory caching with Caffeine
 *
 * Cache Strategy:
 * - Uses Caffeine for application-level in-memory caching (no ElastiCache Redis)
 * - Status summary cached for 60 seconds (balance between freshness and performance)
 * - Maximum 1000 entries per cache (sufficient for typical workload)
 *
 * Performance Targets:
 * - Status summary queries: <200ms (AC from story)
 * - Cache hit ratio: >80% for status dashboard queries
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Cache names used in the application
     */
    public static final String STATUS_SUMMARY_CACHE = "statusSummary";
    public static final String STATUS_HISTORY_CACHE = "statusHistory";

    /**
     * Configures Caffeine-based cache manager
     * TTL: 60 seconds for status dashboard data (Story 5.4 requirement)
     * Max size: 1000 entries per cache
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            STATUS_SUMMARY_CACHE,
            STATUS_HISTORY_CACHE
        );

        cacheManager.setCaffeine(caffeineCacheBuilder());

        return cacheManager;
    }

    /**
     * Caffeine cache builder with performance tuning
     */
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)  // TTL: 60s (Story 5.4 requirement)
            .maximumSize(1000)                        // Max entries per cache
            .recordStats();                           // Enable stats for monitoring
    }
}
