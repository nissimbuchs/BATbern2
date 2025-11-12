package ch.batbern.partners.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Test Security Configuration
 * Enables method-level security (@PreAuthorize) for integration tests.
 *
 * Key configuration:
 * - Method-level security: @EnableMethodSecurity to enforce @PreAuthorize
 * - HTTP-level security is handled by SecurityConfig.testFilterChain()
 *
 * Note: In production, authentication is handled at API Gateway level (Story 1.2)
 * Services receive pre-authenticated requests with user context headers.
 */
@TestConfiguration
@EnableMethodSecurity(prePostEnabled = true) // Enable @PreAuthorize support
public class TestSecurityConfig {
    // No SecurityFilterChain bean needed - SecurityConfig.testFilterChain() handles this
}
