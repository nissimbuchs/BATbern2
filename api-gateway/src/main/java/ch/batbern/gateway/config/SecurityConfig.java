package ch.batbern.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Security Configuration
 *
 * Configures HTTP security for the API Gateway with stateless JWT authentication.
 * CSRF protection is disabled because:
 * - This is a stateless REST API using JWT tokens in Authorization headers
 * - No session cookies are used (stateless session management)
 * - CSRF attacks target cookie-based authentication, which this API doesn't use
 *
 * IMPORTANT: When adding public (permitAll) endpoints, you must ALSO add the same
 * rule to the target service's SecurityConfig (e.g. event-management-service's
 * SecurityConfig.java). Both the gateway AND the downstream service enforce auth
 * independently. Forgetting the service-side rule causes 401 even if the gateway permits.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    @Value("${watch.jwt.secret:batbern-watch-dev-secret-key-min-32-chars}")
    private String watchJwtSecret;

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
    public JwtDecoder compositeJwtDecoder() {
        JwtDecoder cognitoDecoder = buildCognitoDecoder();

        SecretKeySpec watchKey = new SecretKeySpec(
                watchJwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder watchDecoder = NimbusJwtDecoder.withSecretKey(watchKey).build();

        return token -> isWatchJwt(token) ? watchDecoder.decode(token) : cognitoDecoder.decode(token);
    }

    private JwtDecoder buildCognitoDecoder() {
        if (jwkSetUri != null && !jwkSetUri.isEmpty()) {
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        }
        if (issuerUri != null && !issuerUri.isEmpty()) {
            return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        }
        throw new IllegalStateException(
                "Gateway JWT: configure spring.security.oauth2.resourceserver.jwt.jwk-set-uri or issuer-uri");
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

    /*
     * ── CORS is intentionally NOT configured in this gateway ──────────────────────────────
     *
     * Per ADR-008 (Simplified API Gateway), CORS is an EDGE concern owned solely by the AWS
     * API Gateway (HTTP API) in front of this service — see
     * infrastructure/lib/stacks/api-gateway-stack.ts `corsPreflight` (allowOrigins,
     * allowMethods, allowHeaders, allowCredentials, exposeHeaders, maxAge). That edge handles
     * the OPTIONS preflight AND adds the CORS response headers (Access-Control-Allow-Origin,
     * -Allow-Credentials, -Expose-Headers, …) to every proxied response, including 4xx/5xx
     * error responses, for allowed origins.
     *
     * This service therefore has NO `corsConfigurationSource` bean and the filter chains below
     * do NOT enable Spring's CorsFilter. A previous implementation duplicated the origin
     * allowlist here (and in a CorsHandler used by the rate-limit / Turnstile / account-active
     * filters); that was the leftover the ADR-008 consolidation removed. Do NOT re-introduce
     * CORS here — to allow a new origin (e.g. a canary subdomain), update the API Gateway
     * `allowOrigins` only. Single source of truth.
     */

    /**
     * Security configuration for test profile
     * Allows test endpoints without auth, requires JWT for GDPR endpoints
     * CSRF disabled: Stateless JWT API - tokens in headers, not cookies
     */
    @Bean
    @Profile("test")
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF not needed for stateless JWT API with header-based auth
                .csrf(AbstractHttpConfigurer::disable)
                // CORS is owned by the AWS API Gateway edge (ADR-008) — disable Spring's CORS
                // filter here so this service does not add a second (duplicate) CORS layer.
                .cors(AbstractHttpConfigurer::disable)
                // Stateless session - no cookies, no CSRF risk
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health", "/api/v1/test-*", "/api/v1/validation/*").permitAll()
                        .requestMatchers("/api/v1/gdpr/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> { }))
                .build();
    }

    /**
     * Default security configuration
     * Requires authentication for all requests except health checks and public endpoints
     * CSRF disabled: Stateless JWT API - tokens in headers, not cookies
     *
     * Story 4.1.3: Added public event discovery endpoints
     */
    @Bean
    @Profile("!test")
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF not needed for stateless JWT API with header-based auth
                .csrf(AbstractHttpConfigurer::disable)
                // CORS is owned by the AWS API Gateway edge (ADR-008) — disable Spring's CORS
                // filter here so this service does not add a second (duplicate) CORS layer.
                .cors(AbstractHttpConfigurer::disable)
                // Stateless session - no cookies, no CSRF risk
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // System health endpoint (needed for load balancer health checks)
                        .requestMatchers("/actuator/health").permitAll()
                        // Other actuator endpoints require auth (metrics, info, prometheus expose internals)
                        .requestMatchers("/actuator/**").authenticated()
                        // ServiceHealthController: proxy health/info of downstream services via Service Connect.
                        // Used by post-deploy smoke tests; only exposes UP/DOWN, no internal metrics.
                        .requestMatchers(HttpMethod.GET, "/services/*/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/services/*/info").permitAll()
                        .requestMatchers("/api/v1/config").permitAll()

                        // Story 4.1.3: Public event discovery endpoints (no auth required)
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

                        // Story 1.15a.1b: Public speaker list endpoint (GET only, POST/DELETE require ORGANIZER)
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions/*/speakers").permitAll()

                        // Story 5.9: Public materials download endpoint for archived events
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions/*/materials/*/download").permitAll()

                        // Story 4.1.5: Public registration endpoints
                        // (no auth required - anonymous registration per ADR-005)
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/registrations").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/registrations/*").permitAll()

                        // Story 4.1.5c: Email-based confirmation endpoint
                        // (no auth required - JWT token in query param provides security)
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/registrations/confirm").permitAll()

                        // Email-based cancellation endpoint
                        // (no auth required - JWT token in query param provides security)
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/registrations/cancel").permitAll()

                        // Public partner showcase endpoint (GET only for homepage display)
                        .requestMatchers(HttpMethod.GET, "/api/v1/partners").permitAll()

                        // Public company endpoint (GET only for partner showcase logo/website enrichment)
                        .requestMatchers(HttpMethod.GET, "/api/v1/companies/*").permitAll()

                        // Story 4.1.5: Public company search for registration autocomplete
                        .requestMatchers(HttpMethod.GET, "/api/v1/companies/search").permitAll()

                        // Public organizers endpoint for About page
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/organizers").permitAll()

                        // Story 11.C.1: Public user-portrait endpoint (replaces deleted /api/v1/speakers/{username})
                        // CUMS PublicUserController. Filters to SPEAKER role only.
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/users/*").permitAll()

                        // Global teaser images: public list for presenter view (_global = all events)
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/_global/teaser-images").permitAll()

                        // Story 10.8a: Public presentation settings (moderator page)
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/settings/presentation").permitAll()

                        // Story 10.16: AI feature flags (public, no auth required)
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/settings/features").permitAll()

                        // Story 6.1a/6.2a/6.2b: Speaker portal endpoints (token-protected, no JWT auth)
                        // Story 11.C.1: profile endpoints removed (controller deleted, frontend tear-down in Phase F)
                        .requestMatchers(HttpMethod.POST, "/api/v1/speaker-portal/validate-token").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/speaker-portal/respond").permitAll()

                        // Story 6.4: Speaker dashboard endpoint (token-protected, no JWT auth)
                        .requestMatchers(HttpMethod.GET, "/api/v1/speaker-portal/dashboard").permitAll()

                        // Story 11.E.3 / 11.F.1: /api/v1/speaker-portal/** is Cognito-secured
                        // via @PreAuthorize("hasRole('SPEAKER')") in EMS. The previous permitAll
                        // matchers (content GET/draft/submit + materials presigned-url/confirm) and
                        // the magic-link surface (/api/v1/auth/speaker-magic-login + /api/v1/e2e-test/**)
                        // were removed in Story 11.F.1.

                        // Story 10.7: Newsletter public endpoints (subscribe + token-based unsubscribe)
                        .requestMatchers(HttpMethod.POST, "/api/v1/newsletter/subscribe").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/newsletter/unsubscribe/verify").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/newsletter/unsubscribe").permitAll()

                        // Story 10.12: Self-service deregistration (token-protected)
                        .requestMatchers(HttpMethod.GET, "/api/v1/registrations/deregister/verify").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/registrations/deregister").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/registrations/deregister/by-email").permitAll()

                        // W2.2: Watch pairing — unauthenticated (Watch has no JWT yet; exchanges pairing code for JWT)
                        .requestMatchers(HttpMethod.POST, "/api/v1/watch/pair").permitAll()
                        // W2.2: Watch JWT auth — unauthenticated (exchanges pairing token for JWT; must be permit-all)
                        .requestMatchers(HttpMethod.POST, "/api/v1/watch/authenticate").permitAll()

                        // Story 10.26: Internal Lambda forwarder endpoints (VPC-only, no JWT needed)
                        // Safe: Spring Boot API Gateway is only reachable within VPC (Service Connect);
                        // external traffic is authenticated by AWS API Gateway's Cognito authorizer.
                        .requestMatchers(HttpMethod.GET, "/api/v1/users").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/registrations").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/settings/*").permitAll()
                        // Spec auto-participant-email-aliases-excel-export (F2): per-event
                        // distribution-list resolver for batbern{N}-speaker@ +
                        // batbern{N}-moderator@. Same VPC-only forwarder pattern.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/events/*/distribution-list/*").permitAll()

                        // All other requests require authentication (including Watch organizer endpoints,
                        // which are validated by the composite JwtDecoder below)
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(compositeJwtDecoder())))
                .build();
    }
}
