package ch.batbern.events.config;

import ch.batbern.shared.security.JwtRolesConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Security configuration for the Event Management Service
 * Configures role-based access control for event management endpoints with JWT authentication
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

    @Value("${watch.jwt.secret:batbern-watch-dev-secret-key-min-32-chars}")
    private String watchJwtSecret;

    /**
     * Enable method-level security in every profile (local, test, staging, production).
     * Enforces @PreAuthorize annotations on controller methods.
     */
    @Configuration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityConfig {
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
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
            .cors(Customizer.withDefaults()) // Uses CorsConfigurationSource bean if present
            .csrf(csrf -> csrf.disable()) // Disable for stateless API
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Public endpoints - no authentication required
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // Story BAT-7: WebSocket endpoints for real-time notifications
                .requestMatchers("/ws/**").permitAll()
                // W4.1: Raw WebSocket for Watch clients — auth handled at STOMP level by JwtStompInterceptor
                .requestMatchers("/api/v1/watch/ws").permitAll()

                // Story 4.1.3: Public event discovery endpoints
                .requestMatchers(HttpMethod.GET, "/api/v1/events/current").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions/*").permitAll()

                // Story 10.21: Public event photo endpoints (AC4, AC5)
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*/photos").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/events/recent-photos").permitAll()

                // Story 4.2 (BAT-109): Public archive browsing endpoint
                .requestMatchers(HttpMethod.GET, "/api/v1/events").permitAll()

                // Story 4.2 (BAT-109): Public topics list for archive filtering
                .requestMatchers(HttpMethod.GET, "/api/v1/topics").permitAll()

                // Story 1.15a.1b: Public speaker list endpoint (GET only, POST/PUT/DELETE require ORGANIZER)
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions/*/speakers").permitAll()

                // Story 5.9: Public materials download endpoint for archived events
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions/*/materials/*/download").permitAll()

                // Story 2.2a: Public anonymous registration endpoints (ADR-005)
                .requestMatchers(HttpMethod.POST, "/api/v1/events/*/registrations").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*/registrations/*").permitAll()
                // Email confirmation endpoint (no auth required, token-protected)
                .requestMatchers(HttpMethod.POST, "/api/v1/events/*/registrations/confirm").permitAll()
                // Email cancellation endpoint (no auth required, token-protected)
                .requestMatchers(HttpMethod.POST, "/api/v1/events/*/registrations/cancel").permitAll()

                // Story 11.E.3 / 11.F.1: /api/v1/speaker-portal/** is Cognito-secured via
                // @PreAuthorize("hasRole('SPEAKER')") on each controller. The magic-link auth
                // surface (/auth/speaker-magic-login + /speaker-portal/validate-token +
                // /api/v1/e2e-test/**) was removed in Story 11.F.1.

                // Story 10.7: Newsletter public endpoints (subscribe + token-based unsubscribe)
                .requestMatchers(HttpMethod.POST, "/api/v1/newsletter/subscribe").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/newsletter/unsubscribe/verify").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/newsletter/unsubscribe").permitAll()

                // Story 10.12: Self-service deregistration (token-protected)
                .requestMatchers(HttpMethod.GET, "/api/v1/registrations/deregister/verify").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/registrations/deregister").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/registrations/deregister/by-email").permitAll()

                // Story 10.16: AI feature flag endpoint (public — no auth required)
                .requestMatchers(HttpMethod.GET, "/api/v1/public/settings/features").permitAll()

                // Story 10.26: Internal Lambda forwarder endpoints (VPC-only access)
                // Registrations list for batbern{N}@ event distribution lists
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*/registrations").permitAll()
                // Admin settings read for support@ contact resolution
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/settings/*").permitAll()

                // Dev tool: local email inbox (controller is @Profile("local") — safe in prod)
                .requestMatchers("/dev/emails/**").permitAll()

                // Global teaser images: public list for presenter view (_global = all events)
                .requestMatchers(HttpMethod.GET, "/api/v1/events/_global/teaser-images").permitAll()

                // All other requests require authentication
                // AWS API Gateway validates JWT; Spring Security parses it for @PreAuthorize
                .anyRequest().authenticated()
            )
            // Configure OAuth2 resource server to parse JWT tokens
            // Required even in local mode for SecurityContextHelper to extract user context
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
            );

        return http.build();
    }

    /**
     * Multi-issuer JWT decoder accepting both AWS Cognito (RS256) and Watch app (HS256) tokens.
     *
     * Routing strategy: peek at the "iss" claim in the (unverified) JWT payload to select the
     * correct decoder. Signature verification is then performed by the selected decoder, so
     * a forged issuer claim cannot bypass verification — it would just fail with the wrong decoder.
     *
     * - iss == "batbern-watch" → HMAC-SHA256 decoder (Watch pairing JWT)
     * - anything else          → Cognito RS256 decoder (Cognito ID/access token)
     */
    @Bean
    @Profile("!test")
    public JwtDecoder jwtDecoder() {
        if (jwkSetUri == null || jwkSetUri.isEmpty()) {
            throw new IllegalArgumentException("JWT JWK Set URI must be configured");
        }

        NimbusJwtDecoder cognitoDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        cognitoDecoder.setJwtValidator(token ->
                org.springframework.security.oauth2.jwt.JwtValidators.createDefault().validate(token));

        SecretKeySpec watchKey = new SecretKeySpec(
                watchJwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder watchDecoder = NimbusJwtDecoder.withSecretKey(watchKey).build();

        return token -> isWatchJwt(token) ? watchDecoder.decode(token) : cognitoDecoder.decode(token);
    }

    /** Peeks at the JWT payload (base64url, no signature check) to read the "iss" claim. */
    private static boolean isWatchJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return false;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return payload.contains("\"iss\":\"batbern-watch\"");
        } catch (Exception e) {
            return false;
        }
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
