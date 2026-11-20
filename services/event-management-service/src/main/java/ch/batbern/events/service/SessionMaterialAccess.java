package ch.batbern.events.service;

import ch.batbern.events.domain.Session;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.security.CallerRoles;
import ch.batbern.events.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Who may see and change every material of a session, the non-public ones included (#1260): an
 * organizer, or a speaker on that session ({@code session_users}). Everyone else, anonymous callers
 * included, gets the public materials only; the public read model never gets more. The one place
 * this is decided: materials reads, the materials change check and the download use it.
 */
@Component
@RequiredArgsConstructor
public class SessionMaterialAccess {

    private final SessionUserRepository sessionUserRepository;
    private final SecurityContextHelper securityContextHelper;

    /** True for an organizer and for a speaker of the session; false for anyone else. */
    public boolean mayUseNonPublic(Session session) {
        if (CallerRoles.isOrganizer()) {
            return true;
        }
        String username = securityContextHelper.getCurrentUsernameOrNull();
        return username != null && sessionUserRepository.existsBySessionIdAndUsername(session.getId(), username);
    }
}
