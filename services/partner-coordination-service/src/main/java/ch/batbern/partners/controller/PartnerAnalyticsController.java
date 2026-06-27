package ch.batbern.partners.controller;

import ch.batbern.partners.api.generated.PartnerAnalyticsApi;
import ch.batbern.partners.dto.generated.PartnerDashboardResponse;
import ch.batbern.partners.service.PartnerAnalyticsService;
import ch.batbern.partners.service.PartnerAttendanceExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for partner attendance analytics.
 * Story 8.1: Partner Attendance Dashboard — AC1–7.
 *
 * Implements the generated {@link PartnerAnalyticsApi} interface from the consolidated
 * partners-api OpenAPI spec (Phase 5).
 *
 * Role-based access control (AC6):
 * - ORGANIZER: can access any company's analytics
 * - PARTNER: can only access their own company's analytics
 *   (enforced via @partnerSecurityService.isCurrentUserCompany)
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PartnerAnalyticsController implements PartnerAnalyticsApi {

    private final PartnerAnalyticsService analyticsService;
    private final PartnerAttendanceExportService exportService;

    /**
     * Returns per-event attendance summary + cost-per-attendee KPI.
     * fromYear of null/≤0 defaults to current year − 5 (resolved in the service).
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER') or @partnerSecurityService.isCurrentUserCompany(#companyName)")
    public ResponseEntity<PartnerDashboardResponse> getAttendanceDashboard(String companyName, Integer fromYear) {
        log.debug("GET /partners/{}/analytics/dashboard?fromYear={}", companyName, fromYear);
        PartnerDashboardResponse dashboard = analyticsService.getAttendanceDashboard(companyName, orZero(fromYear));
        return ResponseEntity.ok(dashboard);
    }

    /** Returns the attendance table as an XLSX download. */
    @Override
    @PreAuthorize("hasRole('ORGANIZER') or @partnerSecurityService.isCurrentUserCompany(#companyName)")
    public ResponseEntity<Resource> exportAttendance(String companyName, Integer fromYear) {
        log.debug("GET /partners/{}/analytics/export?fromYear={}", companyName, fromYear);

        PartnerDashboardResponse dashboard = analyticsService.getAttendanceDashboard(companyName, orZero(fromYear));
        byte[] xlsx = exportService.generateXlsx(companyName, dashboard);

        String filename = "attendance-" + companyName + ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(
            ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(xlsx.length);

        return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(xlsx));
    }

    private static int orZero(Integer fromYear) {
        return fromYear != null ? fromYear : 0;
    }
}
