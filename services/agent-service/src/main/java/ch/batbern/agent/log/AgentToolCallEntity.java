package ch.batbern.agent.log;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One tool call the model asked for: its input, and what came back ({@code agent_tool_calls}). */
@Entity
@Table(name = "agent_tool_calls")
public class AgentToolCallEntity {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "step_id", nullable = false)
    private UUID stepId;

    @Column(name = "tool_use_id", nullable = false, columnDefinition = "text")
    private String toolUseId;

    @Column(name = "tool_name", nullable = false, columnDefinition = "text")
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String input;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ToolCallStatus status;

    @Column(columnDefinition = "text")
    private String output;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected AgentToolCallEntity() {
    }

    static AgentToolCallEntity started(UUID runId, UUID stepId, String toolUseId, String toolName,
                                       String inputJson, Instant at) {
        AgentToolCallEntity call = new AgentToolCallEntity();
        call.id = UUID.randomUUID();
        call.runId = runId;
        call.stepId = stepId;
        call.toolUseId = toolUseId;
        call.toolName = toolName;
        call.input = inputJson;
        call.status = ToolCallStatus.STARTED;
        call.startedAt = at;
        return call;
    }

    void end(ToolCallStatus endStatus, String output, String error, Instant at) {
        this.status = endStatus;
        this.output = output;
        this.error = error;
        this.endedAt = at;
    }

    public UUID getId() {
        return id;
    }
}
