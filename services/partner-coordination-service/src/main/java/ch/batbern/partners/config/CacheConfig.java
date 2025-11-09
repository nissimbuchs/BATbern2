package ch.batbern.partners.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for Partner Coordination Service.
 *
 * Uses Caffeine for application-level in-memory caching (ADR-001 cost optimization).
 *
 * Cache Strategy:
 * - partners: 10min TTL for partner lookups
 * - companies: 10min TTL for Company Service HTTP responses
 * - users: 10min TTL for User Service HTTP responses
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "partners",         // Partner entity cache
            "companyApiCache",  // Company Service HTTP response cache (15min TTL)
            "userApiCache"      // User Service HTTP response cache (15min TTL)
        );

        cacheManager.setCaffeine(caffeineCacheBuilder());

        return cacheManager;
    }

    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
            .maximumSize(1000)              // Max 1000 entries per cache
            .expireAfterWrite(10, TimeUnit.MINUTES)  // Default TTL: 10 minutes
            .recordStats();                 // Enable cache statistics for monitoring
    }
}
