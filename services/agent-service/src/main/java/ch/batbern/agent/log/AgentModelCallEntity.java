package ch.batbern.agent.log;

import ch.batbern.agent.loop.LlmStopReason;
import ch.batbern.agent.loop.LlmUsage;
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

/** One call of the model: the full prompt, the answer, the tool choice and the tokens ({@code agent_model_calls}). */
@Entity
@Table(name = "agent_model_calls")
public class AgentModelCallEntity {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "step_id", nullable = false)
    private UUID stepId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ModelCallStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String prompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String response;

    @Column(length = 128)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_reason", length = 32)
    private LlmStopReason stopReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "chosen_tools", columnDefinition = "jsonb")
    private String chosenTools;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "cache_creation_input_tokens")
    private Long cacheCreationInputTokens;

    @Column(name = "cache_read_input_tokens")
    private Long cacheReadInputTokens;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected AgentModelCallEntity() {
    }

    static AgentModelCallEntity started(UUID runId, UUID stepId, String promptJson, Instant at) {
        AgentModelCallEntity call = new AgentModelCallEntity();
        call.id = UUID.randomUUID();
        call.runId = runId;
        call.stepId = stepId;
        call.status = ModelCallStatus.STARTED;
        call.prompt = promptJson;
        call.startedAt = at;
        return call;
    }

    void succeed(String responseJson, String model, LlmStopReason stopReason, String chosenToolsJson,
                 LlmUsage usage, Instant at) {
        this.status = ModelCallStatus.SUCCEEDED;
        this.response = responseJson;
        this.model = model;
        this.stopReason = stopReason;
        this.chosenTools = chosenToolsJson;
        this.inputTokens = usage.inputTokens();
        this.outputTokens = usage.outputTokens();
        this.cacheCreationInputTokens = usage.cacheCreationInputTokens();
        this.cacheReadInputTokens = usage.cacheReadInputTokens();
        this.endedAt = at;
    }

    void fail(String error, Instant at) {
        this.status = ModelCallStatus.FAILED;
        this.error = error;
        this.endedAt = at;
    }

    public UUID getId() {
        return id;
    }
}
