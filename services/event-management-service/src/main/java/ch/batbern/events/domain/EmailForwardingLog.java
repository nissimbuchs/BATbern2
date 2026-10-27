package ch.batbern.events.domain;

import ch.batbern.events.converter.EmailForwardingOutcomeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency and audit record of one forwarded inbound mail (one row per S3 object).
 * Holds no addresses or content; see {@code V128__email_forwarding_log.sql}.
 */
@Entity
@Table(name = "email_forwarding_log")
@Getter
@Setter
public class EmailForwardingLog {

    @Id
    private UUID id;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "sending_started_at")
    private Instant sendingStartedAt;

    @Column(name = "aliases")
    private String aliases;

    @Convert(converter = EmailForwardingOutcomeConverter.class)
    @Column(name = "outcome", nullable = false, length = 20)
    private EmailForwardingOutcome outcome;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;
}
