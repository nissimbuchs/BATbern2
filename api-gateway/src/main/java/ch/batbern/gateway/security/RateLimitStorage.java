package ch.batbern.gateway.security;

import java.time.Duration;

public interface RateLimitStorage {

    int getCurrentRequestCount(String userId, String endpoint, String role);

    void incrementRequestCount(String userId, String endpoint, String role);

    void resetRequestCount(String userId, String endpoint);

    int getRateLimit(String role, String endpoint);

    int getBurstLimit(String role);

    /**
     * Path-specific rate limit for anonymous requests, keyed by client IP +
     * method + path. Used for SES-triggering POST endpoints (registration,
     * newsletter, deregister) where the global anon limit is too coarse.
     *
     * Atomic check-and-increment: returns true if the call is within the
     * configured limit AND increments the counter; returns false if the limit
     * has already been reached (does NOT increment).
     *
     * @param clientIp source IP (X-Forwarded-For first hop, or remote addr)
     * @param method   HTTP method (POST)
     * @param path     request URI
     * @param limit    max requests per window for this (ip, method, path)
     * @param window   sliding-window duration
     * @return true if request is allowed (and counter incremented), false if blocked
     */
    boolean tryAcquireForPath(String clientIp, String method, String path,
                              Integer limit, Duration window);
}