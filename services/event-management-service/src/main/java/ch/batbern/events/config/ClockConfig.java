package ch.batbern.events.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The service's one {@link Clock}: the time is a boundary, so code that asks "what time is it"
 * takes this bean and a test can set it. UTC; code that needs a wall clock converts to the zone
 * it means (quiet hours, #1222).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
