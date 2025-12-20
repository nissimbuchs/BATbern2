package ch.batbern.events.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache Configuration for Event Management Service
 * Story 1.15a.1: Events API Consolidation (AC15)
 * Story 5.1: Event Type Definition (eventTypes cache)
 * Story 5.4: Speaker Status Management (status caches)
 *
 * Uses Caffeine in-memory caching for:
 * - Expanded event resources
 * - User API responses (minimize service-to-service calls)
 * - Event type configurations (read-heavy, rarely change)
 * - Speaker status summaries and history (60s TTL)
 *
 * Configuration:
 * - TTL: 15 minutes (events), 1 hour (event types), 60 seconds (speaker status)
 * - Max size: 1000 entries per cache
 * - Cache invalidation on updates
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String EVENT_CACHE = "events";
    public static final String EVENT_WITH_INCLUDES_CACHE = "eventWithIncludes";
    public static final String USER_API_CACHE = "userApiCache";
    public static final String EVENT_TYPES_CACHE = "eventTypes";
    public static final String STATUS_SUMMARY_CACHE = "statusSummary";
    public static final String STATUS_HISTORY_CACHE = "statusHistory";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                EVENT_CACHE,
                EVENT_WITH_INCLUDES_CACHE,
                USER_API_CACHE,
                EVENT_TYPES_CACHE,
                STATUS_SUMMARY_CACHE,
                STATUS_HISTORY_CACHE
        );

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .recordStats());

        return cacheManager;
    }
}
