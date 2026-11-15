package ch.batbern.events.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The caller's roles, read from the security context (platform). One place for the organizer check
 * that decides which view a read gets: sessions on the organizer endpoints, the GHSA-qhqv public
 * projection, the cache key of the event detail.
 */
public final class CallerRoles {

    private static final String ORGANIZER_AUTHORITY = "ROLE_ORGANIZER";

    private CallerRoles() {
    }

    /** True when the current caller is authenticated and holds the ORGANIZER role. */
    public static boolean isOrganizer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (ORGANIZER_AUTHORITY.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /** Cache-key suffix so organizer and non-organizer responses never share a cache entry. */
    public static String audienceCacheSuffix() {
        return isOrganizer() ? "_organizer" : "_public";
    }
}
