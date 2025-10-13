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
 *
 * Uses Caffeine in-memory caching for expanded event resources.
 * - TTL: 15 minutes
 * - Max size: 1000 entries
 * - Cache invalidation on event updates
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String EVENT_CACHE = "events";
    public static final String EVENT_WITH_INCLUDES_CACHE = "eventWithIncludes";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                EVENT_CACHE,
                EVENT_WITH_INCLUDES_CACHE
        );

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .recordStats());

        return cacheManager;
    }
}
