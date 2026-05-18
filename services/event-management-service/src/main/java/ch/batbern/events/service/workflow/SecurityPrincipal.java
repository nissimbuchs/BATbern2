package ch.batbern.events.service.workflow;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authenticated actor passed to {@link ch.batbern.events.service.SpeakerWorkflowService#transition}.
 *
 * <p>Roles use plain strings (e.g. {@code "ORGANIZER"}, {@code "SPEAKER"}). When constructed via
 * {@link #fromAuthentication(Authentication)} the {@code "ROLE_"} prefix is stripped defensively
 * (production-path Cognito JWTs carry bare roles; test-path {@code @WithMockUser} adds the
 * prefix). When constructed directly via the record constructor, callers must pass bare roles.
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
     * through {@link GrantedAuthority#getAuthority()}, stripping the Spring {@code "ROLE_"}
     * prefix when present.
     *
     * <p>Code review 2026-05-18 (P10): the prior Javadoc claimed
     * {@link ch.batbern.events.security.SecurityContextHelper}'s resolver strips the prefix —
     * not true. Production-path Cognito JWTs carry bare roles (e.g. {@code "SPEAKER"}) directly;
     * test-path {@code @WithMockUser} adds {@code "ROLE_"}. This factory always strips so
     * {@link #hasRole(String)} works the same way from both paths without callers having to know
     * which mint produced the {@link Authentication}.
     *
     * <p>Code review 2026-05-18 (P4): explicit null/blank guard on {@code getName()} yields a
     * clearer 400 message via the {@code GlobalExceptionHandler} {@code IllegalArgumentException}
     * handler than the record constructor's generic check (which fires deeper in the call stack
     * and obscures the auth-misconfig root cause in logs).
     */
    public static SecurityPrincipal fromAuthentication(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalArgumentException(
                    "SecurityPrincipal.fromAuthentication requires a non-null Authentication");
        }
        String username = authentication.getName();
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Authentication has no usable principal name (preferred_username / sub claim"
                            + " missing) — JWT converter may be misconfigured");
        }
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
