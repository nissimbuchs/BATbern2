package ch.batbern.events.repository;

import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.AttendanceSummaryDTO;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
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
     * Find all registrations for a specific event whose status is in the given set.
     * Story 7.3: used to resolve the "active registrants" (registered/confirmed) recipient
     * list for the slides-online mail.
     */
    List<Registration> findByEventIdAndStatusIn(UUID eventId, java.util.Collection<String> statuses);

    /**
     * Delete all registrations for a specific event
     */
    void deleteByEventId(UUID eventId);

    /**
     * Find unconfirmed registrations whose most-recently-minted confirmation link is older than the
     * threshold. The link timestamp is the last resend ({@code confirmationResentAt}) if the row was
     * ever auto-resent, otherwise the original {@code createdAt}.
     *
     * <p>Keying cleanup off the last resend — not {@code createdAt} — is required because
     * {@code RegistrationResendService} mints a <em>fresh</em> full-validity confirmation token on
     * each nudge. Deleting purely by {@code createdAt} can orphan a link that was just emailed and is
     * still valid for days (the 2026-06-08 "Confirmation Failed" incident). This query keeps the
     * cleanup-window invariant intact relative to the actual link lifetime.
     *
     * @param status    registration status to scan (e.g. "registered")
     * @param threshold rows whose last link predates this instant are eligible for deletion
     * @return matching registrations
     */
    @Query("SELECT r FROM Registration r WHERE r.status = :status "
            + "AND COALESCE(r.confirmationResentAt, r.createdAt) < :threshold")
    List<Registration> findUnconfirmedForCleanup(
            @Param("status") String status,
            @Param("threshold") Instant threshold);

    /**
     * Find registrations eligible for an automated confirmation-email resend
     * (used by {@code RegistrationResendService}).
     *
     * Eligible = still pending ("registered"), unconfirmed longer than the grace window
     * ({@code createdAt <= eligibleBefore}), still within the active window
     * ({@code createdAt > notExpiredAfter} — not already past the cleanup horizon), under the resend
     * cap ({@code confirmationResendCount < maxAttempts}), and either never resent or last resent on
     * or before {@code resentBefore} (enforces a minimum gap between resends).
     *
     * @param eligibleBefore registrations created at/after this instant are too new to nudge
     * @param notExpiredAfter registrations created on/before this instant are effectively expired
     * @param resentBefore   last-resend must be on/before this instant (or null)
     * @param maxAttempts    resend cap
     * @return matching registrations
     */
    @Query("SELECT r FROM Registration r WHERE r.status = 'registered' "
            + "AND r.createdAt <= :eligibleBefore "
            + "AND r.createdAt > :notExpiredAfter "
            + "AND r.confirmationResendCount < :maxAttempts "
            + "AND (r.confirmationResentAt IS NULL OR r.confirmationResentAt <= :resentBefore)")
    List<Registration> findResendEligible(
            @Param("eligibleBefore") Instant eligibleBefore,
            @Param("notExpiredAfter") Instant notExpiredAfter,
            @Param("resentBefore") Instant resentBefore,
            @Param("maxAttempts") int maxAttempts);

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

    // ── Story 10.12: Deregistration Methods ───────────────────────────────────

    /**
     * Find a registration by its self-service deregistration token.
     * Story 10.12 (T4.1): Token-based lookup for the public deregistration flow.
     *
     * @param token Deregistration UUID token
     * @return Registration if found (may be cancelled)
     */
    Optional<Registration> findByDeregistrationToken(UUID token);

    /**
     * Find a registration by attendee email and event code.
     * Story 10.12 (T4.2): Used for the by-email deregistration flow.
     *
     * @param email     Attendee email (denormalized search field)
     * @param eventCode Event code (joined from events table)
     * @return Registration if found for this email and event
     */
    @Query("SELECT r FROM Registration r JOIN Event e ON r.eventId = e.id "
            + "WHERE r.attendeeEmail = :email AND e.eventCode = :eventCode")
    Optional<Registration> findByAttendeeEmailAndEventCode(
            @Param("email") String email,
            @Param("eventCode") String eventCode);

    // ── Story 10.11: Waitlist & Capacity Methods ───────────────────────────────

    /**
     * Count active registrations (registered + confirmed) for an event.
     * T5.1 — Used for capacity enforcement in RegistrationService.createRegistration().
     */
    long countByEventIdAndStatusIn(UUID eventId, List<String> statuses);

    /**
     * Count *real* attendees for an event: active-status registrations that are NOT programmatic
     * (i.e. carry no {@code autoRegisteredFrom} metadata marker). Programmatic enrollments —
     * organizers/partners auto-enrolled at event creation and auto-registered speakers — are
     * excluded. Drives EventResponse.realAttendeeCount and the deleteEvent 409 guard.
     * <p>
     * Native query: uses {@code jsonb_exists} (not the {@code ?} operator) to avoid clashing with
     * JPA's positional-parameter placeholder. {@code metadata} is NOT NULL (V107 DEFAULT '{}'),
     * but {@code coalesce} keeps it null-safe.
     * <p>
     * {@code flushMode=COMMIT} is REQUIRED: a native query has unknown query-spaces, so Hibernate's
     * default auto-flush would flush the ENTIRE session before running it. {@code enrichWithRegistrationCounts}
     * is called inside {@code createEvent} right after the new Event is saved-but-not-yet-validated;
     * a full flush would prematurely run the Event's bean-validation and 400 a request that the
     * JPQL-based count siblings (registrations-only query-space → no Event flush) let through. COMMIT
     * defers the flush, matching those siblings. This count never needs to see un-flushed writes.
     */
    @QueryHints(@QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    @Query(value = "SELECT count(*) FROM registrations "
            + "WHERE event_id = :eventId "
            + "AND status IN (:statuses) "
            + "AND NOT jsonb_exists(coalesce(metadata, '{}'::jsonb), 'autoRegisteredFrom')",
            nativeQuery = true)
    long countRealAttendees(@Param("eventId") UUID eventId, @Param("statuses") List<String> statuses);

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
     * Bulk-cancel all waitlisted registrations for an event being archived.
     * Story 10.18: Event Archival Task &amp; Notification Cleanup (AC3).
     *
     * @param eventId       the event UUID
     * @param waitlistStatus the exact waitlist status string (e.g. "waitlist")
     * @return number of registrations updated
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Registration r SET r.status = 'cancelled' WHERE r.eventId = :eventId "
            + "AND r.status = :waitlistStatus")
    int cancelWaitlistRegistrationsForEvent(@Param("eventId") UUID eventId,
                                             @Param("waitlistStatus") String waitlistStatus);

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
