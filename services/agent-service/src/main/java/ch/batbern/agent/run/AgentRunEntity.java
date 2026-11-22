package ch.batbern.agent.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One run of the agent loop ({@code agent_runs}, owned by agent-service). */
@Entity
@Table(name = "agent_runs")
public class AgentRunEntity {

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
        if (status != AgentRunStatus.RUNNING) {
            throw new IllegalStateException("Run " + id + " has already ended");
        }
        this.status = reason.status();
        this.endReason = reason;
        this.endedAt = at;
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
}
