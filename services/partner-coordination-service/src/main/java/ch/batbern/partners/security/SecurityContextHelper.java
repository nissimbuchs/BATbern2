package ch.batbern.partners.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Helper class to extract user information from Spring Security context.
 *
 * Per ADR-003: Uses meaningful IDs (username), NOT UUIDs.
 *
 * Supports both JWT tokens (production) and User objects (test with @WithMockUser).
 */
@Component
@Slf4j
public class SecurityContextHelper {

    /**
     * Gets the current authenticated user's username from JWT token or mock user.
     *
     * Story 1.16.2 / ADR-003: Use username (meaningful ID) instead of UUID.
     *
     * @return Username (cognito:username claim from JWT or username from mock user)
     * @throws SecurityException if not authenticated
     */
    public String getCurrentUsername() {
        Authentication authentication = getAuthentication();

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaim("cognito:username");
            if (username == null) {
                log.warn("cognito:username claim not found in JWT, falling back to subject");
                return jwt.getSubject(); // Fallback to subject for backward compatibility
            }
            return username;
        } else if (authentication.getPrincipal() instanceof User user) {
            // In test environment with @WithMockUser, use username
            return user.getUsername();
        } else {
            log.error("Unsupported principal type: {}", authentication.getPrincipal().getClass());
            throw new SecurityException("Unsupported authentication principal type");
        }
    }

    /**
     * Gets the authenticated user from Spring Security context.
     *
     * @return Authentication object
     * @throws SecurityException if authentication is missing or invalid
     */
    private Authentication getAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            log.error("No authentication found in security context");
            throw new SecurityException("No authentication found in security context");
        }

        if (!authentication.isAuthenticated()) {
            log.error("User is not authenticated");
            throw new SecurityException("User is not authenticated");
        }

        return authentication;
    }
}
