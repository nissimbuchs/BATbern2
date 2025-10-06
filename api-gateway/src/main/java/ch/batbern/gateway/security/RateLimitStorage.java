package ch.batbern.gateway.security;

public interface RateLimitStorage {

    int getCurrentRequestCount(String userId, String endpoint, String role);

    void incrementRequestCount(String userId, String endpoint, String role);

    void resetRequestCount(String userId, String endpoint);

    int getRateLimit(String role, String endpoint);

    int getBurstLimit(String role);

}