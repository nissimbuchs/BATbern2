package ch.batbern.partners.client;

import ch.batbern.partners.client.dto.AttendanceSummaryDTO;

import java.util.List;

/**
 * Client interface for communicating with the Event Management Service API.
 * Story 8.1: Partner Attendance Dashboard
 *
 * Provides attendance data for partner analytics.
 * Results are Caffeine-cached for 15 minutes (AC5).
 */
public interface EventManagementClient {

    /**
     * Get per-event attendance summary for a given company.
     *
     * @param companyName ADR-003 meaningful company identifier (e.g. "GoogleZH")
     * @param fromYear    earliest year to include in results
     * @return list of per-event attendance summaries ordered by date descending
     */
    List<AttendanceSummaryDTO> getAttendanceSummary(String companyName, int fromYear);
}
