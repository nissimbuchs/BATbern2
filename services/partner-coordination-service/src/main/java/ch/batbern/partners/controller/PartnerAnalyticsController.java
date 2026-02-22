package ch.batbern.partners.controller;

import ch.batbern.partners.dto.PartnerDashboardDTO;
import ch.batbern.partners.service.PartnerAnalyticsService;
import ch.batbern.partners.service.PartnerAttendanceExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST controller for partner attendance analytics.
 * Story 8.1: Partner Attendance Dashboard — AC1–7.
 *
 * Role-based access control (AC6):
 * - ORGANIZER: can access any company's analytics
 * - PARTNER: can only access their own company's analytics
 *   (enforced via @partnerSecurityService.isCurrentUserCompany)
 */
@RestController
@RequestMapping("/api/v1/partners/{companyName}/analytics")
@RequiredArgsConstructor
@Slf4j
public class PartnerAnalyticsController {

    private final PartnerAnalyticsService analyticsService;
    private final PartnerAttendanceExportService exportService;

    /**
     * GET /api/v1/partners/{companyName}/analytics/dashboard
     *
     * Returns per-event attendance summary + cost-per-attendee KPI.
     *
     * @param companyName ADR-003 company identifier
     * @param fromYear    earliest year to include (default: current year − 5)
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ORGANIZER') or @partnerSecurityService.isCurrentUserCompany(#companyName)")
    public ResponseEntity<PartnerDashboardDTO> getDashboard(
            @PathVariable String companyName,
            @RequestParam(required = false) Integer fromYear) {

        int resolvedYear = (fromYear != null && fromYear > 0) ? fromYear : (LocalDate.now().getYear() - 5);

        log.debug("GET /partners/{}/analytics/dashboard?fromYear={}", companyName, resolvedYear);

        PartnerDashboardDTO dashboard = analyticsService.getAttendanceDashboard(companyName, resolvedYear);

        return ResponseEntity.ok(dashboard);
    }

    /**
     * GET /api/v1/partners/{companyName}/analytics/export
     *
     * Returns attendance table as XLSX download.
     *
     * @param companyName ADR-003 company identifier
     * @param fromYear    earliest year to include (default: current year − 5)
     */
    @GetMapping("/export")
    @PreAuthorize("hasRole('ORGANIZER') or @partnerSecurityService.isCurrentUserCompany(#companyName)")
    public ResponseEntity<byte[]> exportAttendance(
            @PathVariable String companyName,
            @RequestParam(required = false) Integer fromYear) {

        int resolvedYear = (fromYear != null && fromYear > 0) ? fromYear : (LocalDate.now().getYear() - 5);

        log.debug("GET /partners/{}/analytics/export?fromYear={}", companyName, resolvedYear);

        PartnerDashboardDTO dashboard = analyticsService.getAttendanceDashboard(companyName, resolvedYear);
        byte[] xlsx = exportService.generateXlsx(companyName, dashboard);

        String filename = "attendance-" + companyName + ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(
            ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(xlsx.length);

        return ResponseEntity.ok().headers(headers).body(xlsx);
    }
}
