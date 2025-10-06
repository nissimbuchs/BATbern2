package ch.batbern.gateway.security;

import ch.batbern.gateway.util.LogSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
public class InMemoryRateLimitStorage implements RateLimitStorage {

    private final Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    // Rate limits per role (requests per minute)
    private static final Map<String, Integer> RATE_LIMITS = Map.of(
        "organizer", 100,
        "speaker", 50,
        "partner", 75,
        "attendee", 20,
        "anonymous", 10
    );

    // Burst limits per role (requests per 10 seconds)
    private static final Map<String, Integer> BURST_LIMITS = Map.of(
        "organizer", 20,
        "speaker", 15,
        "partner", 15,
        "attendee", 10,
        "anonymous", 5
    );

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
        log.debug("Incremented rate limit for key: {} count: {}", key, entry.getRequestCount());
    }

    @Override
    public void resetRequestCount(String userId, String endpoint) {
        String key = createKey(userId, endpoint);
        rateLimitMap.remove(key);
        log.debug("Reset rate limit for key: {}", LogSanitizer.sanitize(key));
    }

    @Override
    public int getRateLimit(String role, String endpoint) {
        return RATE_LIMITS.getOrDefault(role.toLowerCase(), 10);
    }

    @Override
    public int getBurstLimit(String role) {
        return BURST_LIMITS.getOrDefault(role.toLowerCase(), 5);
    }

    private String createKey(String userId, String endpoint) {
        return userId + ":" + endpoint;
    }

    private boolean isTimeWindowExpired(RateLimitEntry entry) {
        // 1-minute time window
        return ChronoUnit.MINUTES.between(entry.getTimestamp(), LocalDateTime.now()) >= 1;
    }

    /**
     * Clear all rate limit data (for testing)
     */
    public void clearAll() {
        rateLimitMap.clear();
        log.debug("Cleared all rate limit data");
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