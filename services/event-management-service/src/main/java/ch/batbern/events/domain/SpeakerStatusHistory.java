package ch.batbern.events.domain;

import ch.batbern.events.converter.SpeakerWorkflowStateConverter;
import ch.batbern.shared.types.SpeakerWorkflowState;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity for tracking speaker status history
 * Story 5.4: Speaker Status Management - Task 4 (GREEN Phase)
 *
 * Stores audit trail of all speaker status transitions with:
 * - Previous and new status
 * - Timestamp of change
 * - Organizer who made the change
 * - Optional reason for the change
 *
 * Table: speaker_status_history (Migration V19)
 * Database: event-management-service database (colocated with speaker_pool)
 */
@Entity
@Table(name = "speaker_status_history", indexes = {
    @Index(name = "idx_speaker_status_history_speaker_pool_id", columnList = "speaker_pool_id, changed_at")
})
@Data
public class SpeakerStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull
    @Column(name = "speaker_pool_id", nullable = false)
    private UUID speakerPoolId;

    /**
     * Session ID - null until speaker submits content (CONTENT_SUBMITTED status)
     * Story 5.5: Sessions are only created when presentation content is submitted
     */
    @Column(name = "session_id")
    private UUID sessionId;

    @NotNull
    @Column(name = "event_code", nullable = false, length = 20)
    private String eventCode;

    @NotNull
    @Column(name = "previous_status", nullable = false, length = 50)
    @Convert(converter = SpeakerWorkflowStateConverter.class)
    private SpeakerWorkflowState previousStatus;

    @NotNull
    @Column(name = "new_status", nullable = false, length = 50)
    @Convert(converter = SpeakerWorkflowStateConverter.class)
    private SpeakerWorkflowState newStatus;

    @NotNull
    @Column(name = "changed_by_username", nullable = false, length = 100)
    private String changedByUsername;

    @Size(max = 2000)
    @Column(name = "change_reason", length = 2000)
    private String changeReason;

    @NotNull
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
