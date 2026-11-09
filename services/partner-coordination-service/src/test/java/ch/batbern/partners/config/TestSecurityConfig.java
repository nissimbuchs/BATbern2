package ch.batbern.partners.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Method-level security (@PreAuthorize) for the tests that import it.
 *
 * The HTTP chain is the production one, {@code SecurityConfig.productionFilterChain}, in every
 * profile but {@code local} (17.0b3). The permit-all {@code testFilterChain} it used to rely on
 * was deleted.
 */
@TestConfiguration
@EnableMethodSecurity(prePostEnabled = true)
public class TestSecurityConfig {
}
