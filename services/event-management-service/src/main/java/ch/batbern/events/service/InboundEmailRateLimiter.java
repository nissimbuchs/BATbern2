package ch.batbern.events.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter for inbound email processing (Story 10.17 — AC6).
 *
 * Prevents abuse by discarding excess messages from the same sender.
 * Limit: 10 messages per sender address per hour (fixed window — counter resets 1 hour after first message).
 *
 * Caffeine is already in the project ({@code com.github.ben-manes.caffeine:caffeine:3.2.3}).
 */
@Component
@Slf4j
public class InboundEmailRateLimiter {

    private static final int MAX_PER_HOUR = 10;

    private final LoadingCache<String, AtomicInteger> senderCount = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build(key -> new AtomicInteger(0));

    /**
     * Returns {@code true} if the sender is within the rate limit; {@code false} if exceeded.
     * Increments the counter for the sender on every call.
     */
    public boolean isAllowed(String senderEmail) {
        AtomicInteger count = senderCount.get(senderEmail);
        int current = count.incrementAndGet();
        if (current > MAX_PER_HOUR) {
            log.warn("Rate limit exceeded for inbound email sender: {}***",
                    senderEmail.substring(0, Math.min(5, senderEmail.length())));
            return false;
        }
        return true;
    }
}
