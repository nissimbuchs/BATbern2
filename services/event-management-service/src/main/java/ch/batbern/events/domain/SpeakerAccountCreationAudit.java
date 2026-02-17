package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Story 9.2 AC4: Audit record for speaker Cognito account creation/role extension.
 *
 * Email is stored as SHA-256 hash to avoid persisting PII.
 */
@Entity
@Table(name = "speaker_account_creation_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerAccountCreationAudit {

    public enum Action {
        NEW,
        EXTENDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "speaker_pool_id", nullable = false, updatable = false)
    private UUID speakerPoolId;

    /** SHA-256 hex digest of the speaker email — stored instead of plain email (PII protection) */
    @Column(name = "email_hash", nullable = false, updatable = false, length = 64)
    private String emailHash;

    @Column(name = "cognito_user_id", updatable = false, length = 255)
    private String cognitoUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 10)
    private Action action;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onPrePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
