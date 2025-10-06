package ch.batbern.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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
                // Stateless session - no cookies, no CSRF risk
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health", "/api/v1/test-*", "/api/v1/validation/*").permitAll()
                        .requestMatchers("/api/v1/gdpr/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }

    /**
     * Default security configuration
     * Requires authentication for all requests except health checks
     * CSRF disabled: Stateless JWT API - tokens in headers, not cookies
     */
    @Bean
    @Profile("!test")
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF not needed for stateless JWT API with header-based auth
                .csrf(AbstractHttpConfigurer::disable)
                // Stateless session - no cookies, no CSRF risk
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }
}
