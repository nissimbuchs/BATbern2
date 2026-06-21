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
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Registration {

    /**
     * Statuses that count toward currentAttendeeCount (display purposes).
     * Includes 'attended' which is the historical import status for past events.
     * Excludes 'cancelled' to avoid inflating counts.
     */
    public static final List<String> ACTIVE_STATUSES =
            List.of("registered", "confirmed", "waitlist", "attended");

    /**
     * Statuses that count as confirmed/attending (for confirmedCount and spotsRemaining).
     * Excludes 'waitlist' (not yet confirmed) and 'cancelled'.
     */
    public static final List<String> CONFIRMED_STATUSES =
            List.of("registered", "confirmed", "attended");

    /**
     * Statuses that occupy a capacity slot for new registration enforcement.
     * Excludes 'waitlist' (pending), 'attended' (historical only), and 'cancelled'.
     */
    public static final List<String> CAPACITY_STATUSES =
            List.of("registered", "confirmed");

    /**
     * Metadata key marking a registration as programmatic (auto-created by the system rather
     * than self-registered by the attendee). Its value is the trigger source — one of the
     * speaker triggers ({@code POOL_ACCEPTED}, {@code POOL_ACCEPTED_ON_BEHALF},
     * {@code SESSION_PRIMARY_SPEAKER}, {@code SESSION_CO_SPEAKER}) or
     * {@link #TRIGGER_STAKEHOLDER_ENROLLMENT}. A registration carrying this key is NOT counted
     * as a real attendee and never blocks event deletion.
     */
    public static final String AUTO_REGISTERED_FROM_KEY = "autoRegisteredFrom";

    /**
     * {@code autoRegisteredFrom} value used by {@code RegistrationService.createInternalRegistration}
     * when an organizer/partner is auto-enrolled at event creation (AutoEnrollmentListener).
     */
    public static final String TRIGGER_STAKEHOLDER_ENROLLMENT = "STAKEHOLDER_ENROLLMENT";

    /**
     * @return true if this registration was auto-created by the system (carries the
     *     {@link #AUTO_REGISTERED_FROM_KEY} metadata marker), false if it is a real
     *     self-registered attendee.
     */
    public boolean isProgrammatic() {
        return metadata != null && metadata.containsKey(AUTO_REGISTERED_FROM_KEY);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    @JsonIgnore // Story 1.16.2: Hide internal UUID from API responses
    @EqualsAndHashCode.Include
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
     * BATbern59 badge fix: true when this registration refreshed the attendee's
     * stored company (they supplied a company differing from their profile).
     * Not persisted — surfaced in the create-registration response so the UI can
     * tell the attendee we updated their company on file.
     */
    @Transient
    private Boolean companyUpdated;

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
    private String status; // registered, confirmed, waitlist, cancelled

    /**
     * Story 10.12: Self-service deregistration token (UUID).
     * Non-expiring. Generated on registration creation. Used as the sole auth mechanism
     * for the public /deregister?token= flow. Never exposed in public API responses
     * (RegistrationResponse) — only in RegistrationAdminResponse for organizers.
     */
    @Column(name = "deregistration_token", columnDefinition = "UUID")
    @JsonIgnore // Security: never serialise to public API responses
    private UUID deregistrationToken;

    /**
     * Story 10.11: Waitlist position (1-based). Null for non-waitlist registrations.
     * Sequential — assigned at waitlist entry time. Not renumbered on cancellation.
     */
    @Column(name = "waitlist_position")
    private Integer waitlistPosition;

    @Column(nullable = false)
    private Instant registrationDate;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Unconfirmed-resend job (link-validity hardening): timestamp of the most recent automated
     * confirmation-email resend, and how many automated resends have been sent so far. Used by
     * {@link ch.batbern.events.service.RegistrationResendService} to cap resends and enforce a gap
     * between them. Null / 0 for registrations that were never auto-resent.
     */
    @Column(name = "confirmation_resent_at")
    private Instant confirmationResentAt;

    @Column(name = "confirmation_resend_count", nullable = false)
    @Builder.Default
    private Integer confirmationResendCount = 0;

    /**
     * JSONB audit metadata.
     * <p>
     * Auto-participant enrolment (spec
     * {@code _bmad-output/implementation-artifacts/spec-auto-participant-email-aliases-excel-export.md})
     * stores the trigger source here as {@code {"autoRegisteredFrom": "<trigger>"}} where trigger
     * is one of {@code POOL_ACCEPTED}, {@code POOL_ACCEPTED_ON_BEHALF},
     * {@code SESSION_PRIMARY_SPEAKER}, or {@code SESSION_CO_SPEAKER}.
     * <p>
     * Defaults to an empty map (matches the DB-side {@code DEFAULT '{}'} in V107).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @PrePersist
    protected void onCreate() {
        // Story 10.12: auto-generate deregistration token if not set (covers test builders and legacy paths)
        if (deregistrationToken == null) {
            deregistrationToken = UUID.randomUUID();
        }
        if (confirmationResendCount == null) {
            confirmationResendCount = 0;
        }
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
