package ch.batbern.agent.identity;

import ch.batbern.agent.run.PrincipalKind;

import java.util.Objects;
import java.util.UUID;

/**
 * A run grant of CUMS: the run id ({@code act.run} of the run's tokens) and the secret that proves
 * it on every mint. {@code toString} never shows the secret.
 *
 * @param runId         CUMS's run id of the grant
 * @param grantSecret   the grant secret, returned once by CUMS
 * @param principalKind whom the run acts for, as CUMS issued the grant
 */
public record RunGrant(UUID runId, String grantSecret, PrincipalKind principalKind) {

    public RunGrant {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(grantSecret, "grantSecret");
        Objects.requireNonNull(principalKind, "principalKind");
    }

    @Override
    public String toString() {
        return "RunGrant[runId=" + runId + ", grantSecret=****, principalKind=" + principalKind + "]";
    }
}
