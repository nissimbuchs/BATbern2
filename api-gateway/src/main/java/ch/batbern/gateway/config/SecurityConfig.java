package ch.batbern.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
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
     * ── CORS ownership ────────────────────────────────────────────────────────────────────
     *
     * PRODUCTION: CORS is an EDGE concern owned solely by the AWS API Gateway (HTTP API) in
     * front of this service (ADR-008) — see infrastructure/lib/stacks/api-gateway-stack.ts
     * `corsPreflight` (allowOrigins, allowMethods, allowHeaders, allowCredentials, exposeHeaders,
     * maxAge). That edge answers the OPTIONS preflight AND adds the CORS response headers to
     * every proxied response (incl. 4xx/5xx) for allowed origins. This gateway therefore does
     * NOT configure CORS for prod (the `staging` profile has no `corsConfigurationSource` bean).
     * To allow a new PROD origin (e.g. a canary subdomain), update the API Gateway `allowOrigins`
     * only — do NOT add prod origins here. A previous implementation duplicated the prod allowlist
     * here (+ a CorsHandler used by the rate-limit / Turnstile / account-active filters); that
     * leftover was removed when CORS was consolidated to the edge.
     *
     * LOCAL DEV: there is NO API Gateway locally — the browser (Vite dev server on
     * http://localhost:8100) calls this gateway directly on http://localhost:8000, so the gateway
     * itself must answer CORS. The local-only `corsConfigurationSource` bean below does that —
     * permissively (all origins; it is local-only). It is gated to the `local` + `dev` profiles:
     * native dev (`scripts/dev/start-all-native.sh`) runs `local`, and `dev` is the bootRun
     * default (`application.yml SPRING_PROFILES_ACTIVE:dev`). ECS runs the `staging` profile, so
     * this bean never exists in prod and never competes with the API Gateway edge.
     */
    @Bean
    @Profile({ "local", "dev" })
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration =
            new org.springframework.web.cors.CorsConfiguration();
        // This is a deliberate MIRROR of the API Gateway `corsPreflight` in
        // infrastructure/lib/stacks/api-gateway-stack.ts — KEEP THE TWO IN SYNC. Only the ORIGINS
        // differ: prod's edge allowlists the batbern.ch domains, whereas locally there is no edge
        // so we reflect ALL origins (any localhost port / 127.0.0.1 / LAN IP for mobile testing /
        // custom hosts entry — dev-only bean, never in prod, so zero production impact). Methods,
        // allowHeaders, exposeHeaders, credentials and maxAge are kept IDENTICAL on purpose: if
        // the edge is missing a method or an exposed header, local dev fails the same way — so we
        // catch CORS gaps here instead of only discovering them in prod.
        configuration.setAllowedOriginPatterns(java.util.List.of("*"));
        // Mirrors APIGW allowMethods (GET, POST, PUT, DELETE, PATCH, OPTIONS).
        configuration.setAllowedMethods(java.util.List.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        // Mirrors APIGW allowHeaders: ['*'] (case-insensitive per RFC 7230).
        configuration.addAllowedHeader("*");
        // Mirrors APIGW exposeHeaders — the SPA reads X-Correlation-ID off responses (~8 places).
        configuration.setExposedHeaders(java.util.List.of(
            "X-Correlation-ID", "X-Request-Id",
            "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset"
        ));
        // Mirrors APIGW allowCredentials + maxAge (1 hour).
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source =
            new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

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
                // Uses the `corsConfigurationSource` bean IF present — i.e. ONLY under the local
                // dev profiles (`local`/`dev`). In prod (`staging` profile) no such bean exists,
                // so this is a no-op and CORS is owned solely by the API Gateway edge (ADR-008).
                // See the CORS-ownership note above.
                .cors(Customizer.withDefaults())
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
                // Uses the `corsConfigurationSource` bean IF present — i.e. ONLY under the local
                // dev profiles (`local`/`dev`). In prod (`staging` profile) no such bean exists,
                // so this is a no-op and CORS is owned solely by the API Gateway edge (ADR-008).
                // See the CORS-ownership note above.
                .cors(Customizer.withDefaults())
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

                        // Story 15.1: live-timing poll anonymous-readable; POST .../actions requires ORGANIZER.
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/live-timing").permitAll()
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

                        // Story 7.5: Public read of a session's Q&A thread (writes auth'd in EMS).
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions/*/qna").permitAll()

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

                        // Story 7.4: Public "Thank the Organizers" endpoints (anonymous allowed).
                        // Mirror in event-management-service SecurityConfig. POST is added to the
                        // Turnstile protected-endpoints list; the GET is count-only for the public.
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/thanks").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/thanks").permitAll()

                        // Story 7.7: Public curated featured thank-you marquee (global, cross-event).
                        // The organizer PATCH /api/v1/events/*/thanks/* stays authenticated.
                        .requestMatchers(HttpMethod.GET, "/api/v1/thanks/featured").permitAll()

                        // Additional-email verification (v2): public token-credentialed verify
                        // endpoints (GET-check / POST-confirm). The token IS the credential.
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/additional-emails/verify").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/additional-emails/verify").permitAll()

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
