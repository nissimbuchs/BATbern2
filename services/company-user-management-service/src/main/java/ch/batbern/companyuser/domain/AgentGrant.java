package ch.batbern.companyuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * An agent run grant (agent identity, story 5), a row of {@code agent_grants} (V42): one run, one
 * principal, at most one hour. Immutable once issued. The grant secret itself is never stored, only
 * its SHA-256 hash; the agent token of the run (story 6) is minted only against an active grant.
 */
@Entity
@Table(name = "agent_grants")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentGrant {

    /** A random UUID string; {@code act.run} of the run's agent tokens. */
    @Id
    @Column(name = "run_id", nullable = false, updatable = false, length = 36)
    private String runId;

    @Convert(converter = AgentPrincipalKind.DbConverter.class)
    @Column(name = "principal_kind", nullable = false, updatable = false, length = 30)
    private AgentPrincipalKind principalKind;

    /** The person the run acts for. */
    @Column(name = "username", nullable = false, updatable = false, length = 100)
    private String username;

    @Convert(converter = AgentGrantTrigger.DbConverter.class)
    @Column(name = "trigger_kind", nullable = false, updatable = false, length = 30)
    private AgentGrantTrigger trigger;

    /** The requester run this run follows; {@code LINKED_RUN} only. */
    @Column(name = "linked_run_id", updatable = false, length = 36)
    private String linkedRunId;

    /** The scheduler job; {@code SCHEDULER_JOB} only. */
    @Column(name = "job_name", updatable = false, length = 100)
    private String jobName;

    /** Hex SHA-256 of the grant secret. */
    @Column(name = "secret_hash", nullable = false, updatable = false, length = 64)
    private String secretHash;

    /** Who requested it ({@code Actors}): {@code system:<job>} or the organizer's username. */
    @Column(name = "requested_by", nullable = false, updatable = false, length = 255)
    private String requestedBy;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;
}
