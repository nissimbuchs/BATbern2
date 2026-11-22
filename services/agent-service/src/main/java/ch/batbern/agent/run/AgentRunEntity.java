package ch.batbern.agent.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/** One run of the agent loop ({@code agent_runs}, owned by agent-service). */
@Entity
@Table(name = "agent_runs")
public class AgentRunEntity {

    /** The {@code chk_agent_runs_end_detail} pattern: a code, never free text. */
    public static final Pattern END_DETAIL = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

    /** What an end detail that is not a code is stored as. */
    public static final String UNKNOWN_CODE = "UNKNOWN_CODE";

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentRunStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger", nullable = false, length = 32)
    private RunTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 32)
    private RunEndReason endReason;

    @Column(name = "end_detail", length = 64)
    private String endDetail;

    /** The run id of the CUMS grant the run acts under ({@code act.run} of its tokens). */
    @Column(name = "grant_run_id", unique = true)
    private UUID grantRunId;

    protected AgentRunEntity() {
    }

    /** A run started by {@code trigger} at {@code startedAt}; it is {@link AgentRunStatus#RUNNING}. */
    public static AgentRunEntity started(UUID id, RunTrigger trigger, Instant startedAt) {
        AgentRunEntity run = new AgentRunEntity();
        run.id = id;
        run.trigger = trigger;
        run.status = AgentRunStatus.RUNNING;
        run.startedAt = startedAt;
        return run;
    }

    /** Ends the run for {@code reason} at {@code at}; the reason fixes the end status. */
    public void end(RunEndReason reason, Instant at) {
        end(reason, null, at);
    }

    /**
     * Ends the run for {@code reason} with a machine-readable {@code detail} (a CUMS code); a detail
     * that is not a code is stored as {@value #UNKNOWN_CODE}.
     */
    public void end(RunEndReason reason, String detail, Instant at) {
        if (status != AgentRunStatus.RUNNING) {
            throw new IllegalStateException("Run " + id + " has already ended");
        }
        this.status = reason.status();
        this.endReason = reason;
        this.endDetail = detail == null ? null : (END_DETAIL.matcher(detail).matches() ? detail : UNKNOWN_CODE);
        this.endedAt = at;
    }

    /** The run acts under the CUMS grant {@code grantRunId}; set once. */
    public void recordGrant(UUID grantRunId) {
        if (this.grantRunId != null) {
            throw new IllegalStateException("Run " + id + " already has a grant");
        }
        this.grantRunId = grantRunId;
    }

    public UUID getId() {
        return id;
    }

    public AgentRunStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public RunTrigger getTrigger() {
        return trigger;
    }

    public RunEndReason getEndReason() {
        return endReason;
    }

    public String getEndDetail() {
        return endDetail;
    }

    public UUID getGrantRunId() {
        return grantRunId;
    }
}
