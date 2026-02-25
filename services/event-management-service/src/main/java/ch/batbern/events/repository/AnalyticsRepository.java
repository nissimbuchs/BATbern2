package ch.batbern.events.repository;

import ch.batbern.events.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository providing analytics queries for the Analytics Dashboard (Story 10.5).
 *
 * All queries aggregate data — no individual attendee data is exposed.
 * Uses the INTENTIONAL ARCHITECTURE BREAK pattern (joining user_profiles from
 * company-user-management-service) for sessions-per-company queries, following the
 * established pattern in SessionUserRepository#findUserPortraitsByUsernames.
 *
 * Note on fromDate: queries use `e.date >= :fromDate` (never nullable).
 * Callers pass Instant.EPOCH for "all-time" to avoid PostgreSQL NULL type inference errors.
 */
@Repository
public interface AnalyticsRepository extends JpaRepository<Event, UUID> {

    // ── Overview queries ─────────────────────────────────────────────────────

    /**
     * Count total events (all-time, no filter).
     * AC2: KPI card — Total Events.
     */
    @Query("SELECT COUNT(e) FROM Event e")
    long countTotalEvents();

    /**
     * Count total confirmed/attended registrations (all-time, no filter).
     * AC2: KPI card — Total Attendees.
     */
    @Query("SELECT COUNT(r) FROM Registration r WHERE r.status IN ('confirmed', 'attended')")
    long countTotalAttendees();

    /**
     * Count distinct companies from confirmed/attended registrations (all-time).
     * AC2: KPI card — Companies Represented.
     */
    @Query("SELECT COUNT(DISTINCT r.attendeeCompanyId) FROM Registration r "
            + "WHERE r.status IN ('confirmed', 'attended') AND r.attendeeCompanyId IS NOT NULL")
    long countDistinctCompanies();

    /**
     * Count total session_users rows (confirmed speaker contributions, all-time).
     * AC2: KPI card — Total Sessions (= total speaker appearances).
     */
    @Query("SELECT COUNT(su) FROM SessionUser su WHERE su.isConfirmed = true")
    long countTotalSessions();

    /**
     * All events with topic category for the cadence timeline (all-time, not filtered).
     * Returns: [eventId, eventCode, eventNumber, title, date, category]
     * Uses LEFT JOIN on Topic so events without a topic still appear (category = null).
     * AC2: Event cadence timeline chart.
     */
    @Query("""
        SELECT e.id, e.eventCode, e.eventNumber, e.title, e.date, t.category
        FROM Event e
        LEFT JOIN Topic t ON e.topicCode = t.topicCode
        ORDER BY e.date ASC
        """)
    List<Object[]> findAllEventsForTimeline();

    // ── Attendance queries ───────────────────────────────────────────────────

    /**
     * All confirmed/attended registrations with their event date, used for
     * the returning/new attendees in-memory algorithm.
     * Returns: [attendeeUsername, eventId, eventDate]
     * Results ordered by event date ASC so the algorithm processes first-event first.
     * AC3: Returning vs. New attendees.
     */
    @Query("""
        SELECT r.attendeeUsername, e.id, e.date
        FROM Registration r
        JOIN Event e ON r.eventId = e.id
        WHERE r.status IN ('confirmed', 'attended')
        ORDER BY e.date ASC
        """)
    List<Object[]> findAllAttendancesForReturningAlgorithm();

    /**
     * Per-event attendance totals with topic category, filtered by start date.
     * Pass Instant.EPOCH for all-time data.
     * Returns: [eventId, eventCode, eventNumber, title, date, category, totalAttendees]
     * AC3: Attendees per event chart data.
     */
    @Query("""
        SELECT e.id, e.eventCode, e.eventNumber, e.title, e.date, t.category,
               COUNT(r.id)
        FROM Event e
        LEFT JOIN Topic t ON e.topicCode = t.topicCode
        LEFT JOIN Registration r ON r.eventId = e.id AND r.status IN ('confirmed', 'attended')
        WHERE e.date >= :fromDate
        GROUP BY e.id, e.eventCode, e.eventNumber, e.title, e.date, t.category
        ORDER BY e.date ASC
        """)
    List<Object[]> findPerEventAttendanceTotals(@Param("fromDate") Instant fromDate);

    // ── Topics queries ───────────────────────────────────────────────────────

    /**
     * Events grouped by topic category, filtered by start date.
     * Pass Instant.EPOCH for all-time data.
     * Returns: [category, eventCount]
     * AC4: Events per category horizontal bar chart.
     */
    @Query("""
        SELECT t.category, COUNT(e.id)
        FROM Event e
        JOIN Topic t ON e.topicCode = t.topicCode
        WHERE e.date >= :fromDate
        GROUP BY t.category
        ORDER BY COUNT(e.id) DESC
        """)
    List<Object[]> findEventsPerCategory(@Param("fromDate") Instant fromDate);

