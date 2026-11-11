package ch.batbern.events.dto;

import java.time.Instant;

/**
 * Repository projection for the per-event attendance summary (Story 8.1).
 *
 * <p>Internal to EMS — constructed by a JPQL {@code new} expression in
 * {@code RegistrationRepository#findAttendanceSummary}. {@code InternalAttendanceSummaryController}
 * maps it to its wire record {@code AttendanceSummary} (ems-internal schema of the same name).
 */
public record AttendanceSummaryProjection(
        String eventCode,
        String eventTitle,
        Instant eventDate,
        long totalAttendees,
        long companyAttendees
) {}
