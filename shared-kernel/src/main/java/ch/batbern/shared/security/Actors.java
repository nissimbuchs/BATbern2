package ch.batbern.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Who acted, as recorded in an audit column (agentic interaction, story 0.2; module map, "Services:
 * who may call whom"). One convention for every service:
 *
 * <ul>
 *   <li>{@code agent:<run> for <username>}: the caller's token carries an {@code act} claim (an
 *       agent token, {@link AgentTokenValidator}); {@code run} is {@code act.run}, {@code username}
 *       is the token's {@code custom:username}, the person the agent acts for;</li>
 *   <li>the plain username: a person signed in with their own token (Cognito, Watch, a test user);</li>
 *   <li>{@code system:<job>}: no user at all (scheduler, SQS listener, anonymous flow).</li>
 * </ul>
 *
 * <p>Who-acted values are wrapped <b>at the sink</b>, where an audit column is written or the value
 * leaves the service ({@code performedBy}). Identity and ownership never use this class: they compare
 * and store the username ({@code requester_username}, {@code UserOwnership}, {@code findBy…Username}),
 * so an agent acting for a person passes the same ownership checks as that person.
 * {@link #forUsername} is idempotent, so wrapping a value twice on its way down is harmless.
 *
 * <p>The agent is recognised by the {@code act} claim, not by the issuer: the claim is what the
 * audience guard (story 7) checks as well ({@link #carriesAct}).
 */
public final class Actors {

    /** Prefix of an agent actor: {@code agent:<run> for <username>}. */
    public static final String AGENT_PREFIX = "agent:";
    /** Prefix of an actor without a user: {@code system:<job>}. */
    public static final String SYSTEM_PREFIX = "system:";

    private static final String FOR = " for ";
    private static final String ACT_CLAIM = "act";
    private static final String USERNAME_CLAIM = "custom:username";
    private static final Pattern JOB = Pattern.compile("[a-z0-9-]+");

    private Actors() {
    }

    /**
     * The {@code act} claim of an agent token.
     *
     * @param run       the agent run ({@code act.run})
     * @param principal who the agent acts as ({@code act.principal})
     * @param username  the person it acts for ({@code custom:username})
     */
    public record AgentAct(String run, String principal, String username) {
    }

    /**
     * The agent act of {@code jwt}: present when it carries an {@code act} object with a non-blank
     * {@code run} and a non-blank {@code custom:username}, empty otherwise.
     */
    public static Optional<AgentAct> agentAct(Jwt jwt) {
        Map<String, Object> claims = jwt != null ? jwt.getClaims() : null;
        if (claims == null || !(claims.get(ACT_CLAIM) instanceof Map<?, ?> act)) {
            return Optional.empty();
        }
        String run = text(act.get("run"));
        String username = text(claims.get(USERNAME_CLAIM));
        if (run == null || username == null) {
            return Optional.empty();
        }
        return Optional.of(new AgentAct(run, text(act.get("principal")), username));
    }

    /** Whether {@code authentication}'s token carries an {@code act} claim at all (well-formed or not). */
    public static boolean carriesAct(Authentication authentication) {
        Jwt jwt = jwtOf(authentication);
        return jwt != null && jwt.hasClaim(ACT_CLAIM);
    }

    /** The agent act of the current request's token, empty for a person, a mock user or no user. */
    public static Optional<AgentAct> currentAgentAct() {
        return agentAct(jwtOf(SecurityContextHolder.getContext().getAuthentication()));
    }

    /**
     * The actor to record for a who-acted value {@code value} (a username) in the current request:
     * {@code null} stays {@code null}; a value already starting with {@value #AGENT_PREFIX} or
     * {@value #SYSTEM_PREFIX} is returned unchanged; under an agent token the agent actor
     * ({@code agent:<run> for <custom:username>}); otherwise {@code value}.
     */
    public static String forUsername(String value) {
        if (value == null || value.startsWith(AGENT_PREFIX) || value.startsWith(SYSTEM_PREFIX)) {
            return value;
        }
        return currentAgentAct().map(Actors::format).orElse(value);
    }

    /**
     * The actor of a token: the agent actor when it carries an agent act, else {@code custom:username}
     * when non-blank, else the subject.
     */
    public static String forJwt(Jwt jwt) {
        Optional<AgentAct> act = agentAct(jwt);
        if (act.isPresent()) {
            return format(act.get());
        }
        String username = text(jwt.getClaimAsString(USERNAME_CLAIM));
        return username != null ? username : jwt.getSubject();
    }

    /**
     * The person behind a recorded actor, for code that must turn an audit value back into an
     * identity (a mail sender, a lookup): {@code agent:<run> for <username>} gives the username, a
     * plain username itself, {@code system:<job>} and {@code null} give {@code null}.
     */
    public static String usernameOf(String actor) {
        if (actor == null || actor.startsWith(SYSTEM_PREFIX)) {
            return null;
        }
        if (actor.startsWith(AGENT_PREFIX)) {
            int separator = actor.lastIndexOf(FOR);
            return separator < 0 ? null : actor.substring(separator + FOR.length());
        }
        return actor;
    }

    /**
     * {@code system:<job>} for a write without a user.
     *
     * @throws IllegalArgumentException when {@code job} is blank or not kebab case ({@code [a-z0-9-]+})
     */
    public static String system(String job) {
        if (job == null || !JOB.matcher(job).matches()) {
            throw new IllegalArgumentException("A system job is kebab case ([a-z0-9-]+): " + job);
        }
        return SYSTEM_PREFIX + job;
    }

    private static String format(AgentAct act) {
        return AGENT_PREFIX + act.run() + FOR + act.username();
    }

    private static Jwt jwtOf(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof Jwt jwt ? jwt : null;
    }

    private static String text(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}
