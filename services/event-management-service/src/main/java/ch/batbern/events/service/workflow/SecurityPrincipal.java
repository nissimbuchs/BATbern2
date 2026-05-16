package ch.batbern.events.service.workflow;

import java.util.List;

/**
 * Authenticated actor passed to {@link ch.batbern.events.service.SpeakerWorkflowService#transition}.
 *
 * <p>Roles use plain strings (e.g. {@code "ORGANIZER"}, {@code "SPEAKER"}) — the {@code "ROLE_"}
 * prefix is already stripped by {@link ch.batbern.events.security.SecurityContextHelper}.
 *
 * <p>Construction patterns per ADR-009 Story 11.B.2:
 * <ul>
 *   <li>Organizer-side: {@code new SecurityPrincipal(securityContextHelper.getCurrentUsername(),
 *       securityContextHelper.getCurrentUserRoles())}</li>
 *   <li>Speaker-side (magic-link portal): {@code new SecurityPrincipal(speaker.getUsername(),
 *       List.of("SPEAKER"))} — direct construction because the magic-link path doesn't populate
 *       Spring's {@code SecurityContext}.</li>
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
}
