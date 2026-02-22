package ch.batbern.partners.client.dto;

import java.time.Instant;

/**
 * DTO for per-event attendance summary received from event-management-service.
 * Story 8.1: Partner Attendance Dashboard - AC1, AC2, AC5
 *
 * Mirrors ch.batbern.events.dto.AttendanceSummaryDTO in event-management-service.
 */
public record AttendanceSummaryDTO(
        String eventCode,
        Instant eventDate,
        long totalAttendees,
        long companyAttendees
) {}
