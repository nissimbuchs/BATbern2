package ch.batbern.partners.client.impl;

import ch.batbern.partners.client.EventManagementClient;
import ch.batbern.partners.client.dto.AttendanceSummaryDTO;
import ch.batbern.partners.client.dto.EventSummaryDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Implementation of EventManagementClient using Spring RestTemplate.
 * Story 8.1: Partner Attendance Dashboard
 * Story 8.3: Partner Meeting Coordination — getEventSummary for ICS generation
 *
 * Communicates with event-management-service to retrieve attendance data and event summaries.
 * Results are Caffeine-cached (15 min attendance, 1 hour event summary).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventManagementClientImpl implements EventManagementClient {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    private final RestTemplate restTemplate;

    @Value("${event-management-service.base-url}")
    private String eventManagementBaseUrl;

    /**
     * Get attendance summary for a company from event-management-service.
     * Cached 15 minutes per (companyName, fromYear) pair (AC5).
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

    /**
     * Get event summary from event-management-service for ICS generation.
     * Cached 1 hour — event details rarely change (Story 8.3, AC8 performance).
     */
    @Override
    @Cacheable(value = "eventSummaryCache", key = "#eventCode")
    public EventSummaryDTO getEventSummary(String eventCode) {
        log.debug("Fetching event summary from event-management-service: eventCode={}", eventCode);

        String url = eventManagementBaseUrl + "/api/v1/events/" + eventCode;

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<EventResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    EventResponse.class
            );

            EventResponse body = response.getBody();
            if (body == null) {
                throw new RuntimeException("Empty response from event-management-service for eventCode: " + eventCode);
            }

            // Derive LocalDate + LocalTime from the event's Instant date in Europe/Zurich zone
            ZonedDateTime zurichDt = body.getDate() != null
                    ? body.getDate().atZone(ZURICH)
                    : ZonedDateTime.now(ZURICH);

            LocalDate eventDate = zurichDt.toLocalDate();
            LocalTime startTime = zurichDt.toLocalTime();
            // EMS doesn't have an explicit endTime; default conference duration is ~4 hours
            LocalTime endTime = startTime.plusHours(4);

            // Combine venue name + address for ICS location
            String venue = buildVenueString(body.getVenueName(), body.getVenueAddress());

            log.debug("Event summary fetched: eventCode={}, date={}, venue={}", eventCode, eventDate, venue);

            return new EventSummaryDTO(eventCode, body.getTitle(), eventDate, startTime, endTime, venue);

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Event not found in event-management-service: eventCode={}", eventCode);
            throw new IllegalArgumentException("Event not found: " + eventCode);
        } catch (Exception e) {
            log.error("Failed to fetch event summary for eventCode={}: {}", eventCode, e.getMessage(), e);
            throw new RuntimeException(
                    "Failed to fetch event summary from event-management-service for: " + eventCode, e);
        }
    }

    private String buildVenueString(String venueName, String venueAddress) {
        if (venueName == null && venueAddress == null) {
            return null;
        }
        if (venueName == null) {
            return venueAddress;
        }
        if (venueAddress == null) {
            return venueName;
        }
        return venueName + ", " + venueAddress;
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

    /**
     * Minimal response DTO for parsing GET /api/v1/events/{eventCode} response.
     * Only the fields needed for ICS generation are mapped.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EventResponse {
        private String title;
        private String eventCode;
        private Instant date;
        private String venueName;
        private String venueAddress;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getEventCode() {
            return eventCode;
        }

        public void setEventCode(String eventCode) {
            this.eventCode = eventCode;
        }

        public Instant getDate() {
            return date;
        }

        public void setDate(Instant date) {
            this.date = date;
        }

        public String getVenueName() {
            return venueName;
        }

        public void setVenueName(String venueName) {
            this.venueName = venueName;
        }

        public String getVenueAddress() {
            return venueAddress;
        }

        public void setVenueAddress(String venueAddress) {
            this.venueAddress = venueAddress;
        }
    }
}
