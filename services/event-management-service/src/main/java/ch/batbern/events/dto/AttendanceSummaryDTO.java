package ch.batbern.events.dto;

import java.time.Instant;

/**
 * DTO for attendance summary per event.
 * Story 8.1: Partner Attendance Dashboard - AC1, AC2, AC5
 *
 * Returns per-event attendance breakdown for a given company.
 * Used by partner-coordination-service via server-to-server HTTP call.
 */
public record AttendanceSummaryDTO(
        String eventCode,
        Instant eventDate,
        long totalAttendees,
        long companyAttendees
) {}
