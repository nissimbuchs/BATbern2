package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_generation_log")
@Getter
@Setter
public class AiGenerationLog {

    @Id
    private UUID id;

    @Column(name = "event_code")
    private String eventCode;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "input_hash", nullable = false)
    private String inputHash;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "was_accepted")
    private Boolean wasAccepted;
}
