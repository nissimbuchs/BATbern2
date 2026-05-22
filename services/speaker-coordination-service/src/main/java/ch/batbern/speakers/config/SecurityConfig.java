package ch.batbern.speakers.config;

import ch.batbern.shared.security.JwtRolesConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.sql.DataSource;

/**
 * Security configuration for the Speaker Coordination Service
 * Story 5.4: Speaker Status Management (AC16 - ORGANIZER role required)
 *
 * Configures role-based access control for speaker status endpoints with JWT authentication
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
     * Story 5.4: All speaker status endpoints require ORGANIZER role
     */
    @Bean
    @Profile("!test")
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable for stateless API
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Public endpoints - no authentication required
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // All other requests require authentication
                // Note: Actual JWT validation happens at API Gateway layer
                // Services trust requests from API Gateway (VPC-internal communication)
                .anyRequest().permitAll()
            )
            // Configure OAuth2 resource server to parse JWT tokens
            // Required even in local mode for SecurityContextHelper to extract user context
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
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
     * JWT Authentication Converter with database fallback when custom:role is empty.
     * Epic 11.E.7: local-dev speakers have a Cognito user in staging but a user_profiles
     * row only in the local DB, so the PreTokenGen Lambda can't populate custom:role.
     * In staging the JWT always carries roles, so the fallback is dormant.
     * See {@link JwtRolesConverter}.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            ObjectProvider<DataSource> dataSourceProvider) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new JwtRolesConverter(dataSourceProvider.getIfAvailable()));
        return converter;
    }
}
