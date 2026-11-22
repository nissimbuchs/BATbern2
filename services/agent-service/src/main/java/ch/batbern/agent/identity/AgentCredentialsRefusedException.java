package ch.batbern.agent.identity;

import ch.batbern.agent.run.RunEndReason;

/**
 * CUMS refused the run's grant or a mint, or could not be asked. The run ends with {@link #reason()}
 * (GRANT_REFUSED or TOKEN_REFUSED) and {@link #code()} as its end detail. The message carries the
 * reason and the code only: never a username, token, grant secret or caller secret.
 */
public class AgentCredentialsRefusedException extends RuntimeException {

    private final RunEndReason reason;
    private final String code;

    public AgentCredentialsRefusedException(RunEndReason reason, String code) {
        super(reason + " (" + code + ")");
        if (reason != RunEndReason.GRANT_REFUSED && reason != RunEndReason.TOKEN_REFUSED) {
            throw new IllegalArgumentException("Not a credential refusal: " + reason);
        }
        this.reason = reason;
        this.code = code;
    }

    /** GRANT_REFUSED or TOKEN_REFUSED. */
    public RunEndReason reason() {
        return reason;
    }

    /** The CUMS code, {@code HTTP_<status>}, CUMS_UNREACHABLE, CALLER_SECRET_MISSING or UNKNOWN_CODE. */
    public String code() {
        return code;
    }
}
