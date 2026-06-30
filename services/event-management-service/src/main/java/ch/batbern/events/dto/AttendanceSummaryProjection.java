package ch.batbern.events.dto;

import java.time.Instant;

/**
 * Repository projection for the per-event attendance summary (Story 8.1).
 *
 * <p>Internal to EMS — constructed by a JPQL {@code new} expression in
 * {@code RegistrationRepository#findAttendanceSummary}. The controller maps this to the
 * generated {@code ch.batbern.events.analytics.dto.generated.AttendanceSummaryDTO} wire contract
 * ({@code eventDate} Instant → OffsetDateTime). Kept separate from the generated DTO because the
 * JPQL constructor projects an {@code Instant} column and primitive counts, which the typed wire
 * DTO (OffsetDateTime / Long) cannot bind directly.
 */
public record AttendanceSummaryProjection(
        String eventCode,
        String eventTitle,
        Instant eventDate,
        long totalAttendees,
        long companyAttendees
) {}
