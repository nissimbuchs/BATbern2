package ch.batbern.events.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Test Security Configuration
 * Enables method-level security (@PreAuthorize) for integration tests.
 *
 * Key configuration:
 * - HTTP-level security: permitAll() to allow @WithMockUser through
 * - Method-level security: @EnableMethodSecurity to enforce @PreAuthorize
 * - Returns 401 for unauthenticated requests, 403 for insufficient permissions
 *
 * Note: In production, authentication is handled at API Gateway level (Story 1.2)
 * Services receive pre-authenticated requests with user context headers.
 */
@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // Enable @PreAuthorize support
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    // Require authentication at HTTP level for proper 401/403 distinction
                    .anyRequest().authenticated()
            )
                .exceptionHandling(exceptions -> exceptions
                    // Return 401 for unauthenticated requests (missing credentials)
                    .authenticationEntryPoint(authenticationEntryPoint())
                    // Return 403 for authenticated but unauthorized requests (insufficient permissions)
                    .accessDeniedHandler(accessDeniedHandler())
            )
                // Allow @WithMockUser to work properly
                .anonymous(anonymous -> anonymous.disable());
        return http.build();
    }

    /**
     * Returns 401 Unauthorized for unauthenticated requests
     */
    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
        };
    }

    /**
     * Returns 403 Forbidden for authenticated but unauthorized requests
     */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            // Check if user is authenticated
            if (request.getUserPrincipal() == null) {
                // No authentication - return 401
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
            } else {
                // Authenticated but insufficient permissions - return 403
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Insufficient permissions\"}");
            }
        };
    }
}
