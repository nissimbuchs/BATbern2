package ch.batbern.partners.config;

import ch.batbern.shared.security.JwtRolesConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.sql.DataSource;

/**
 * Security configuration for the Partner Coordination Service.
 *
 * Story 8.1: Added @EnableMethodSecurity for @PreAuthorize support (AC6).
 * Added JwtAuthenticationConverter to extract roles from custom:role claim.
 *
 * Method Security Strategy:
 * - All profiles (local, test, staging, production): @EnableMethodSecurity enforces @PreAuthorize.
 *   Pattern 3b (Epic 11.E.7) makes this safe locally even for CUMS-provisioned speakers whose
 *   Cognito user lives in staging while their user_profiles row lives in the local DB — the
 *   JwtRolesConverter DB fallback populates ROLE_<X> from the local row. Before Pattern 3b
 *   existed, local profile relaxed method security to a "trusted localhost" model; that
 *   shortcut is no longer needed and masked role-config drift between dev and staging.
 *   Removed 2026-05-25 during Bruno F2 admin-cleanup-api hardening.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    /**
     * Enable method-level security in every profile (local, test, staging, production).
     * Enforces @PreAuthorize annotations on controller methods (AC6).
     */
    @Configuration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityConfig {
    }

    /**
     * Local development security filter chain.
     * All requests permitted BUT JWT tokens are still parsed for service-to-service propagation.
     */
    @Bean
    @Profile("local")
    public SecurityFilterChain localFilterChain(HttpSecurity http,
                                                JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
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
                    .jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    /**
     * Production security filter chain with JWT authentication.
     * Actuator endpoints are public for health checks.
     * Public partner list endpoint for homepage showcase.
     */
    @Bean
    @Profile("!local & !test")
    public SecurityFilterChain productionFilterChain(HttpSecurity http,
                                                     JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
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
                // Story 7.1: Attendee "Topics From the Floor" — submit-only, ATTENDEE role
                .requestMatchers(HttpMethod.POST, "/api/v1/attendees/topics").hasRole("ATTENDEE")
                // Story 8.3: Partner Meeting Coordination (AC6 — ORGANIZER only)
                .requestMatchers("/api/v1/partner-meetings/**").hasRole("ORGANIZER")
                // Story 8.4: Partner Notes (ORGANIZER only — partners must not see notes)
                .requestMatchers("/api/v1/partners/*/notes/**").hasRole("ORGANIZER")
                // Issue #821: reactivating a partnership is an organizer action. Stated
                // explicitly rather than relying on the anyRequest() fallback below, which
                // would let ANY authenticated principal — attendee or speaker — flip a
                // partnership back on.
                .requestMatchers(HttpMethod.POST, "/api/v1/partners/*/reactivate").hasRole("ORGANIZER")

                // #961: partner MUTATIONS had no role rule and fell through to
                // .anyRequest().authenticated() below, so any authenticated principal — an
                // ATTENDEE, a SPEAKER, a PARTNER of a different company — could create,
                // re-level or DEACTIVATE a partnership. Only the client-side half of the
                // "check server-side AND client-side" rule existed: the Settings tab is
                // organizer-only in the UI, which holds right up until someone calls the API
                // directly.
                //
                // These sit AFTER the /topics and /notes matchers above on purpose — a
                // broader `/api/v1/partners/*` pattern placed earlier would shadow them.
                //
                // Belt and braces with @PreAuthorize on the controller methods, which is the
                // layer that actually gets tested: this chain is replaced by permitAll under
                // the `test` profile, so a matcher-only rule would be unverifiable.
                .requestMatchers(HttpMethod.POST, "/api/v1/partners").hasRole("ORGANIZER")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/partners/*").hasRole("ORGANIZER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/partners/*").hasRole("ORGANIZER")
                .requestMatchers(HttpMethod.GET, "/api/v1/partners/statistics").hasRole("ORGANIZER")

                // Contact PII: organizer, or the partner's own company (enforced precisely by
                // @PreAuthorize on the controller; this is the coarse first gate).
                .requestMatchers(HttpMethod.GET, "/api/v1/partners/*/contacts")
                        .hasAnyRole("ORGANIZER", "PARTNER")

                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .decoder(jwtDecoder())
                .jwtAuthenticationConverter(jwtAuthenticationConverter)));

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
