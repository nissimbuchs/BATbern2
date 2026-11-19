package ch.batbern.shared.test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A {@link Clock} an integration test can set (#1222): the service's {@code Clock} bean in tests.
 * It follows the system clock until {@link #setInstant} fixes it; {@link #reset} goes back to the
 * system clock. The context is shared between test classes, so a test that sets it resets it in
 * {@code @AfterEach}.
 */
public final class MutableClock extends Clock {

    private volatile Instant fixed;

    /** Fixes the time at {@code instant} until {@link #reset}. */
    public void setInstant(Instant instant) {
        this.fixed = instant;
    }

    /** Follows the system clock again. */
    public void reset() {
        this.fixed = null;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        MutableClock self = this;
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId other) {
                return self.withZone(other);
            }

            @Override
            public Instant instant() {
                return self.instant();
            }
        };
    }

    @Override
    public Instant instant() {
        Instant at = fixed;
        return at != null ? at : Instant.now();
    }
}
