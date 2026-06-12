package ch.batbern.events.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-(event, client-IP) rate limiter for anonymous "Thank the Organizers" submissions
 * (Story 7.4, Resolved Decision #3 — AC3/AC5).
 *
 * <p>The gateway already buckets ALL anonymous traffic by IP globally; this adds a small,
 * targeted per-event cap so a single IP cannot inflate one event's clap counter. Logged-in
 * thank-yous are deduped to one row per (event, username) and so are NOT rate-limited here.
 *
 * <p>Fixed window: the counter for an (event, IP) pair resets one hour after its first
 * submission — same shape as {@link InboundEmailRateLimiter}. In-memory per service instance
 * (no Redis, per the project's Caffeine-only caching stance); at BATbern's scale a per-instance
 * cap is sufficient abuse mitigation and avoids cross-instance coordination.
 */
@Component
@Slf4j
public class ThanksRateLimiter {

    /** "A handful per event per IP" (Resolved Decision #3). */
    static final int MAX_PER_EVENT_PER_IP_PER_HOUR = 5;

    private final LoadingCache<String, AtomicInteger> counts = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build(key -> new AtomicInteger(0));

    /**
     * Returns {@code true} if this (event, IP) pair is within the cap; {@code false} if exceeded.
     * Increments the internal counter on every call — call this ONCE per anonymous submission
     * attempt, before inserting the row.
     */
    public boolean isAllowed(UUID eventId, String clientIp) {
        String key = eventId + ":" + (clientIp == null ? "unknown" : clientIp);
        int current = counts.get(key).incrementAndGet();
        if (current > MAX_PER_EVENT_PER_IP_PER_HOUR) {
            log.warn("Thanks rate limit exceeded for event {} from IP {}***", eventId,
                    clientIp == null ? "unknown" : clientIp.substring(0, Math.min(7, clientIp.length())));
            return false;
        }
        return true;
    }
}
