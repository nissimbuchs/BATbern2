package ch.batbern.events.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Registration entity representing event registrations/attendees
 * Story 1.15a.1: Events API Consolidation - AC11-12
 */
@Entity
@Table(name = "registrations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String attendeeId;

    @Column(nullable = false)
    private String attendeeName;

    @Column(nullable = false)
    private String attendeeEmail;

    @Column(nullable = false)
    private String status; // pending, confirmed, cancelled, waitlist

    @Column(nullable = false)
    private Instant registrationDate;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
