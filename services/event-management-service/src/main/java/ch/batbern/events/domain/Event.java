package ch.batbern.events.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Event Domain Entity
 * Represents a BATbern event with all core attributes.
 *
 * Story 1.15a.1: Events API Consolidation
 * Part of the consolidated Events API design.
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @NotNull(message = "Event date is required")
    @Column(name = "event_date", nullable = false)
    private Instant date;

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "draft|published|archived|cancelled", message = "Status must be one of: draft, published, archived, cancelled")
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "venue_id")
    private String venueId;

    @Column(name = "workflow_state", length = 50)
    private String workflowState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = "evt-" + UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
