package ch.batbern.partners.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the Partner Coordination Service
 *
 * Method Security Strategy:
 * - Production/Staging: JWT authentication enforced via API Gateway
 * - Local Development: All requests permitted (trusted localhost environment)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Local development security filter chain
     * All requests permitted BUT JWT tokens are still parsed for service-to-service propagation
     * This mirrors AWS VPC security pattern (network isolation in AWS = localhost trust in local dev)
     * while allowing JWT propagation to downstream services
     */
    @Bean
    @Profile("local")
    public SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .anyRequest().permitAll() // Local dev: trust all inter-service calls (localhost isolation)
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {})); // Parse JWT for token propagation

        return http.build();
    }

    /**
     * Production security filter chain with JWT authentication
     * Actuator endpoints are public for health checks
     * Public partner list endpoint for homepage showcase
     */
    @Bean
    @Profile("!local & !test")
    public SecurityFilterChain productionFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Public partner showcase endpoint (GET only for homepage display)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/partners").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }

    /**
     * Test security filter chain
     * Permits all requests for integration testing
     */
    @Bean
    @Profile("test")
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .anyRequest().permitAll()
            );

        return http.build();
    }
}
