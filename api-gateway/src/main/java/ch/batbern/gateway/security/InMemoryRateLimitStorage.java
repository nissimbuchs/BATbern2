package ch.batbern.gateway.security;

import ch.batbern.gateway.util.LogSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
public class InMemoryRateLimitStorage implements RateLimitStorage {

    private final Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    /**
     * Path-specific anonymous counters (T1.2): keyed by clientIp+method+path,
     * with a per-entry configurable window. Separate from the role-based map
     * above so the existing 1-minute role-based logic stays untouched.
     */
    private final Map<String, PathEntry> pathLimitMap = new ConcurrentHashMap<>();

    // Rate limits per role (requests per minute)
    // Must match RateLimiter.ROLE_RATE_LIMITS
    private static final Map<String, Integer> RATE_LIMITS = Map.of(
        "organizer", 1000,
        "speaker", 500,
        "partner", 500,
        "attendee", 200,
        "anonymous", 50
    );

    // Burst limits per role (requests per 10 seconds)
    // Must match RateLimiter.ROLE_BURST_LIMITS
    private static final Map<String, Integer> BURST_LIMITS = Map.of(
        "organizer", 200,
        "speaker", 100,
        "partner", 100,
        "attendee", 50,
        "anonymous", 20
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

    @Override
    public synchronized boolean tryAcquireForPath(String clientIp, String method, String path,
                                                  Integer limit, Duration window) {
        if (clientIp == null || method == null || path == null
                || limit == null || window == null) {
            // Bad input — fail open so the caller's fallback path runs.
            return true;
        }
        String key = clientIp + "|" + method + "|" + path;
        Instant now = Instant.now();
        PathEntry entry = pathLimitMap.get(key);
        if (entry == null || now.isAfter(entry.windowStart.plus(window))) {
            pathLimitMap.put(key, new PathEntry(1, now));
            return true;
        }
        if (entry.count >= limit) {
            log.warn("Per-IP path rate limit reached: {} (count={}, limit={}, window={})",
                    LogSanitizer.sanitize(key), entry.count, limit, window);
            return false;
        }
        entry.count++;
        return true;
    }

    /**
     * Clear all rate limit data (for testing)
     */
    public void clearAll() {
        rateLimitMap.clear();
        pathLimitMap.clear();
        log.debug("Cleared all rate limit data");
    }

    private static class PathEntry {
        int count;
        final Instant windowStart;

        PathEntry(int count, Instant windowStart) {
            this.count = count;
            this.windowStart = windowStart;
        }
    }

    private static class RateLimitEntry {
        private int requestCount;
        private final LocalDateTime timestamp;

        RateLimitEntry(int requestCount, LocalDateTime timestamp) {
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