    /**
     * Per-topic scatter data: event count and average attendees per topic.
     * Uses native SQL for subquery-in-FROM (not supported in JPQL).
     * Pass Instant.EPOCH for all-time data.
     * Returns: [topicCode, topicTitle, category, eventCount, avgAttendees]
     * AC4: Topic popularity scatter chart.
     */
    @Query(value = """
        SELECT t.topic_code AS topicCode,
               t.title AS topicTitle,
               t.category AS category,
               COUNT(DISTINCT e.id)::int AS eventCount,
               COALESCE(AVG(event_att.cnt), 0.0) AS avgAttendees
        FROM topics t
        JOIN events e ON e.topic_code = t.topic_code
        LEFT JOIN (
            SELECT r.event_id, COUNT(r.id) AS cnt
            FROM registrations r
            WHERE r.status IN ('confirmed', 'attended')
            GROUP BY r.event_id
        ) event_att ON event_att.event_id = e.id
        WHERE e.event_date >= :fromDate
        GROUP BY t.topic_code, t.title, t.category
        ORDER BY eventCount DESC
        """, nativeQuery = true)
    List<Object[]> findTopicScatterData(@Param("fromDate") Instant fromDate);

    // ── Companies queries ────────────────────────────────────────────────────

    /**
     * Attendee count per company per year (for stacked-bar chart).
     * Pass Instant.EPOCH for all-time data.
     * Returns: [year, companyName, attendeeCount]
     * AC5: Attendance by company over time.
     */
    @Query(value = """
        SELECT EXTRACT(YEAR FROM e.event_date)::int AS year,
               r.attendee_company_id AS companyName,
               COUNT(r.id) AS attendeeCount
        FROM registrations r
        JOIN events e ON r.event_id = e.id
        WHERE r.status IN ('confirmed', 'attended')
          AND r.attendee_company_id IS NOT NULL
          AND e.event_date >= :fromDate
        GROUP BY EXTRACT(YEAR FROM e.event_date), r.attendee_company_id
        ORDER BY year ASC, attendeeCount DESC
        """, nativeQuery = true)
    List<Object[]> findAttendanceByYearAndCompany(@Param("fromDate") Instant fromDate);

    /**
     * Sessions per company with unique speaker count.
     * INTENTIONAL ARCHITECTURE BREAK: joins user_profiles from company-user-management-service.
     * Both services share the same PostgreSQL database.
     * Pass Instant.EPOCH for all-time data.
     * Returns: [companyName, sessionCount, uniqueSpeakers]
     * AC5: Sessions per company chart.
     */
    @Query(value = """
        SELECT up.company_id AS companyName,
               COUNT(su.id) AS sessionCount,
               COUNT(DISTINCT su.username) AS uniqueSpeakers
        FROM session_users su
        JOIN sessions s ON su.session_id = s.id
        JOIN user_profiles up ON su.username = up.username
        JOIN events e ON s.event_id = e.id
        WHERE e.event_date >= :fromDate
        GROUP BY up.company_id
        ORDER BY sessionCount DESC
        """, nativeQuery = true)
    List<Object[]> findSessionsPerCompany(@Param("fromDate") Instant fromDate);

    /**
     * All-time attendee distribution per company (for pie chart default view).
     * Pass Instant.EPOCH for all-time data.
     * Returns: [companyName, attendeeCount]
     * AC5: Attendee distribution pie chart (no event filter).
     */
    @Query("""
        SELECT r.attendeeCompanyId, COUNT(r.id)
        FROM Registration r
        JOIN Event e ON r.eventId = e.id
        WHERE r.status IN ('confirmed', 'attended')
          AND r.attendeeCompanyId IS NOT NULL
          AND e.date >= :fromDate
        GROUP BY r.attendeeCompanyId
        ORDER BY COUNT(r.id) DESC
        """)
    List<Object[]> findCompanyDistribution(@Param("fromDate") Instant fromDate);

    /**
     * Attendee distribution for a single event (for pie chart per-event filter).
     * Returns: [companyName, attendeeCount]
     * AC5: Per-event distribution via GET /analytics/companies/distribution?eventCode=...
     */
    @Query("""
        SELECT r.attendeeCompanyId, COUNT(r.id)
        FROM Registration r
        JOIN Event e ON r.eventId = e.id
        WHERE r.status IN ('confirmed', 'attended')
          AND r.attendeeCompanyId IS NOT NULL
          AND e.eventCode = :eventCode
        GROUP BY r.attendeeCompanyId
        ORDER BY COUNT(r.id) DESC
        """)
    List<Object[]> findCompanyDistributionByEvent(@Param("eventCode") String eventCode);
}
