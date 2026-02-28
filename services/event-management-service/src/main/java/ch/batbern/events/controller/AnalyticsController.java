package ch.batbern.events.controller;

import ch.batbern.events.dto.generated.AnalyticsAttendanceResponse;
import ch.batbern.events.dto.generated.AnalyticsCompaniesResponse;
import ch.batbern.events.dto.generated.AnalyticsOverviewResponse;
import ch.batbern.events.dto.generated.AnalyticsTopicsResponse;
import ch.batbern.events.dto.generated.CompanyDistributionResponse;
import ch.batbern.events.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analytics Dashboard API (Story 10.5).
 *
 * Provides aggregate analytics data for the 4-tab Analytics page:
 * Overview, Attendance, Topics, Companies.
 *
 * All endpoints require ORGANIZER or PARTNER role.
 * No individual attendee data is exposed — aggregates only.
 *
 * Uses generated DTOs from events-api.openapi.yml (ADR-006 contract-first).
 */
@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAnyRole('ORGANIZER', 'PARTNER')")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * GET /api/v1/analytics/overview
     * Returns all-time KPI totals and the event cadence timeline.
     * AC2, AC6.
     */
    @GetMapping("/overview")
    public ResponseEntity<AnalyticsOverviewResponse> getOverview() {
        log.debug("GET /api/v1/analytics/overview");
        return ResponseEntity.ok(analyticsService.getOverview());
    }

    /**
     * GET /api/v1/analytics/attendance?fromYear={year}
     * Returns per-event attendance with returning/new breakdown.
     * AC3, AC6.
     */
    @GetMapping("/attendance")
    public ResponseEntity<AnalyticsAttendanceResponse> getAttendance(
            @RequestParam(required = false) Integer fromYear) {
        log.debug("GET /api/v1/analytics/attendance fromYear={}", fromYear);
        return ResponseEntity.ok(analyticsService.getAttendance(fromYear));
    }

    /**
     * GET /api/v1/analytics/topics?fromYear={year}
     * Returns events per category and topic scatter data.
     * AC4, AC6.
     */
    @GetMapping("/topics")
    public ResponseEntity<AnalyticsTopicsResponse> getTopics(
            @RequestParam(required = false) Integer fromYear) {
        log.debug("GET /api/v1/analytics/topics fromYear={}", fromYear);
        return ResponseEntity.ok(analyticsService.getTopics(fromYear));
    }

    /**
     * GET /api/v1/analytics/companies?fromYear={year}
     * Returns attendance over time, sessions per company, and distribution.
     * AC5, AC6.
     */
    @GetMapping("/companies")
    public ResponseEntity<AnalyticsCompaniesResponse> getCompanies(
            @RequestParam(required = false) Integer fromYear) {
        log.debug("GET /api/v1/analytics/companies fromYear={}", fromYear);
        return ResponseEntity.ok(analyticsService.getCompanies(fromYear));
    }

    /**
     * GET /api/v1/analytics/companies/distribution?eventCode={code}
     * Returns per-event company attendee distribution for pie chart event filter.
     * AC5, AC6.
     */
    @GetMapping("/companies/distribution")
    public ResponseEntity<CompanyDistributionResponse> getCompanyDistribution(
            @RequestParam @NotBlank String eventCode) {
        log.debug("GET /api/v1/analytics/companies/distribution eventCode={}", eventCode);
        return ResponseEntity.ok(analyticsService.getCompanyDistribution(eventCode));
    }
}
