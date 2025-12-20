package ch.batbern.speakers.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test Security Configuration for Speaker Coordination Service
 * Story 5.4: Speaker Status Management
 *
 * Enables method-level security (@PreAuthorize) for integration tests.
 *
 * Key configuration:
 * - HTTP-level security: permitAll() to allow @WithMockUser through
 * - Method-level security: @EnableMethodSecurity to enforce @PreAuthorize
 *
 * Note: In production, authentication is handled at API Gateway level
 * Services receive pre-authenticated requests with user context headers.
 */
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // Enable @PreAuthorize support
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll() // Allow all at HTTP level, @PreAuthorize handles method-level auth
            );
        return http.build();
    }
}
