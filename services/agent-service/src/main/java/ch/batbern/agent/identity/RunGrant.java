package ch.batbern.agent.identity;

import java.util.Objects;
import java.util.UUID;

/**
 * A run grant of CUMS: the run id ({@code act.run} of the run's tokens) and the secret that proves
 * it on every mint. {@code toString} never shows the secret.
 *
 * @param runId       CUMS's run id of the grant
 * @param grantSecret the grant secret, returned once by CUMS
 */
public record RunGrant(UUID runId, String grantSecret) {

    public RunGrant {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(grantSecret, "grantSecret");
    }

    @Override
    public String toString() {
        return "RunGrant[runId=" + runId + ", grantSecret=****]";
    }
}
