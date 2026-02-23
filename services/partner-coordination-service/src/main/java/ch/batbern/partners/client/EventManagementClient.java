package ch.batbern.partners.client;

import ch.batbern.partners.client.dto.AttendanceSummaryDTO;
import ch.batbern.partners.client.dto.EventSummaryDTO;

import java.util.List;

/**
 * Client interface for communicating with the Event Management Service API.
 * Story 8.1: Partner Attendance Dashboard
 * Story 8.3: Partner Meeting Coordination — event summary for ICS generation
 *
 * Results are Caffeine-cached (AC5 / AC8 performance).
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

    /**
     * Get a summary of a single BATbern event for ICS generation.
     *
     * Story 8.3: Used to build the second VEVENT in the partner meeting calendar invite.
     * Cached for 1 hour — event details rarely change.
     *
     * @param eventCode ADR-003 event code (e.g. "BATbern57")
     * @return event summary with title, date, start/end times, venue
     */
    EventSummaryDTO getEventSummary(String eventCode);
}
