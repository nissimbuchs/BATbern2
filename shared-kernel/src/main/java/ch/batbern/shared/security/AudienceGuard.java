package ch.batbern.shared.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Audience sends refuse agent tokens (AD-15; agentic interaction, story 7). A mail to an audience
 * (Mail Center composer send and retry, newsletter and slides-online bulk sends in EMS, partner
 * meeting invite and its cancellation in PCS) may only be started by a person: a token that carries
 * an {@code act} claim, well-formed or not ({@link Actors#carriesAct}), is refused with
 * {@link AccessDeniedException}, which each service maps to 403.
 *
 * <p>The agent is recognised by the {@code act} claim, not by the issuer. The guard is called as the
 * first statement of the service method, before any recipient lookup, render, write or SES call, so a
 * refused call leaves nothing behind. Without an authentication (scheduler, {@code @Async} thread, SQS
 * listener) and for a person's token (Cognito, Watch, a mock user) it does nothing.
 *
 * <p>Single-recipient sends (speaker invitation, conversation reply, registration confirmation
 * resend, add participant) stay open to agent tokens and do not call this guard.
 */
public final class AudienceGuard {

    private static final String MESSAGE = "Audience sends refuse agent tokens (AD-15): ";

    private AudienceGuard() {
    }

    /**
     * Refuses the current request when its token carries {@code act}.
     *
     * @param operation the refused operation, named in the message (no personal data)
     * @throws AccessDeniedException when the current authentication's token carries {@code act}
     */
    public static void refuseAgentTokens(String operation) {
        if (Actors.carriesAct(SecurityContextHolder.getContext().getAuthentication())) {
            throw new AccessDeniedException(MESSAGE + operation);
        }
    }
}
