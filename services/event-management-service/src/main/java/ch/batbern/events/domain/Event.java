package ch.batbern.events.domain;

import ch.batbern.events.converter.EventTypeConverter;
import ch.batbern.events.converter.EventWorkflowStateConverter;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.shared.types.EventWorkflowState;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "UUID")
    private UUID id;

    @NotBlank(message = "Event code is required")
    @Size(max = 50, message = "Event code must not exceed 50 characters")
    @Column(name = "event_code", nullable = false, unique = true, length = 50)
    private String eventCode;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @NotNull(message = "Event number is required")
    @Column(name = "event_number", nullable = false, unique = true)
    private Integer eventNumber;

    @NotNull(message = "Event date is required")
    @Column(name = "event_date", nullable = false)
    private Instant date;

    @NotNull(message = "Registration deadline is required")
    @Column(name = "registration_deadline", nullable = false)
    private Instant registrationDeadline;

    @NotBlank(message = "Venue name is required")
    @Column(name = "venue_name", nullable = false)
    private String venueName;

    @NotBlank(message = "Venue address is required")
    @Column(name = "venue_address", nullable = false, columnDefinition = "TEXT")
    private String venueAddress;

    @NotNull(message = "Venue capacity is required")
    @Column(name = "venue_capacity", nullable = false)
    private Integer venueCapacity;

    @NotBlank(message = "Organizer username is required")
    @Column(name = "organizer_username", nullable = false, length = 100)
    private String organizerUsername;

    @Column(name = "current_attendee_count")
    private Integer currentAttendeeCount;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "metadata", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String metadata;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "theme_image_url", length = 1000)
    private String themeImageUrl;

    @Column(name = "theme_image_upload_id", length = 100)
    private String themeImageUploadId;

    @NotNull(message = "Event type is required")
    @Convert(converter = EventTypeConverter.class)
    @Column(name = "event_type", nullable = false, length = 20)
    private EventType eventType;

    @NotNull(message = "Workflow state is required")
    @Convert(converter = EventWorkflowStateConverter.class)
    @Column(name = "workflow_state", nullable = false, length = 50)
    private EventWorkflowState workflowState;

    @Column(name = "topic_code", length = 100)
    private String topicCode;

    @Column(name = "current_published_phase", length = 50)
    private String currentPublishedPhase; // none, topic, speakers, agenda

    @Column(name = "last_published_at")
    private Instant lastPublishedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        // Set default workflow state if not already set
        if (workflowState == null) {
            workflowState = EventWorkflowState.CREATED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
