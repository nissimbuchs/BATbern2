package ch.batbern.partners.service;

import ch.batbern.partners.client.EventManagementClient;
import ch.batbern.partners.client.dto.AttendanceSummaryDTO;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.dto.PartnerDashboardDTO;
import ch.batbern.partners.exception.PartnerNotFoundException;
import ch.batbern.partners.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Service for partner attendance analytics.
 * Story 8.1: Partner Attendance Dashboard - AC1, AC2, AC3
 *
 * Aggregates attendance data from event-management-service and computes
 * the cost-per-attendee KPI from local partner data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerAnalyticsService {

    private final EventManagementClient eventManagementClient;
    private final PartnerRepository partnerRepository;

    /**
     * Build the attendance dashboard for a partner company.
     *
     * @param companyName ADR-003 meaningful company identifier
     * @param fromYear    earliest year to include (0 or negative → current year minus 5)
     * @return dashboard DTO containing per-event summary + cost-per-attendee
     * @throws PartnerNotFoundException if no partner record exists for companyName
     */
    public PartnerDashboardDTO getAttendanceDashboard(String companyName, int fromYear) {
        log.debug("Building attendance dashboard for company={}, fromYear={}", companyName, fromYear);

        int resolvedFromYear = (fromYear > 0) ? fromYear : (LocalDate.now().getYear() - 5);

        // 1. Fetch per-event attendance data from event-management-service (cached 15 min)
        List<AttendanceSummaryDTO> summaries =
                eventManagementClient.getAttendanceSummary(companyName, resolvedFromYear);

        // 2. Fetch partnership_cost from local partners table (AC3)
        Partner partner = partnerRepository.findByCompanyName(companyName)
                .orElseThrow(() -> new PartnerNotFoundException("Partner not found: " + companyName));

        BigDecimal costPerAttendee = computeCostPerAttendee(partner.getPartnershipCost(), summaries);

        return new PartnerDashboardDTO(summaries, costPerAttendee);
    }

    /**
     * Compute cost-per-attendee KPI.
     * Returns null when there are no attendees or no partnership_cost configured (AC3).
     */
    private BigDecimal computeCostPerAttendee(
            BigDecimal partnershipCost,
            List<AttendanceSummaryDTO> summaries) {

        if (partnershipCost == null) {
            return null; // partnership cost not configured → display as N/A
        }

        long totalCompanyAttendees = summaries.stream()
                .mapToLong(AttendanceSummaryDTO::companyAttendees)
                .sum();

        if (totalCompanyAttendees == 0) {
            return null; // avoid division by zero → display as N/A
        }

        return partnershipCost
                .divide(BigDecimal.valueOf(totalCompanyAttendees), 2, RoundingMode.HALF_UP);
    }
}
