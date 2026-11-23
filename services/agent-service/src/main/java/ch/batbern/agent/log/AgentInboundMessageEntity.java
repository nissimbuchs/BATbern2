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

/** One message a run was given ({@code agent_inbound_messages}). */
@Entity
@Table(name = "agent_inbound_messages")
public class AgentInboundMessageEntity {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InboundChannel channel;

    @Column(name = "external_id", length = 512)
    private String externalId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** The whole message, JSON text. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String content;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected AgentInboundMessageEntity() {
    }

    static AgentInboundMessageEntity of(UUID runId, InboundMessage message, String contentJson, Instant recordedAt) {
        AgentInboundMessageEntity entity = new AgentInboundMessageEntity();
        entity.id = UUID.randomUUID();
        entity.runId = runId;
        entity.channel = message.channel();
        entity.externalId = LlmJson.storable(message.externalId());
        entity.receivedAt = message.receivedAt();
        entity.content = contentJson;
        entity.recordedAt = recordedAt;
        return entity;
    }

    public UUID getId() {
        return id;
    }
}
