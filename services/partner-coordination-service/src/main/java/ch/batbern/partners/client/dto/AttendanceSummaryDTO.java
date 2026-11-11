package ch.batbern.partners.client.dto;

import java.time.Instant;

/**
 * DTO for per-event attendance summary received from event-management-service.
 * Story 8.1: Partner Attendance Dashboard - AC1, AC2, AC5
 *
 * Schema {@code AttendanceSummary} of the ems-internal spec
 * ({@code GET /internal/events/attendance-summary}, EMS {@code InternalAttendanceSummaryController}).
 */
public record AttendanceSummaryDTO(
        String eventCode,
        String eventTitle,
        Instant eventDate,
        long totalAttendees,
        long companyAttendees
) {}
