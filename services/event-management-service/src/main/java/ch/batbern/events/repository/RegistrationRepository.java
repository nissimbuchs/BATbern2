package ch.batbern.events.repository;

import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.AttendanceSummaryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Registration entities
 * Story 1.15a.1: Events API Consolidation - AC11-12
 * Story 1.16.2: Uses registrationCode and attendeeUsername as public identifiers
 */
@Repository
public interface RegistrationRepository
    extends JpaRepository<Registration, UUID>, JpaSpecificationExecutor<Registration> {

    /**
     * Find a registration by its code (public identifier)
     * Story 1.16.2: Public API uses registrationCode instead of UUID
     */
    Optional<Registration> findByRegistrationCode(String registrationCode);

    /**
     * Check if a registration code already exists
     * Story 1.16.2: For collision detection during code generation
     */
    boolean existsByRegistrationCode(String registrationCode);

    /**
     * Check if a registration already exists for a specific event and attendee
     * QA Fix (VALID-001): Duplicate registration prevention
     *
     * @param eventId Event UUID
     * @param attendeeUsername Username reference to User Management Service
     * @return true if registration exists, false otherwise
     */
    boolean existsByEventIdAndAttendeeUsername(UUID eventId, String attendeeUsername);

    /**
     * Find registration for a specific event and attendee
     * Used to check status and resend confirmation email for pending registrations
     *
     * @param eventId Event UUID
     * @param attendeeUsername Username reference to User Management Service
     * @return Registration if exists
     */
    Optional<Registration> findByEventIdAndAttendeeUsername(UUID eventId, String attendeeUsername);

    /**
     * Find all registrations for a specific event
     */
    List<Registration> findByEventId(UUID eventId);

    /**
     * Find registrations for a specific event with pagination
     * Story 3.3: Event Participants Tab - Pagination support
     *
     * @param eventId Event UUID
     * @param pageable Pagination parameters (page, size, sort)
     * @return Page of registrations for this event
     */
    Page<Registration> findByEventId(UUID eventId, Pageable pageable);

    /**
     * Find all registrations for a specific event and status
     */
    List<Registration> findByEventIdAndStatus(UUID eventId, String status);

    /**
     * Delete all registrations for a specific event
     */
    void deleteByEventId(UUID eventId);

    /**
     * Find registrations by status and created before threshold
     * Used for cleanup of unconfirmed registrations
     *
     * @param status Registration status (e.g., "registered")
     * @param threshold Instant threshold (e.g., 48 hours ago)
     * @return List of registrations matching criteria
     */
    List<Registration> findByStatusAndCreatedAtBefore(String status, Instant threshold);

    /**
     * Count registrations by status
     * Used for cleanup statistics and monitoring
     *
     * @param status Registration status
     * @return Count of registrations with given status
     */
    long countByStatus(String status);

    /**
     * Find all registrations for a specific user
     * Story BAT-15: Used by user detail page to show event participation history
     *
     * @param attendeeUsername Username reference to User Management Service
     * @return List of registrations for this user
     */
    List<Registration> findByAttendeeUsername(String attendeeUsername);

    /**
     * Count total registrations for a specific event
     * Used to display accurate registration counts on event cards and analytics
     *
     * @param eventId Event UUID
     * @return Total count of registrations for this event (all statuses)
     */
    long countByEventId(UUID eventId);

    /**
     * Find usernames of attendees registered for a specific event (by event code)
     * Story BAT-7: Used for notification delivery (deadline reminders, event updates)
     *
     * @param eventCode Event code (e.g., "BATbern123")
     * @return List of usernames registered for this event
     */
    @Query("SELECT r.attendeeUsername FROM Registration r JOIN Event e "
            + "ON r.eventId = e.id WHERE e.eventCode = :eventCode")
    List<String> findUsernamesByEventCode(@Param("eventCode") String eventCode);

    /**
     * Find registration for a specific event (by event code) and authenticated user.
     * Story 10.10: GET /events/{eventCode}/my-registration (AC1)
     *
     * Uses existing indices:
     * - idx_registrations_event_id (via JOIN to events table on event code)
     * - idx_registrations_attendee_username
     *
     * @param eventCode Event code (e.g., "BATbern142")
     * @param username  Authenticated user's username (Cognito sub or username)
     * @return Registration if found for this event and user
     */
    @Query("SELECT r FROM Registration r JOIN Event e ON r.eventId = e.id "
            + "WHERE e.eventCode = :eventCode AND r.attendeeUsername = :username")
    Optional<Registration> findByEventCodeAndAttendeeUsername(
            @Param("eventCode") String eventCode,
            @Param("username") String username);

    // ── Story 10.11: Waitlist & Capacity Methods ───────────────────────────────

    /**
     * Count active registrations (registered + confirmed) for an event.
     * T5.1 — Used for capacity enforcement in RegistrationService.createRegistration().
     */
    long countByEventIdAndStatusIn(UUID eventId, List<String> statuses);

    /**
     * Find all waitlisted registrations for an event ordered by position (FIFO).
     * T5.2 — Used for waitlist display and management.
     */
    @Query("SELECT r FROM Registration r WHERE r.eventId = :eventId AND r.status = 'waitlist' "
            + "ORDER BY r.waitlistPosition ASC")
    List<Registration> findWaitlistByEventIdOrdered(@Param("eventId") UUID eventId);

    /**
     * Find the first waitlisted registration (lowest waitlistPosition) for promotion.
     * T5.3 — Spring Data derived query: finds lowest-position waitlist entry.
     */
    Optional<Registration> findTopByEventIdAndStatusOrderByWaitlistPositionAsc(UUID eventId, String status);

    /**
     * Get the next sequential waitlist position for an event.
     * T5.4 — MAX(waitlist_position) + 1, or 1 if no waitlist entries exist yet.
     */
    @Query("SELECT COALESCE(MAX(r.waitlistPosition), 0) + 1 FROM Registration r "
            + "WHERE r.eventId = :eventId AND r.status = 'waitlist'")
    int getNextWaitlistPosition(@Param("eventId") UUID eventId);

    /**
     * Count registrations by event and single status.
     * T5.5 — Used to compute waitlistCount for event responses.
     */
    long countByEventIdAndStatus(UUID eventId, String status);

    /**
     * Attendance summary per event for a given company.
     * Story 8.1: Partner Attendance Dashboard - AC1, AC2, AC5
     *
     * Returns one row per event containing:
     * - eventCode (e.g. "BATbern142")
     * - eventDate
     * - totalAttendees: all confirmed registrations for that event
     * - companyAttendees: confirmed registrations where attendeeCompanyId = :companyId
     *
     * Only events on or after :fromDate are included.
     *
     * @param companyId  company identifier (ADR-003 meaningful ID = company name)
     * @param fromDate   earliest event date to include
     * @return list of per-event attendance summaries ordered by date descending
     */
    @Query("""
        SELECT new ch.batbern.events.dto.AttendanceSummaryDTO(
            e.eventCode,
            e.title,
            e.date,
            COUNT(r.id),
            SUM(CASE WHEN r.attendeeCompanyId = :companyId THEN 1L ELSE 0L END)
        )
        FROM Event e
        LEFT JOIN Registration r ON r.eventId = e.id AND r.status IN ('confirmed', 'attended')
        WHERE e.date >= :fromDate
        GROUP BY e.id, e.eventCode, e.title, e.date
        ORDER BY e.date DESC
        """)
    List<AttendanceSummaryDTO> findAttendanceSummary(
            @Param("companyId") String companyId,
            @Param("fromDate") Instant fromDate);
}
