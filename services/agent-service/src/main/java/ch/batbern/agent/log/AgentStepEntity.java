package ch.batbern.agent.log;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One step of a run: one model call and the tool calls it asked for ({@code agent_steps}). */
@Entity
@Table(name = "agent_steps")
public class AgentStepEntity {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected AgentStepEntity() {
    }

    static AgentStepEntity started(UUID runId, int stepNumber, Instant at) {
        AgentStepEntity step = new AgentStepEntity();
        step.id = UUID.randomUUID();
        step.runId = runId;
        step.stepNumber = stepNumber;
        step.startedAt = at;
        return step;
    }

    void end(Instant at) {
        this.endedAt = at;
    }

    public UUID getId() {
        return id;
    }
}
