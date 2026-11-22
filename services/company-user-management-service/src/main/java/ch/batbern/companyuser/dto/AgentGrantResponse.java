package ch.batbern.companyuser.dto;

import ch.batbern.companyuser.domain.AgentGrant;
import ch.batbern.companyuser.domain.AgentGrantTrigger;
import ch.batbern.companyuser.domain.AgentPrincipalKind;

import java.time.Instant;

/**
 * The 201 of {@code POST /internal/agent-grants} ({@code AgentGrant} in
 * {@code cums-internal.openapi.yml}). {@code grantSecret} is in this answer only: the database keeps
 * its hash.
 */
public record AgentGrantResponse(
        String runId,
        String grantSecret,
        AgentPrincipalKind principalKind,
        String username,
        AgentGrantTrigger trigger,
        String linkedRunId,
        String jobName,
        Instant issuedAt,
        Instant expiresAt) {

    /** The answer for a freshly issued {@code grant} with its plain {@code grantSecret}. */
    public static AgentGrantResponse of(AgentGrant grant, String grantSecret) {
        return new AgentGrantResponse(grant.getRunId(), grantSecret, grant.getPrincipalKind(),
                grant.getUsername(), grant.getTrigger(), grant.getLinkedRunId(), grant.getJobName(),
                grant.getIssuedAt(), grant.getExpiresAt());
    }

    /** Never prints the secret. */
    @Override
    public String toString() {
        return "AgentGrantResponse[runId=" + runId + ", principalKind=" + principalKind
                + ", username=" + username + ", trigger=" + trigger + ", expiresAt=" + expiresAt + "]";
    }
}
