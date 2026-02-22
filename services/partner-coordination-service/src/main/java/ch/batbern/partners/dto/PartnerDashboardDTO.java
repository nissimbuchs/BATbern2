package ch.batbern.partners.dto;

import ch.batbern.partners.client.dto.AttendanceSummaryDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dashboard DTO for partner attendance analytics.
 * Story 8.1: Partner Attendance Dashboard - AC1, AC2, AC3
 *
 * Returned by PartnerAnalyticsController to the frontend.
 */
public record PartnerDashboardDTO(
        List<AttendanceSummaryDTO> attendanceSummary,
        BigDecimal costPerAttendee  // null when no attendees or no partnership_cost configured (AC3)
) {}
