package ch.batbern.companyuser.config;

import ch.batbern.companyuser.security.VpcInternalAuthorizationManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
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
import java.util.List;
import java.util.stream.Collectors;

/**
 * Security configuration for the Company-User Management Service
 * AC10: Authentication integration with API Gateway
 * Configures role-based access control for company management endpoints with JWT authentication
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

    @Value("${vpc.cidr:10.1.0.0/16}")
    private String vpcCidr;

    /**
     * Enable method-level security for production and staging environments
     * Enforces @PreAuthorize annotations on controller methods
     */
    @Configuration
    @EnableMethodSecurity(prePostEnabled = true)
    @Profile("!local")
    static class ProductionMethodSecurityConfig {
    }

    /**
     * Local development security filter chain
     * JWT validation active but all authenticated requests permitted (trusted localhost environment)
     * Mirrors AWS VPC security pattern: network isolation in AWS = localhost trust in local dev
     */
    @Bean
    @Profile("local")
    public SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Story 4.1.5: Public company search for registration autocomplete
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/companies/search").permitAll()
                // Public company endpoint (GET only for partner showcase enrichment)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/companies/*").permitAll()
                // Public user profile endpoint (GET only for service-to-service calls from localhost)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/users/*").permitAll()
                .anyRequest().authenticated() // Require authentication but accept any authenticated user
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
     * Production security filter chain with JWT authentication
     */
    @Bean
    @Profile("!test & !local")
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable for stateless API
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Story 4.1.5: Anonymous registration - allow get-or-create user endpoint
                .requestMatchers("/api/v1/users/get-or-create").permitAll()
                // Story 4.1.5: Public company search for registration autocomplete
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/companies/search").permitAll()
                // Public company endpoint (GET only for partner showcase enrichment)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/companies/*").permitAll()
                // Current user endpoint always requires authentication (even from VPC)
                .requestMatchers("/api/v1/users/me").authenticated()
                // Service-to-service: Allow user profile lookups from VPC internal network
                // OR authenticated external requests (via API Gateway with JWT)
                .requestMatchers("/api/v1/users/*")
                    .access(new VpcInternalAuthorizationManager(vpcCidr))
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
     * Test security filter chain with authentication enforcement
     * Uses @WithMockUser for testing authenticated endpoints
     */
    @Bean
    @Profile("test")
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .anonymous(anonymous -> anonymous.principal("anonymous"))  // Enable anonymous authentication for public endpoints
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Story 4.1.5: Anonymous registration - allow get-or-create user endpoint
                .requestMatchers("/api/v1/users/get-or-create").permitAll()
                // Story 4.1.5: Public company search for registration autocomplete
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/companies/search").permitAll()
                // Public company endpoint (GET only for partner showcase enrichment)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/companies/*").permitAll()
                // Current user endpoint always requires authentication (even from VPC)
                .requestMatchers("/api/v1/users/me").authenticated()
                // Test environment: Enforce authentication for all user endpoints
                // (tests run from localhost but should verify authentication logic)
                .requestMatchers("/api/v1/users/*").authenticated()
                .anyRequest().authenticated() // Enforce authentication in tests
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    // Return 401 for unauthenticated requests
                    response.sendError(401, "Unauthorized");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    // Return 403 for authenticated but unauthorized requests
                    response.sendError(403, "Forbidden");
                })
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
     * Story 1.2.6: Migrated from cognito:groups to custom:role (ADR-001)
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
     * Story 1.2.6: ADR-001 Database-centric architecture
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
