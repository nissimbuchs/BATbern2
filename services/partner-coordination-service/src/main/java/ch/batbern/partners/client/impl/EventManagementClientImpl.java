package ch.batbern.partners.client.impl;

import ch.batbern.partners.client.EventManagementClient;
import ch.batbern.partners.client.dto.AttendanceSummaryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Implementation of EventManagementClient using Spring RestTemplate.
 * Story 8.1: Partner Attendance Dashboard
 *
 * Communicates with event-management-service to retrieve attendance data.
 * Results are Caffeine-cached for 15 minutes to satisfy AC5 (data freshness / no real-time requirement).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventManagementClientImpl implements EventManagementClient {

    private final RestTemplate restTemplate;

    @Value("${event-management-service.base-url}")
    private String eventManagementBaseUrl;

    /**
     * Get attendance summary for a company from event-management-service.
     * Cached 15 minutes per (companyName, fromYear) pair (AC5).
     *
     * @param companyName ADR-003 meaningful company identifier
     * @param fromYear    earliest year to include
     * @return list of per-event attendance summaries
     */
    @Override
    @Cacheable(value = "partnerAttendanceCache", key = "#companyName + '-' + #fromYear")
    public List<AttendanceSummaryDTO> getAttendanceSummary(String companyName, int fromYear) {
        log.debug("Fetching attendance summary from event-management-service: company={}, fromYear={}",
                companyName, fromYear);

        String url = UriComponentsBuilder
                .fromHttpUrl(eventManagementBaseUrl + "/api/v1/events/attendance-summary")
                .queryParam("companyName", companyName)
                .queryParam("fromYear", fromYear)
                .toUriString();

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<List<AttendanceSummaryDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<List<AttendanceSummaryDTO>>() {}
            );

            List<AttendanceSummaryDTO> result = response.getBody();

            log.debug("Retrieved {} attendance summary entries for company={}",
                    result != null ? result.size() : 0, companyName);
            return result != null ? result : List.of();

        } catch (Exception e) {
            log.error("Failed to fetch attendance summary for company={}, fromYear={}: {}",
                    companyName, fromYear, e.getMessage(), e);
            throw new RuntimeException(
                    "Failed to fetch attendance data from event-management-service for company: "
                            + companyName, e);
        }
    }

    private HttpHeaders createHeadersWithJwtToken() {
        HttpHeaders headers = new HttpHeaders();
        try {
            Object principal = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getPrincipal();

            if (principal instanceof Jwt jwt) {
                headers.set("Authorization", "Bearer " + jwt.getTokenValue());
                log.trace("JWT token propagated to event-management-service");
            } else {
                log.warn("No JWT token in SecurityContext for event-management-service call");
            }
        } catch (Exception e) {
            log.warn("Failed to extract JWT token for event-management-service: {}", e.getMessage());
        }
        return headers;
    }
}
