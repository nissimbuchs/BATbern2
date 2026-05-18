package ch.batbern.events.service.workflow;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authenticated actor passed to {@link ch.batbern.events.service.SpeakerWorkflowService#transition}.
 *
 * <p>Roles use plain strings (e.g. {@code "ORGANIZER"}, {@code "SPEAKER"}) — the {@code "ROLE_"}
 * prefix is already stripped by {@link ch.batbern.events.security.SecurityContextHelper}.
 *
 * <p>Construction patterns per ADR-009 Story 11.B.2 + 11.E.3:
 * <ul>
 *   <li>Organizer-side: {@code new SecurityPrincipal(securityContextHelper.getCurrentUsername(),
 *       securityContextHelper.getCurrentUserRoles())}</li>
 *   <li>Speaker-portal (Cognito-authenticated, Story 11.E.3): use
 *       {@link #fromAuthentication(Authentication)} from a controller method that
 *       takes a Spring {@code Authentication} parameter.</li>
 *   <li>Speaker-side (legacy magic-link portal — kept while Phase F is pending): direct
 *       {@code new SecurityPrincipal(speaker.getUsername(), List.of("SPEAKER"))}.</li>
 * </ul>
 */
public record SecurityPrincipal(String username, List<String> roles) {

    /**
     * Validation: {@code username} must be non-null and non-blank because it is persisted as
     * {@code speaker_status_history.changed_by_username} (NOT NULL, VARCHAR(100)). Without this
     * guard, a transition originated from an auth-misconfig path would silently rollback at
     * INSERT time with a NOT NULL violation, masking the real error.
     */
    public SecurityPrincipal {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "SecurityPrincipal.username must be non-null and non-blank");
        }
        if (username.length() > 100) {
            throw new IllegalArgumentException(
                    "SecurityPrincipal.username must be ≤100 chars (speaker_status_history limit)");
        }
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /**
     * Story 11.E.3: build a {@code SecurityPrincipal} from a Spring {@code Authentication}.
     * Reads the username from {@link Authentication#getName()} (the Cognito {@code preferred_username}
     * claim, populated by {@code JwtAuthenticationConverter}) and maps every granted authority
     * through {@link GrantedAuthority#getAuthority()}.
     *
     * <p>Authority strings come from {@link ch.batbern.events.security.SecurityContextHelper}'s
     * resolver, which strips the Spring {@code "ROLE_"} prefix already, so callers see plain role
     * names matching {@link #hasRole(String)}'s expectation.
     */
    public static SecurityPrincipal fromAuthentication(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalArgumentException(
                    "SecurityPrincipal.fromAuthentication requires a non-null Authentication");
        }
        String username = authentication.getName();
        List<String> authorities =
                authentication.getAuthorities() == null
                        ? List.of()
                        : authentication.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .map(SecurityPrincipal::stripRolePrefix)
                                .toList();
        return new SecurityPrincipal(username, authorities);
    }

    private static String stripRolePrefix(String authority) {
        return authority != null && authority.startsWith("ROLE_") ? authority.substring(5) : authority;
    }
}
