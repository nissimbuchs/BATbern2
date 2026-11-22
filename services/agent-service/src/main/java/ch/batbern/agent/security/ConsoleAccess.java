package ch.batbern.agent.security;

import ch.batbern.shared.security.Actors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Who may read the decision log (R8, AD-9): an organizer, as a person. The decoder already refuses
 * agent tokens (401); method security refuses any token that carries {@code act} as well (403), so
 * the agent can never read its own log even if the decoder ever accepted its kind.
 */
@Component("consoleAccess")
public class ConsoleAccess {

    /** The {@code @PreAuthorize} rule of every read operation of {@code agent-api.openapi.yml}. */
    public static final String ORGANIZER_PERSON =
            "hasRole('ORGANIZER') and !@consoleAccess.carriesAct(authentication)";

    /** Whether the token of {@code authentication} carries an {@code act} claim. */
    public boolean carriesAct(Authentication authentication) {
        return Actors.carriesAct(authentication);
    }
}
