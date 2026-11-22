package ch.batbern.agent.identity;

import ch.batbern.agent.run.PrincipalKind;
import ch.batbern.agent.run.RunEndReason;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * The identity of one run (story 4, R2): its CUMS grant and the agent token it acts with.
 *
 * <p>A read reuses the current token until {@value #REUSE_MARGIN_SECONDS} s before its
 * {@code expiresAt}; a write never reuses one, it mints a fresh token first. The first refusal is
 * kept: every later call throws it again without asking CUMS, and the loop reads it through
 * {@link #refusal()} after each tool, so a tool that swallows the exception cannot hide it.
 *
 * <p>One instance per run; the loop runs a run's tools one after another, the methods are
 * synchronized anyway.
 */
public final class RunCredentials {

    /** A read mints first when the token expires within this many seconds. */
    public static final long REUSE_MARGIN_SECONDS = 60;
    private static final Duration REUSE_MARGIN = Duration.ofSeconds(REUSE_MARGIN_SECONDS);

    private final CumsAgentClient cums;
    private final Clock clock;
    private final RunGrant grant;
    private AgentToken current;
    private AgentCredentialsRefusedException refusal;

    private RunCredentials(CumsAgentClient cums, Clock clock, RunGrant grant) {
        this.cums = cums;
        this.clock = clock;
        this.grant = grant;
    }

    /**
     * Obtains the run's grant: from CUMS, or as handed over by EMS (no grant call). Mints nothing
     * yet; the caller records {@link #grantRunId()} and then calls {@link #start()}.
     *
     * @throws AgentCredentialsRefusedException GRANT_REFUSED: CUMS refused or could not be asked,
     *                                          or no caller secret is installed (CUMS not called)
     */
    public static RunCredentials obtain(CumsAgentClient cums, GrantSource source, Clock clock) {
        if (!cums.hasCallerSecret()) {
            throw new AgentCredentialsRefusedException(RunEndReason.GRANT_REFUSED,
                    CumsAgentClient.CALLER_SECRET_MISSING);
        }
        RunGrant grant = source instanceof GrantSource.HandedOver handedOver
                // CUMS issues EMS_HANDOFF grants for the requester only (AgentGrantTrigger)
                ? new RunGrant(handedOver.runId(), handedOver.grantSecret(), PrincipalKind.REQUESTER)
                : cums.grant(source);
        return new RunCredentials(cums, clock, grant);
    }

    /** CUMS's run id of the grant: {@code act.run} of the run's tokens, stored on the run. */
    public UUID grantRunId() {
        return grant.runId();
    }

    /** Whom the run acts for, as its grant says; stored on the run. */
    public PrincipalKind principalKind() {
        return grant.principalKind();
    }

    /**
     * Mints the run's first token, before its first model call.
     *
     * @throws AgentCredentialsRefusedException TOKEN_REFUSED
     */
    public synchronized void start() {
        mint();
    }

    /** A token for a GET: the current one unless it expires within the margin. */
    public synchronized String tokenForRead() {
        throwIfRefused();
        if (current == null || !clock.instant().isBefore(current.expiresAt().minus(REUSE_MARGIN))) {
            mint();
        }
        return current.token();
    }

    /** A token for a write (every non-GET): always freshly minted. */
    public synchronized String tokenForWrite() {
        throwIfRefused();
        mint();
        return current.token();
    }

    /** The refusal that ended the run's credentials, if any. */
    public synchronized Optional<AgentCredentialsRefusedException> refusal() {
        return Optional.ofNullable(refusal);
    }

    private void mint() {
        try {
            current = cums.mint(grant);
        } catch (AgentCredentialsRefusedException e) {
            current = null;
            refusal = e;
            throw e;
        }
    }

    private void throwIfRefused() {
        if (refusal != null) {
            throw new AgentCredentialsRefusedException(refusal.reason(), refusal.code());
        }
    }

    @Override
    public String toString() {
        return "RunCredentials[grantRunId=" + grant.runId() + "]";
    }
}
