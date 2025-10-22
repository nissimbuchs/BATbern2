package ch.batbern.companyuser.domain;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * User Sync Compensation Log Entity
 * <p>
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * AC3, AC8: Saga compensation pattern for failed Cognito syncs
 * <p>
 * Tracks compensation operations when database updates succeed but Cognito sync fails.
 * Reconciliation service retries failed compensations on schedule.
 */
@Entity
@Table(name = "user_sync_compensation_log",
        indexes = {
                @Index(name = "idx_compensation_status", columnList = "status,compensation_required"),
                @Index(name = "idx_compensation_user", columnList = "user_id"),
                @Index(name = "idx_compensation_cognito", columnList = "cognito_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSyncCompensationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "cognito_id", nullable = false, length = 255)
    private String cognitoId;

    /**
     * Operation type: ROLE_SYNC, USER_CREATE, USER_DELETE
     */
    @Column(name = "operation", nullable = false, length = 50)
    private String operation;

    /**
     * Target role for ROLE_SYNC operations
     */
    @Column(name = "target_role", length = 50)
    @Enumerated(EnumType.STRING)
    private Role targetRole;

    /**
     * Status: PENDING, COMPLETED, FAILED
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "compensation_required")
    @Builder.Default
    private Boolean compensationRequired = false;

    @Column(name = "compensation_executed_at")
    private Instant compensationExecutedAt;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @PrePersist
    public void onCreate() {
        if (attemptedAt == null) {
            attemptedAt = Instant.now();
        }
    }

    /**
     * Mark compensation as completed
     */
    public void markCompleted() {
        this.status = "COMPLETED";
        this.completedAt = Instant.now();
        this.compensationRequired = false;
    }

    /**
     * Mark compensation as failed
     *
     * @param errorMessage Error message
     */
    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.retryCount++;
        this.compensationRequired = true;
    }

    /**
     * Record compensation execution attempt
     */
    public void recordCompensationAttempt() {
        this.compensationExecutedAt = Instant.now();
        this.retryCount++;
    }
}
