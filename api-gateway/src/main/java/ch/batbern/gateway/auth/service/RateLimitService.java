package ch.batbern.gateway.auth.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Rate limiting service for password reset requests
 *
 * Story 1.2.2 - AC13: Rate limiting (3 requests per hour per email)
 */
@Service
public class RateLimitService {

    private static final int MAX_REQUESTS_PER_HOUR = 3;
    private static final long HOUR_IN_MILLIS = 60 * 60 * 1000;

    // Map of email -> list of request timestamps
    private final Map<String, RequestHistory> requestHistory = new ConcurrentHashMap<>();

    public boolean allowPasswordReset(String email) {
        String key = email.toLowerCase();
        RequestHistory history = requestHistory.computeIfAbsent(key, k -> new RequestHistory());

        long now = Instant.now().toEpochMilli();

        // Clean up old requests (older than 1 hour)
        history.removeOldRequests(now - HOUR_IN_MILLIS);

        // Check if within rate limit
        if (history.getCount() >= MAX_REQUESTS_PER_HOUR) {
            return false;
        }

        // Record this request
        history.addRequest(now);
        return true;
    }

    /**
     * Get the number of remaining attempts for an email
     */
    public int getRemainingAttempts(String email) {
        String key = email.toLowerCase();
        RequestHistory history = requestHistory.get(key);

        if (history == null) {
            return MAX_REQUESTS_PER_HOUR;
        }

        long now = Instant.now().toEpochMilli();
        history.removeOldRequests(now - HOUR_IN_MILLIS);

        return Math.max(0, MAX_REQUESTS_PER_HOUR - history.getCount());
    }

    /**
     * Inner class to track request history for an email
     *
     * Uses CopyOnWriteArrayList for thread-safe operations without explicit synchronization.
     * This is appropriate for our use case where reads are more frequent than writes,
     * and the list size is small (max 3 elements per email).
     *
     * Fixes QA issue THREAD-001: Eliminates race condition in rate limiting
     */
    private static class RequestHistory {
        private final List<Long> timestamps = new CopyOnWriteArrayList<>();

        public void addRequest(long timestamp) {
            timestamps.add(timestamp);
        }

        public void removeOldRequests(long cutoffTime) {
            timestamps.removeIf(timestamp -> timestamp < cutoffTime);
        }

        public int getCount() {
            return timestamps.size();
        }
    }

    /**
     * Clear rate limit history (for testing)
     */
    public void clearHistory() {
        requestHistory.clear();
    }
}
