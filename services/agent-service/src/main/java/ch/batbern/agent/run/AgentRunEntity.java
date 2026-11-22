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

    protected AgentRunEntity() {
    }

    /** A run that started at {@code startedAt} and is {@link AgentRunStatus#RUNNING}. */
    public static AgentRunEntity started(UUID id, Instant startedAt) {
        AgentRunEntity run = new AgentRunEntity();
        run.id = id;
        run.status = AgentRunStatus.RUNNING;
        run.startedAt = startedAt;
        return run;
    }

    /** Ends the run with {@code status} at {@code endedAt}. */
    public void end(AgentRunStatus endStatus, Instant at) {
        if (endStatus == AgentRunStatus.RUNNING) {
            throw new IllegalArgumentException("A run ends DONE, FAILED or HANDED_TO_ORGANIZERS");
        }
        this.status = endStatus;
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
}
