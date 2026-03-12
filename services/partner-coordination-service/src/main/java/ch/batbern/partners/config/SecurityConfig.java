package ch.batbern.partners.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
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
 * Security configuration for the Partner Coordination Service.
 *
 * Story 8.1: Added @EnableMethodSecurity for @PreAuthorize support (AC6).
 * Added JwtAuthenticationConverter to extract roles from custom:role claim.
 *
 * Method Security Strategy:
 * - Production/Staging: @EnableMethodSecurity enforces @PreAuthorize annotations
 * - Local Development: Method security disabled (trusted localhost environment)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    /**
     * Enable method-level security for non-local environments.
     * Enforces @PreAuthorize annotations on controller methods (AC6).
     */
    @Configuration
    @EnableMethodSecurity(prePostEnabled = true)
    @Profile("!local")
    static class ProductionMethodSecurityConfig {
    }

    /**
     * Local development security filter chain.
     * All requests permitted BUT JWT tokens are still parsed for service-to-service propagation.
     */
    @Bean
    @Profile("local")
    public SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Production security filter chain with JWT authentication.
     * Actuator endpoints are public for health checks.
     * Public partner list endpoint for homepage showcase.
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
                // Story 10.27: VPC-internal RSVP endpoint (no JWT — SQS async context)
                .requestMatchers("/internal/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/partners/me").hasRole("PARTNER")
                .requestMatchers(HttpMethod.GET, "/api/v1/partners").permitAll()
                // Story 8.2: Partner Topic Suggestions & Voting (AC6 — role-based access)
                .requestMatchers(HttpMethod.GET, "/api/v1/partners/topics").hasAnyRole("PARTNER", "ORGANIZER")
                .requestMatchers(HttpMethod.POST, "/api/v1/partners/topics").hasAnyRole("PARTNER", "ORGANIZER")
                .requestMatchers(HttpMethod.POST, "/api/v1/partners/topics/*/vote").hasRole("PARTNER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/partners/topics/*/vote").hasRole("PARTNER")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/partners/topics/*").hasAnyRole("PARTNER", "ORGANIZER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/partners/topics/*").hasAnyRole("PARTNER", "ORGANIZER")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/partners/topics/*/status").hasRole("ORGANIZER")
                // Story 8.3: Partner Meeting Coordination (AC6 — ORGANIZER only)
                .requestMatchers("/api/v1/partner-meetings/**").hasRole("ORGANIZER")
                // Story 8.4: Partner Notes (ORGANIZER only — partners must not see notes)
                .requestMatchers("/api/v1/partners/*/notes/**").hasRole("ORGANIZER")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .decoder(jwtDecoder())
                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Test security filter chain.
     * Permits all requests for integration testing.
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

    /**
     * JWT decoder for AWS Cognito tokens.
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
     * JWT Authentication Converter to extract roles from custom:role claim.
     * Maps custom:role claim (comma-separated string) to Spring Security ROLE_ authorities.
     * Required for @PreAuthorize("hasRole('PARTNER')") to work (AC6).
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new CustomRolesToAuthoritiesConverter());
        return converter;
    }

    /**
     * Extracts custom:role claim and maps to Spring Security ROLE_ authorities.
     * Format: comma-separated string e.g. "PARTNER" or "ORGANIZER".
     */
    private static class CustomRolesToAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            String rolesString = jwt.getClaimAsString("custom:role");

            if (rolesString == null || rolesString.isEmpty()) {
                return Collections.emptyList();
            }

            return Arrays.stream(rolesString.split(","))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()))
                .collect(Collectors.toList());
        }
    }
}
