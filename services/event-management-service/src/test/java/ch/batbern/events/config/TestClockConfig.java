package ch.batbern.events.config;

import ch.batbern.shared.test.MutableClock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The time is a boundary (#1222): every EMS integration test context gets a {@link MutableClock} in
 * place of {@code ClockConfig}'s system clock. Picked up by the application's component scan, like
 * {@code CumsContractHarnessConfig}. It follows the system clock unless a test sets it; a test that
 * sets it resets it afterwards.
 */
@TestConfiguration
public class TestClockConfig {

    // Not named clock: the test profile allows overriding, and the production bean would win (#1186).
    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock();
    }
}
