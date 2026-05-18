package ch.batbern.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InMemoryRateLimitStorage#tryAcquireForPath} —
 * T1.2 per-IP path-specific anonymous limits.
 */
@DisplayName("InMemoryRateLimitStorage.tryAcquireForPath")
class InMemoryRateLimitStorageTest {

    private InMemoryRateLimitStorage storage;

    @BeforeEach
    void setUp() {
        storage = new InMemoryRateLimitStorage();
    }

    @Test
    @DisplayName("allows up to the configured limit, blocks the next one")
    void should_allowUpToLimit_thenBlock() {
        for (int i = 0; i < 5; i++) {
            assertThat(storage.tryAcquireForPath("203.0.113.1", "POST",
                    "/api/v1/newsletter/subscribe", 5, Duration.ofMinutes(5)))
                    .as("call %d of 5", i + 1)
                    .isTrue();
        }
        // 6th call → blocked
        assertThat(storage.tryAcquireForPath("203.0.113.1", "POST",
                "/api/v1/newsletter/subscribe", 5, Duration.ofMinutes(5))).isFalse();
    }

    @Test
    @DisplayName("counters are isolated per IP")
    void should_isolateCountersPerIp() {
        // IP-A burns the limit
        for (int i = 0; i < 5; i++) {
            storage.tryAcquireForPath("203.0.113.1", "POST",
                    "/api/v1/newsletter/subscribe", 5, Duration.ofMinutes(5));
        }
        assertThat(storage.tryAcquireForPath("203.0.113.1", "POST",
                "/api/v1/newsletter/subscribe", 5, Duration.ofMinutes(5))).isFalse();

        // IP-B is unaffected
        assertThat(storage.tryAcquireForPath("203.0.113.2", "POST",
                "/api/v1/newsletter/subscribe", 5, Duration.ofMinutes(5))).isTrue();
    }

    @Test
    @DisplayName("counters are isolated per path")
    void should_isolateCountersPerPath() {
        for (int i = 0; i < 5; i++) {
            storage.tryAcquireForPath("203.0.113.1", "POST",
                    "/api/v1/newsletter/subscribe", 5, Duration.ofMinutes(5));
        }
        assertThat(storage.tryAcquireForPath("203.0.113.1", "POST",
                "/api/v1/newsletter/subscribe", 5, Duration.ofMinutes(5))).isFalse();

        // Different path → fresh counter
        assertThat(storage.tryAcquireForPath("203.0.113.1", "POST",
                "/api/v1/events/BATbern59/registrations", 5, Duration.ofMinutes(5))).isTrue();
    }

    @Test
    @DisplayName("counter resets when the window has elapsed")
    void should_resetAfterWindow() throws InterruptedException {
        // Use a 50ms window to make the test fast and deterministic.
        Duration tinyWindow = Duration.ofMillis(50);

        for (int i = 0; i < 3; i++) {
            storage.tryAcquireForPath("203.0.113.1", "POST",
                    "/api/v1/registrations/deregister/by-email", 3, tinyWindow);
        }
        assertThat(storage.tryAcquireForPath("203.0.113.1", "POST",
                "/api/v1/registrations/deregister/by-email", 3, tinyWindow)).isFalse();

        Thread.sleep(80);

        assertThat(storage.tryAcquireForPath("203.0.113.1", "POST",
                "/api/v1/registrations/deregister/by-email", 3, tinyWindow)).isTrue();
    }

    @Test
    @DisplayName("fails open on null inputs (so caller fallback runs)")
    void should_failOpen_onNullInputs() {
        assertThat(storage.tryAcquireForPath(null, "POST", "/path", 5, Duration.ofMinutes(1))).isTrue();
        assertThat(storage.tryAcquireForPath("ip", null, "/path", 5, Duration.ofMinutes(1))).isTrue();
        assertThat(storage.tryAcquireForPath("ip", "POST", null, 5, Duration.ofMinutes(1))).isTrue();
        assertThat(storage.tryAcquireForPath("ip", "POST", "/path", null, Duration.ofMinutes(1))).isTrue();
        assertThat(storage.tryAcquireForPath("ip", "POST", "/path", 5, null)).isTrue();
    }

    @Test
    @DisplayName("clearAll wipes path-specific counters too")
    void should_clearPathCounters_when_clearAll() {
        for (int i = 0; i < 5; i++) {
            storage.tryAcquireForPath("203.0.113.1", "POST",
                    "/api/v1/newsletter/subscribe", 5, Duration.ofMinutes(5));
        }
        storage.clearAll();
        assertThat(storage.tryAcquireForPath("203.0.113.1", "POST",
                "/api/v1/newsletter/subscribe", 5, Duration.ofMinutes(5))).isTrue();
    }
}
