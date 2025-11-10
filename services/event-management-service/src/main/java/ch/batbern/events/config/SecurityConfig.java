package ch.batbern.events.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Security configuration for the Event Management Service
 * Configures role-based access control for event management endpoints with JWT authentication
 *
 * Method Security Strategy:
 * - Production/Staging: @EnableMethodSecurity enforces @PreAuthorize annotations
 * - Local Development: Method security disabled (trusted localhost environment, mirrors AWS VPC security)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    /**
     * Enable method-level security for production, staging, and test environments
     * Enforces @PreAuthorize annotations on controller methods
     */
    @Configuration
    @EnableMethodSecurity(prePostEnabled = true)
    @Profile("!local")
    static class ProductionMethodSecurityConfig {
    }

    /**
     * Production security filter chain with JWT authentication
     * Note: Tests use TestSecurityConfig instead
     *
     * Story 4.1.3: Public endpoints for event discovery
     * QA Fix (SEC-001): Rate limiting filter auto-registered via @Component @Order
     */
    @Bean
    @Profile("!test")
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable for stateless API
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Public endpoints - no authentication required
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // Story 4.1.3: Public event discovery endpoints
                .requestMatchers(HttpMethod.GET, "/api/v1/events/current").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions/*").permitAll()

                // Story 1.15a.1b: Public speaker list endpoint (GET only, POST/PUT/DELETE require ORGANIZER)
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions/*/speakers").permitAll()

                // Story 2.2a: Public anonymous registration endpoints (ADR-005)
                .requestMatchers(HttpMethod.POST, "/api/v1/events/*/registrations").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*/registrations/*").permitAll()
                // Email confirmation endpoint (no auth required, token-protected)
                .requestMatchers(HttpMethod.POST, "/api/v1/registrations/confirm").permitAll()

                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * JWT decoder for AWS Cognito tokens
     */
    @Bean
    @Profile("!test")
    public JwtDecoder jwtDecoder() {
        if (jwkSetUri == null || jwkSetUri.isEmpty()) {
            throw new IllegalArgumentException("JWT JWK Set URI must be configured");
        }
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    /**
     * JWT Authentication Converter to extract roles from custom:role claim
     * Maps custom:role claim (comma-separated string) to Spring Security ROLE_ authorities
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new CustomRolesToAuthoritiesConverter());
        return converter;
    }

    /**
     * Converter to extract custom:role claim and map to Spring Security authorities
     *
     * Roles are stored in PostgreSQL and synced to Cognito custom:role attribute
     * Format: comma-separated string (e.g., "ORGANIZER,SPEAKER")
     * Requires ROLE_ prefix for Spring Security @PreAuthorize annotations
     */
    private static class CustomRolesToAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            // Extract roles from custom:role claim (comma-separated string)
            String rolesString = jwt.getClaimAsString("custom:role");

            if (rolesString == null || rolesString.isEmpty()) {
                return Collections.emptyList();
            }

            // Split comma-separated roles and map to ROLE_ authorities
            return Arrays.stream(rolesString.split(","))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()))
                .collect(Collectors.toList());
        }
    }
}
