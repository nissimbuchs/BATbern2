package ch.batbern.events.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Registration entity representing event registrations/attendees
 * <p>
 * Story 1.15a.1: Events API Consolidation - AC11-12
 * Story 1.16.2: Uses registrationCode and attendeeUsername as public identifiers
 * Story 2.2a: Anonymous Event Registration (ADR-005)
 * <p>
 * ADR-004: Factor User Fields from Domain Entities
 * - User details fetched via UserApiClient from User Management Service
 * - Denormalized search fields (name, email, company) for database-level filtering
 * - Full enrichment still required for API responses
 * <p>
 * ADR-005: Anonymous Event Registration
 * - Supports anonymous users (cognito_id=NULL in user_profiles)
 * - Registration links to user via attendeeUsername
 * - User details enriched at API response time
 * <p>
 * Performance Optimization:
 * - Denormalized attendee search fields enable database-level filtering before pagination
 * - Prevents N+1 queries and in-memory filtering of large result sets
 * - Fields updated on registration creation and user profile updates
 */
@Entity
@Table(
        name = "registrations",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_registration_event_attendee",
                    columnNames = {
                        "event_id",
                        "attendee_username"
                    }
                )
        },
        indexes = {
            @Index(name = "idx_registration_code", columnList = "registration_code", unique = true),
            @Index(name = "idx_event_id", columnList = "event_id"),
            @Index(name = "idx_attendee_username", columnList = "attendee_username"),
            @Index(name = "idx_attendee_email", columnList = "attendee_email"),
            @Index(name = "idx_attendee_company_id", columnList = "attendee_company_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    @JsonIgnore // Story 1.16.2: Hide internal UUID from API responses
    private UUID id;

    /**
     * ADR-003: Public meaningful identifier for registration
     * Format: {eventCode}-reg-{random} (e.g., "BATbern142-reg-abc123")
     */
    @Column(name = "registration_code", nullable = false, unique = true, length = 100)
    private String registrationCode;

    @Column(nullable = false, columnDefinition = "UUID")
    @JsonIgnore // Story 1.16.2: Hide internal UUID from API responses
    private UUID eventId;

    @Transient
    private String eventCode; // Not persisted - populated from path parameter for API responses

    /**
     * ADR-004: Cross-service reference to user_profiles.username
     * ADR-005: Links to anonymous or authenticated users
     * <p>
     * This is NOT a foreign key (crosses service boundary).
     * User details (name, email, company) must be fetched via UserApiClient.
     * Format: firstname.lastname or email prefix for anonymous users
     */
    @Column(name = "attendee_username", nullable = false, length = 100)
    private String attendeeUsername;

    /**
     * Denormalized search fields for database-level filtering (Performance Optimization)
     * <p>
     * These fields enable database-level search and filtering before pagination,
     * preventing the need to fetch ALL registrations and enrich with HTTP calls.
     * Updated on registration creation and user profile changes.
     * <p>
     * Full user enrichment still required for API responses (via UserApiClient).
     */
    @Column(name = "attendee_first_name", length = 100)
    private String attendeeFirstName;

    @Column(name = "attendee_last_name", length = 100)
    private String attendeeLastName;

    @Column(name = "attendee_email", length = 255)
    private String attendeeEmail;

    @Column(name = "attendee_company_id", length = 100)
    private String attendeeCompanyId;

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
