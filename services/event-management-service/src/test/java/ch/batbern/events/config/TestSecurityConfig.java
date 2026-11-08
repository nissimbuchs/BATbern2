package ch.batbern.events.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Method-level security (@PreAuthorize) for the slice tests that import it.
 *
 * The HTTP chain is the production one, {@link SecurityConfig#filterChain}, in every profile
 * (17.0b3). This class used to replace it with {@code anyRequest().permitAll()}, which let
 * anonymous callers reach the controllers in tests while production answered 401.
 */
@TestConfiguration
@EnableMethodSecurity(prePostEnabled = true)
public class TestSecurityConfig {
}
