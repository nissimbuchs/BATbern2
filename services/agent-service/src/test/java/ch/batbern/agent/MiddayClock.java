package ch.batbern.agent;

import ch.batbern.agent.run.RunLimit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The clock for tests that count per day (daily budget, the gate's recipients per day): a ticking
 * clock that starts at 12:00 Europe/Zurich ({@link RunLimit#DAY_ZONE}) of a fixed day, so a test
 * that runs across midnight on the real clock cannot see two days. The clock is a process boundary,
 * so replacing it is within the mock rule. {@code @Import} it on the test class; a
 * {@code @TestConfiguration} is never picked up by component scanning.
 *
 * <p>The day lies in the past: CUMS at the wire stamps grants and tokens with the same clock
 * ({@code CumsAtTheWire.withClock}), and data seeded with fixed instants before it (October 2026)
 * stays on earlier days.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MiddayClock {

    /** The fixed day the clock starts on. */
    public static final LocalDate DAY = LocalDate.of(2026, 11, 18);

    /** 12:00 Europe/Zurich of {@link #DAY}: the clock's first instant. */
    public static final Instant NOON = DAY.atTime(LocalTime.NOON).atZone(RunLimit.DAY_ZONE).toInstant();

    /** How long a test class may run before the clock would leave {@link #DAY}: 11 h before midnight. */
    private static final Duration MARGIN = Duration.ofHours(11);

    @Bean
    @Primary
    public Clock middayClock() {
        Clock system = Clock.systemUTC();
        return Clock.offset(system, Duration.between(system.instant(), NOON));
    }

    /** Positive check that {@code clock} is this clock: on {@link #DAY}, after noon, ticking. */
    public static void assertAnchored(Clock clock) {
        Instant first = clock.instant();
        assertThat(LocalDate.ofInstant(first, RunLimit.DAY_ZONE)).as("the test clock's day").isEqualTo(DAY);
        assertThat(first).as("the test clock started at noon of the fixed day")
                .isBetween(NOON, NOON.plus(MARGIN));
        assertThat(clock.instant()).as("the test clock ticks").isAfterOrEqualTo(first);
    }
}
