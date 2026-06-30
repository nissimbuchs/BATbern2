package ch.batbern.events.controller;

import ch.batbern.events.analytics.api.generated.AnalyticsApi;
import ch.batbern.events.analytics.dto.generated.AnalyticsAttendanceResponse;
import ch.batbern.events.analytics.dto.generated.AnalyticsCompaniesResponse;
import ch.batbern.events.analytics.dto.generated.AnalyticsOverviewResponse;
import ch.batbern.events.analytics.dto.generated.AnalyticsTopicsResponse;
import ch.batbern.events.analytics.dto.generated.CompanyDistributionResponse;
import ch.batbern.events.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
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
 * {@code implements} the generated {@link AnalyticsApi} interface (event-analytics spec,
 * ADR-006 contract-first). The interface supplies the verb/path/param mapping annotations and
 * the generated DTO types; the class-level {@code @RequestMapping("/api/v1")} supplies the
 * version prefix (interface paths are {@code /analytics/...}). Override params are bare —
 * the {@code @RequestParam}/{@code @Valid} annotations are inherited from the interface
 * (declaring them here would stop Spring inheriting the interface's binding annotations).
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ORGANIZER', 'PARTNER')")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AnalyticsController implements AnalyticsApi {

    private final AnalyticsService analyticsService;

    /**
     * GET /api/v1/analytics/overview
     * Returns all-time KPI totals and the event cadence timeline.
     * AC2, AC6.
     */
    @Override
    public ResponseEntity<AnalyticsOverviewResponse> getAnalyticsOverview() {
        log.debug("GET /api/v1/analytics/overview");
        return ResponseEntity.ok(analyticsService.getOverview());
    }

    /**
     * GET /api/v1/analytics/attendance?fromYear={year}
     * Returns per-event attendance with returning/new breakdown.
     * AC3, AC6.
     */
    @Override
    public ResponseEntity<AnalyticsAttendanceResponse> getAnalyticsAttendance(Integer fromYear) {
        log.debug("GET /api/v1/analytics/attendance fromYear={}", fromYear);
        return ResponseEntity.ok(analyticsService.getAttendance(fromYear));
    }

    /**
     * GET /api/v1/analytics/topics?fromYear={year}
     * Returns events per category and topic scatter data.
     * AC4, AC6.
     */
    @Override
    public ResponseEntity<AnalyticsTopicsResponse> getAnalyticsTopics(Integer fromYear) {
        log.debug("GET /api/v1/analytics/topics fromYear={}", fromYear);
        return ResponseEntity.ok(analyticsService.getTopics(fromYear));
    }

    /**
     * GET /api/v1/analytics/companies?fromYear={year}
     * Returns attendance over time, sessions per company, and distribution.
     * AC5, AC6.
     */
    @Override
    public ResponseEntity<AnalyticsCompaniesResponse> getAnalyticsCompanies(Integer fromYear) {
        log.debug("GET /api/v1/analytics/companies fromYear={}", fromYear);
        return ResponseEntity.ok(analyticsService.getCompanies(fromYear));
    }

    /**
     * GET /api/v1/analytics/companies/distribution?eventCode={code}
     * Returns per-event company attendee distribution for pie chart event filter.
     * AC5, AC6.
     */
    @Override
    public ResponseEntity<CompanyDistributionResponse> getCompanyDistribution(String eventCode) {
        log.debug("GET /api/v1/analytics/companies/distribution eventCode={}", eventCode);
        return ResponseEntity.ok(analyticsService.getCompanyDistribution(eventCode));
    }
}
