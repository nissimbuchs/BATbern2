package ch.batbern.migration.model.target;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Migration Error
 *
 * Logs migration errors for manual review and retry.
 * Supports error analysis and rollback procedures.
 *
 * Story: 3.2.1 - Migration Tool Implementation
 */
@Entity
@Table(name = "migration_errors")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MigrationError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_execution_id")
    private Long jobExecutionId;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "legacy_id")
    private String legacyId;

    @Column(name = "phase", length = 20)
    private String phase; // READ, PROCESS, WRITE

    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved")
    private Boolean resolved = false;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating error records
     */
    public MigrationError(Long jobExecutionId, String entityType, String legacyId,
                         String errorMessage, String stackTrace) {
        this.jobExecutionId = jobExecutionId;
        this.entityType = entityType;
        this.legacyId = legacyId;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
    }
}
