package ch.batbern.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security Configuration
 *
 * Configures HTTP security for the API Gateway with stateless JWT authentication.
 * CSRF protection is disabled because:
 * - This is a stateless REST API using JWT tokens in Authorization headers
 * - No session cookies are used (stateless session management)
 * - CSRF attacks target cookie-based authentication, which this API doesn't use
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * CORS configuration bean
     * Allows frontend (different origin: localhost:3000, staging.batbern.ch, etc.)
     * to access API (localhost:8080, api.staging.batbern.ch)
     */
    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration =
            new org.springframework.web.cors.CorsConfiguration();

        // Allow specific origins
        // For development: Allow any localhost port (multi-instance support)
        // For production: Only allow specific domains
        configuration.setAllowedOriginPatterns(java.util.Arrays.asList(
            "http://localhost:*",      // Development: any port (e.g., 3000, 4000, 8600)
            "http://127.0.0.1:*",      // Development: any port on 127.0.0.1
            "https://staging.batbern.ch",
            "https://www.batbern.ch"
        ));

        configuration.setAllowedMethods(java.util.Arrays.asList(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"
        ));
        // Use wildcard to allow all headers (case-insensitive per RFC 7230)
        // Prevents issues with case variations (x-correlation-id vs X-Correlation-ID)
        configuration.addAllowedHeader("*");
        configuration.setExposedHeaders(java.util.Arrays.asList(
            "X-Request-Id",
            "X-Correlation-ID",
            "X-Rate-Limit-Remaining",
            "X-Rate-Limit-Reset"
        ));
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
                // Enable CORS for cross-origin requests (frontend on different port/subdomain)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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
                // Enable CORS for cross-origin requests (frontend on different port/subdomain)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Stateless session - no cookies, no CSRF risk
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // System health endpoints
                        .requestMatchers("/actuator/health", "/actuator/info", "/api/v1/config").permitAll()

                        // Story 4.1.3: Public event discovery endpoints (no auth required)
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/current").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions/*").permitAll()

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

                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> { }))
                .build();
    }
}
