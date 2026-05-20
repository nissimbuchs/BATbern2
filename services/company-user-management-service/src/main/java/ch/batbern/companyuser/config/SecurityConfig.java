package ch.batbern.companyuser.config;

import ch.batbern.companyuser.security.VpcInternalAuthorizationManager;
import ch.batbern.shared.security.JwtRolesConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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

    @Value("${watch.jwt.secret:batbern-watch-dev-secret-key-min-32-chars}")
    private String watchJwtSecret;

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
    public SecurityFilterChain localFilterChain(HttpSecurity http,
                                                JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Story 4.1.5: Anonymous registration - allow get-or-create user endpoint
                .requestMatchers("/api/v1/users/get-or-create").permitAll()
                // Story 4.1.5: Public company search for registration autocomplete
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/companies/search").permitAll()
                // Public company endpoint (GET only for partner showcase enrichment)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/companies/*").permitAll()
                // Public organizers endpoint for About page
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/public/organizers").permitAll()
                // Story 11.C.1: Public user-portrait endpoint (replaces deleted /api/v1/speakers/{username})
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/public/users/*").permitAll()
                // Story 10.8a: Public presentation settings (moderator page)
                .requestMatchers(HttpMethod.GET, "/api/v1/public/settings/presentation").permitAll()
                // Public user profile endpoint (GET only for service-to-service calls from localhost)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/users/*").permitAll()
                // W2.2: Watch pairing endpoints — unauthenticated (code/token IS the credential)
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/watch/pair").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/watch/authenticate").permitAll()
                .anyRequest().authenticated() // Require authentication but accept any authenticated user
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter)
                )
            );

        return http.build();
    }

    /**
     * Production security filter chain with JWT authentication
     */
    @Bean
    @Profile("!test & !local")
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable for stateless API
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Story 4.1.5: Anonymous registration - allow get-or-create user endpoint
                .requestMatchers("/api/v1/users/get-or-create").permitAll()
                // Story 4.1.5: Public company search for registration autocomplete
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/companies/search").permitAll()
                // Public company endpoint (GET only for partner showcase enrichment)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/companies/*").permitAll()
                // Public organizers endpoint for About page
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/public/organizers").permitAll()
                // Story 11.C.1: Public user-portrait endpoint (replaces deleted /api/v1/speakers/{username})
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/public/users/*").permitAll()
                // Story 10.8a: Public presentation settings (moderator page)
                .requestMatchers(HttpMethod.GET, "/api/v1/public/settings/presentation").permitAll()
                // Current user endpoint always requires authentication (even from VPC)
                .requestMatchers("/api/v1/users/me").authenticated()
                // Service-to-service: Allow user profile lookups from VPC internal network
                // OR authenticated external requests (via API Gateway with JWT)
                .requestMatchers("/api/v1/users/*")
                    .access(new VpcInternalAuthorizationManager(vpcCidr))
                // Story 10.26: Allow user list by role (Lambda email forwarder — routes via NAT GW, no VPC IP)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/users").permitAll()
                // W2.2: Watch pairing endpoints — unauthenticated (code/token IS the credential)
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/watch/pair").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/watch/authenticate").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter)
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
            // Enable anonymous authentication for public endpoints
            .anonymous(anonymous -> anonymous.principal("anonymous"))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Story 4.1.5: Anonymous registration - allow get-or-create user endpoint
                .requestMatchers("/api/v1/users/get-or-create").permitAll()
                // Story 4.1.5: Public company search for registration autocomplete
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/companies/search").permitAll()
                // Public company endpoint (GET only for partner showcase enrichment)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/companies/*").permitAll()
                // Public organizers endpoint for About page
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/public/organizers").permitAll()
                // Story 11.C.1: Public user-portrait endpoint (replaces deleted /api/v1/speakers/{username})
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/public/users/*").permitAll()
                // Story 10.8a: Public presentation settings (moderator page)
                .requestMatchers(HttpMethod.GET, "/api/v1/public/settings/presentation").permitAll()
                // Current user endpoint always requires authentication (even from VPC)
                .requestMatchers("/api/v1/users/me").authenticated()
                // Test environment: Enforce authentication for all user endpoints
                // (tests run from localhost but should verify authentication logic)
                .requestMatchers("/api/v1/users/*").authenticated()
                // W2.2: Watch pairing endpoints — unauthenticated (code/token IS the credential)
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/watch/pair").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/watch/authenticate").permitAll()
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
     * Multi-issuer JWT decoder accepting both AWS Cognito (RS256) and Watch app (HS256) tokens.
     *
     * Story 11.C.1: Watch organizer JWTs are now propagated to CUMS by EMS's
     * {@code WatchEventController} / {@code WatchSpeakerArrivalService} when they call
     * {@code UserApiClient.getUserByUsername(...)}, so CUMS must accept the Watch issuer too.
     * Mirrors the multi-issuer pattern in EMS {@code SecurityConfig#jwtDecoder()}.
     *
     * Routing strategy: peek at the "iss" claim in the (unverified) JWT payload to select
     * the correct decoder. Signature verification is then performed by the selected decoder,
     * so a forged issuer claim cannot bypass verification — it would just fail with the wrong key.
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
        cognitoDecoder.setJwtValidator(JwtValidators.createDefault());

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
     * JWT Authentication Converter to extract roles from custom:role claim, with a
     * database fallback when the claim is empty. See {@link JwtRolesConverter}.
     * Story 1.2.6 / Epic 11.E.7: custom:role primary; DB-by-sub fallback enables
     * local-dev speakers whose user_profiles row is in the local DB but whose
     * Cognito user is in staging (so the PreTokenGen Lambda finds no roles).
     * In staging the JWT always carries custom:role, so the fallback is dormant.
     *
     * 11.E.9: DataSource is injected via {@link ObjectProvider} so this bean can
     * load in {@code @WebMvcTest} slices that don't include JPA. With no DataSource
     * the converter still parses the primary {@code custom:role} claim; only the
     * DB-fallback path is disabled (already dormant in non-local environments
     * since the staging JWT always carries roles).
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
