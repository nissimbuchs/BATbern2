package ch.batbern.partners.security;

import ch.batbern.shared.security.Actors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Helper class to extract user information from Spring Security context
 *
 * Supports both JWT tokens (production) and User objects (test with @WithMockUser)
 */
@Component
@Slf4j
public class SecurityContextHelper {

    /**
     * Gets the current authenticated user's username from JWT token or mock user
     * Used for Partner Contact management (ADR-003: meaningful IDs)
     * ADR-001: Custom claims are set by PreTokenGeneration Lambda from database
     * @return Username (custom:username claim from JWT or username from mock user)
     * @throws SecurityException if not authenticated
     */
    public String getCurrentUsername() {
        Authentication authentication = getAuthentication();

        if (authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            // ADR-001: PreTokenGeneration Lambda sets 'custom:username' claim from database
            String username = jwt.getClaim("custom:username");
            if (username == null) {
                log.warn("custom:username claim not found in JWT, falling back to subject (UUID). "
                        + "This indicates PreTokenGeneration Lambda may not be configured properly.");
                return jwt.getSubject(); // Fallback to subject for backward compatibility
            }
            return username;
        } else if (authentication.getPrincipal() instanceof User) {
            // In test environment with @WithMockUser, use username
            User user = (User) authentication.getPrincipal();
            return user.getUsername();
        } else {
            log.error("Unsupported principal type: {}", authentication.getPrincipal().getClass());
            throw new SecurityException("Unsupported authentication principal type");
        }
    }

    /**
     * The current caller as an audit column records it ({@link Actors#forUsername}): the username, or
     * {@code agent:<run> for <username>} under an agent token. Never for ownership checks.
     */
    public String getCurrentActor() {
        return Actors.forUsername(getCurrentUsername());
    }

    /**
     * Who acted: the current caller's actor ({@link #getCurrentActor()}) when someone is signed in,
     * otherwise {@code system:<job>} (scheduler, listener, a call without a user).
     *
     * @param systemJob kebab-case name of the job that writes without a user
     */
    public String actorOr(String systemJob) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Actors.system(systemJob);
        }
        try {
            return getCurrentActor();
        } catch (SecurityException e) {
            log.debug("No usable user in the security context, recording system:{}", systemJob);
            return Actors.system(systemJob);
        }
    }

    /** Whether the current caller's token carries an {@code act} claim (an agent token). */
    public boolean carriesAgentAct() {
        return Actors.carriesAct(SecurityContextHolder.getContext().getAuthentication());
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
