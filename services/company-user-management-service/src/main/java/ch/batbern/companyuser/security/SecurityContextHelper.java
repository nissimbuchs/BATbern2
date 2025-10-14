package ch.batbern.companyuser.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper class to extract user information from Spring Security context
 * AC10: Authentication integration - provide company context
 *
 * GREEN Phase: Implementation to make tests pass
 *
 * Supports both JWT tokens (production) and User objects (test with @WithMockUser)
 */
@Component
@Slf4j
public class SecurityContextHelper {

    /**
     * Gets the current authenticated user's ID from JWT token or mock user
     * @return User ID (subject claim from JWT or username from mock user)
     * @throws SecurityException if not authenticated
     */
    public String getCurrentUserId() {
        Authentication authentication = getAuthentication();

        if (authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            return jwt.getSubject();
        } else if (authentication.getPrincipal() instanceof User) {
            // In test environment with @WithMockUser, use username as user ID
            User user = (User) authentication.getPrincipal();
            return user.getUsername();
        } else {
            log.error("Unsupported principal type: {}", authentication.getPrincipal().getClass());
            throw new SecurityException("Unsupported authentication principal type");
        }
    }

    /**
     * Gets the current authenticated user's email from JWT token or mock user
     * @return User email (email claim from JWT or username from mock user)
     * @throws SecurityException if not authenticated
     */
    public String getCurrentUserEmail() {
        Authentication authentication = getAuthentication();

        if (authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            return jwt.getClaim("email");
        } else if (authentication.getPrincipal() instanceof User) {
            // In test environment with @WithMockUser, use username as email
            User user = (User) authentication.getPrincipal();
            return user.getUsername();
        } else {
            log.error("Unsupported principal type: {}", authentication.getPrincipal().getClass());
            throw new SecurityException("Unsupported authentication principal type");
        }
    }

    /**
     * Gets the current authenticated user's roles from JWT token or mock user
     * @return List of role names (cognito:groups claim from JWT or authorities from mock user)
     * @throws SecurityException if not authenticated
     */
    @SuppressWarnings("unchecked")
    public List<String> getCurrentUserRoles() {
        Authentication authentication = getAuthentication();

        if (authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            Object groups = jwt.getClaim("cognito:groups");

            if (groups instanceof List) {
                return (List<String>) groups;
            }

            return Collections.emptyList();
        } else if (authentication.getPrincipal() instanceof User) {
            // In test environment with @WithMockUser, extract roles from authorities
            // @WithMockUser(roles = {"ORGANIZER"}) creates authority "ROLE_ORGANIZER"
            return authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(auth -> auth.startsWith("ROLE_") ? auth.substring(5) : auth)
                    .collect(Collectors.toList());
        } else {
            log.error("Unsupported principal type: {}", authentication.getPrincipal().getClass());
            throw new SecurityException("Unsupported authentication principal type");
        }
    }

    /**
     * Checks if the current user has a specific role
     * @param role Role name to check
     * @return true if user has the role, false otherwise
     */
    public boolean hasRole(String role) {
        List<String> roles = getCurrentUserRoles();
        return roles.contains(role);
    }

    /**
     * Gets the company ID associated with the current user from JWT token
     * @return Company ID (custom:companyId claim from JWT), or null if not present or in test mode
     * @throws SecurityException if not authenticated
     */
    public String getCompanyId() {
        Authentication authentication = getAuthentication();

        if (authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            return jwt.getClaim("custom:companyId");
        } else if (authentication.getPrincipal() instanceof User) {
            // In test environment with @WithMockUser, company ID is not available
            return null;
        } else {
            log.error("Unsupported principal type: {}", authentication.getPrincipal().getClass());
            throw new SecurityException("Unsupported authentication principal type");
        }
    }

    /**
     * Gets the authenticated user from Spring Security context
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
