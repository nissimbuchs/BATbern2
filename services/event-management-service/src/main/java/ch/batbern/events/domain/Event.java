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
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "UUID")
    private UUID id;

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

    @NotBlank(message = "Status is required")
    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @NotNull(message = "Organizer ID is required")
    @Column(name = "organizer_id", nullable = false, columnDefinition = "UUID")
    private UUID organizerId;

    @Column(name = "current_attendee_count")
    private Integer currentAttendeeCount;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "metadata", columnDefinition = "JSONB")
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

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
