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

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
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

    @Value("${watch.jwt.secret:batbern-watch-dev-secret-key-min-32-chars}")
    private String watchJwtSecret;

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

                // Story BAT-7: WebSocket endpoints for real-time notifications
                .requestMatchers("/ws/**").permitAll()
                // W4.1: Raw WebSocket for Watch clients — auth handled at STOMP level by JwtStompInterceptor
                .requestMatchers("/api/v1/watch/ws").permitAll()

                // Story 4.1.3: Public event discovery endpoints
                .requestMatchers(HttpMethod.GET, "/api/v1/events/current").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions/*").permitAll()

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

                // Story 6.1a: Speaker portal magic link validation (no auth required, token-protected)
                .requestMatchers(HttpMethod.POST, "/api/v1/speaker-portal/validate-token").permitAll()

                // Story 6.2a: Speaker portal response submission (no auth required, token-protected)
                .requestMatchers(HttpMethod.POST, "/api/v1/speaker-portal/respond").permitAll()

                // Story 6.2b: Speaker portal profile endpoints (no auth required, token-protected)
                .requestMatchers(HttpMethod.GET, "/api/v1/speaker-portal/profile").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/speaker-portal/profile").permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/speaker-portal/profile/photo/presigned-url").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/speaker-portal/profile/photo/confirm").permitAll()

                // Story 6.4: Speaker dashboard endpoint (no auth required, token-protected)
                .requestMatchers(HttpMethod.GET, "/api/v1/speaker-portal/dashboard").permitAll()

                // Story 6.3: Speaker portal content submission endpoints (no auth required, token-protected)
                .requestMatchers(HttpMethod.GET, "/api/v1/speaker-portal/content").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/speaker-portal/content/draft").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/speaker-portal/content/submit").permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/speaker-portal/materials/presigned-url").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/speaker-portal/materials/confirm").permitAll()

                // Story 6.3: E2E test token generation (dev/test profiles only, controller is @Profile protected)
                .requestMatchers("/api/v1/e2e-test/**").permitAll()

                // All other requests require authentication
                // AWS API Gateway validates JWT; Spring Security parses it for @PreAuthorize
                .anyRequest().authenticated()
            )
            // Configure OAuth2 resource server to parse JWT tokens
            // Required even in local mode for SecurityContextHelper to extract user context
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
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
     * Converter to extract roles from JWT claims and map to Spring Security authorities.
     *
     * Supports two JWT formats:
     * - Cognito tokens: roles in "custom:role" claim (comma-separated, e.g. "ORGANIZER,SPEAKER")
     * - Watch tokens:   role in "role" claim (single value, e.g. "ORGANIZER")
     */
    private static class CustomRolesToAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            // Cognito ID token: "custom:role" claim (comma-separated)
            String rolesString = jwt.getClaimAsString("custom:role");
            // Watch JWT fallback: "role" claim (single value)
            if (rolesString == null || rolesString.isEmpty()) {
                rolesString = jwt.getClaimAsString("role");
            }
            if (rolesString == null || rolesString.isEmpty()) {
                return Collections.emptyList();
            }
            return Arrays.stream(rolesString.split(","))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()))
                .collect(Collectors.toList());
        }
    }
}
