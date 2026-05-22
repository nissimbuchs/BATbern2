package ch.batbern.events.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper class to extract user information from Spring Security context
 * Story 1.15a.1: Events API Consolidation
 *
 * Supports both JWT tokens (production) and User objects (test with @WithMockUser)
 */
@Component
@Slf4j
public class SecurityContextHelper {

    /**
     * Pattern 3b twin for the {@code custom:username} claim.
     *
     * <p>Same root cause as {@code JwtRolesConverter}: in local dev, CUMS provisions a
     * speaker by creating the Cognito user in staging while writing {@code user_profiles}
     * into the LOCAL DB. The PreTokenGen Lambda runs against the staging DB, finds nothing,
     * and the JWT comes back with {@code custom:username = ""} (empty string, not null).
     * Without this fallback every locally-promoted speaker sees an empty dashboard because
     * {@code sessionUserRepository.findByUsername("")} matches nothing. Looks the user up
     * by {@code cognito_user_id == jwt.subject} against the local DB. In staging the JWT
     * always carries a non-empty username so this branch is dormant.
     */
    private static final String USERNAME_LOOKUP_SQL =
            "SELECT username FROM user_profiles WHERE cognito_user_id = ?";

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public SecurityContextHelper(DataSource dataSource) {
        this.jdbcTemplate = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    /** No-args constructor for test slices without a DataSource. */
    public SecurityContextHelper() {
        this.jdbcTemplate = null;
    }

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
        } else if (authentication.getPrincipal() instanceof String) {
            // Fallback for simple test authentication with String principal
            return (String) authentication.getPrincipal();
        } else {
            log.error("Unsupported principal type: {}", authentication.getPrincipal().getClass());
            throw new SecurityException("Unsupported authentication principal type");
        }
    }

    /**
     * Gets the current authenticated user's username from JWT token or mock user
     * Used for task assignment and other username-based operations (ADR-003: meaningful IDs)
     * ADR-001: Custom claims are set by PreTokenGeneration Lambda from database
     * @return Username (custom:username claim from JWT or username from mock user)
     * @throws SecurityException if not authenticated
     */
    public String getCurrentUsername() {
        Authentication authentication = getAuthentication();

        if (authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            // ADR-001: PreTokenGeneration Lambda sets 'custom:username' claim from database.
            String username = jwt.getClaim("custom:username");
            if (username != null && !username.isBlank()) {
                return username;
            }
            // Local-dev twin of Pattern 3b (see field-level javadoc). The Lambda runs
            // against staging DB and emits an empty claim for locally-provisioned users;
            // resolve via user_profiles.cognito_user_id instead. Falls back to the raw
            // subject (UUID) only if the DB lookup is unavailable or finds nothing —
            // matches the legacy behaviour rather than throwing.
            String resolved = lookupUsernameByCognitoUserId(jwt.getSubject());
            if (resolved != null) {
                return resolved;
            }
            log.warn("custom:username claim is missing or blank and DB fallback found "
                    + "no user_profiles row for cognito_user_id={}; falling back to "
                    + "subject (UUID). PreTokenGeneration Lambda may not be configured.",
                    jwt.getSubject());
            return jwt.getSubject();
        } else if (authentication.getPrincipal() instanceof User) {
            // In test environment with @WithMockUser, use username
            User user = (User) authentication.getPrincipal();
            return user.getUsername();
        } else if (authentication.getPrincipal() instanceof String) {
            // Fallback for simple test authentication with String principal
            return (String) authentication.getPrincipal();
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
        } else if (authentication.getPrincipal() instanceof String) {
            // Fallback for simple test authentication with String principal
            return (String) authentication.getPrincipal();
        } else {
            log.error("Unsupported principal type: {}", authentication.getPrincipal().getClass());
            throw new SecurityException("Unsupported authentication principal type");
        }
    }

    /**
     * Gets the current authenticated user's roles from JWT token or mock user
     * ADR-001: Custom claims are set by PreTokenGeneration Lambda from database (not Cognito Groups)
     * @return List of role names (custom:role claim from JWT or authorities from mock user)
     * @throws SecurityException if not authenticated
     */
    @SuppressWarnings("unchecked")
    public List<String> getCurrentUserRoles() {
        Authentication authentication = getAuthentication();

        if (authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            // ADR-001: PreTokenGeneration Lambda sets 'custom:role' claim from database
            String rolesString = jwt.getClaim("custom:role");

            if (rolesString != null && !rolesString.isEmpty()) {
                // Split comma-separated roles (e.g., "ORGANIZER,SPEAKER")
                return List.of(rolesString.split(","))
                        .stream()
                        .map(String::trim)
                        .collect(Collectors.toList());
            }

            return Collections.emptyList();
        } else if (authentication.getPrincipal() instanceof User) {
            // In test environment with @WithMockUser, extract roles from authorities
            // @WithMockUser(roles = {"ORGANIZER"}) creates authority "ROLE_ORGANIZER"
            return authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(auth -> auth.startsWith("ROLE_") ? auth.substring(5) : auth)
                    .collect(Collectors.toList());
        } else if (authentication.getPrincipal() instanceof String) {
            // Fallback for simple test authentication with String principal
            // Return empty roles list (test can set authorities directly if roles are needed)
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

    /**
     * Pattern 3b twin: look up a username by Cognito user id when the JWT carries an
     * empty {@code custom:username} claim. Returns {@code null} on any miss (no
     * DataSource, no match, DB error) — caller decides how to degrade.
     */
    private String lookupUsernameByCognitoUserId(String cognitoUserId) {
        if (cognitoUserId == null || cognitoUserId.isBlank() || jdbcTemplate == null) {
            return null;
        }
        try {
            List<String> matches =
                    jdbcTemplate.queryForList(USERNAME_LOOKUP_SQL, String.class, cognitoUserId);
            if (matches.isEmpty()) {
                return null;
            }
            String resolved = matches.get(0);
            log.info("JWT username fallback hit for cognito_user_id={} → username={} "
                    + "(local-dev path; staging JWTs always carry custom:username).",
                    cognitoUserId, resolved);
            return resolved;
        } catch (Exception e) {
            log.warn("JWT username fallback failed for cognito_user_id={}: {}",
                    cognitoUserId, e.getMessage());
            return null;
        }
    }
}
