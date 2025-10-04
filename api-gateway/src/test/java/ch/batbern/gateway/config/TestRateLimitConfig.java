package ch.batbern.gateway.config;

import ch.batbern.gateway.security.RateLimitStorage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test Configuration for Rate Limiting
 *
 * Provides a test-specific RateLimitStorage with higher limits
 * to match test expectations (60 requests per minute).
 */
@TestConfiguration
@Profile("test")
public class TestRateLimitConfig {

    @Bean
    @Primary
    public RateLimitStorage testRateLimitStorage() {
        System.out.println("Creating test RateLimitStorage with 60 req/min limit");
        return new TestRateLimitStorage();
    }

    /**
     * Test-specific RateLimitStorage with 60 req/min limit for all users
     */
    private static class TestRateLimitStorage implements RateLimitStorage {

        private final Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

        // Test rate limit: 60 requests per minute (to match test expectations)
        private static final int TEST_RATE_LIMIT = 60;
        private static final int TEST_BURST_LIMIT = 20;

        @Override
        public int getCurrentRequestCount(String userId, String endpoint, String role) {
            String key = createKey(userId, endpoint);
            RateLimitEntry entry = rateLimitMap.get(key);

            if (entry == null) {
                return 0;
            }

            // Reset if time window has expired
            if (isTimeWindowExpired(entry)) {
                resetRequestCount(userId, endpoint);
                return 0;
            }

            return entry.getRequestCount();
        }

        @Override
        public void incrementRequestCount(String userId, String endpoint, String role) {
            String key = createKey(userId, endpoint);
            RateLimitEntry entry = rateLimitMap.get(key);

            if (entry == null || isTimeWindowExpired(entry)) {
                entry = new RateLimitEntry(1, LocalDateTime.now());
            } else {
                entry.incrementCount();
            }

            rateLimitMap.put(key, entry);
        }

        @Override
        public void resetRequestCount(String userId, String endpoint) {
            String key = createKey(userId, endpoint);
            rateLimitMap.remove(key);
        }

        @Override
        public int getRateLimit(String role, String endpoint) {
            return TEST_RATE_LIMIT; // All users get 60 req/min in tests
        }

        @Override
        public int getBurstLimit(String role) {
            return TEST_BURST_LIMIT;
        }

        private String createKey(String userId, String endpoint) {
            return userId + ":" + endpoint;
        }

        private boolean isTimeWindowExpired(RateLimitEntry entry) {
            // 1-minute time window
            return ChronoUnit.MINUTES.between(entry.getTimestamp(), LocalDateTime.now()) >= 1;
        }

        private static class RateLimitEntry {
            private int requestCount;
            private final LocalDateTime timestamp;

            public RateLimitEntry(int requestCount, LocalDateTime timestamp) {
                this.requestCount = requestCount;
                this.timestamp = timestamp;
            }

            public int getRequestCount() {
                return requestCount;
            }

            public LocalDateTime getTimestamp() {
                return timestamp;
            }

            public void incrementCount() {
                this.requestCount++;
            }
        }
    }
}
