package ch.batbern.events.controller;

import ch.batbern.events.dto.AttendanceSummaryProjection;
import ch.batbern.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * {@code GET /internal/events/attendance-summary} ({@code docs/api/internal/ems-internal.openapi.yml},
 * operation {@code getAttendanceSummary}): confirmed attendees per event, in total and of one
 * company, for partner-coordination's partner attendance dashboard and its XLSX export (Story 8.1).
 *
 * <p>Authorized by operation, not by the caller (ADR-016 decision 3): it is reached inside the VPC
 * only (the API gateway denies {@code /internal/**}), a forwarded JWT is optional and does not change
 * the answer. The role and ownership check (PARTNER of that company, or ORGANIZER) stays on
 * partner-coordination's {@code PartnerAnalyticsController}, where the user entered. Until Epic 17 6b
 * this was the public {@code GET /api/v1/events/attendance-summary} in {@code EventController}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class InternalAttendanceSummaryController {

    /** Without {@code fromYear}: the current year minus this many years. */
    static final int DEFAULT_YEARS_BACK = 5;

    private final RegistrationRepository registrationRepository;

    /** One row per event since 1 January of {@code fromYear} (UTC), newest first. */
    @GetMapping("/internal/events/attendance-summary")
    @Transactional(readOnly = true)
    public List<AttendanceSummary> attendanceSummary(@RequestParam String companyName,
            @RequestParam(required = false) Integer fromYear) {
        int resolvedFromYear = fromYear != null
                ? fromYear
                : LocalDate.now(ZoneOffset.UTC).getYear() - DEFAULT_YEARS_BACK;
        log.debug("Internal attendance summary: companyName={}, fromYear={}", companyName, resolvedFromYear);
        Instant fromDate = LocalDate.of(resolvedFromYear, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return registrationRepository.findAttendanceSummary(companyName, fromDate).stream()
                .map(InternalAttendanceSummaryController::toAttendanceSummary)
                .toList();
    }

    private static AttendanceSummary toAttendanceSummary(AttendanceSummaryProjection p) {
        return new AttendanceSummary(p.eventCode(), p.eventTitle(), p.eventDate(), p.totalAttendees(),
                p.companyAttendees());
    }

    /** Schema {@code AttendanceSummary}. */
    public record AttendanceSummary(
            String eventCode,
            String eventTitle,
            Instant eventDate,
            long totalAttendees,
            long companyAttendees) {
    }
}
