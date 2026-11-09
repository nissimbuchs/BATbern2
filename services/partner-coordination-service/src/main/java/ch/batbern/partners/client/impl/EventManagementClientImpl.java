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

import java.net.URI;
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
 * Communicates with event-management-service to retrieve attendance data (public API, with the
 * user's JWT) and event schedules (internal API, no JWT needed).
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

        // Strictly encoded: a '+' or '&' in a company name stays part of the value.
        URI url = UriComponentsBuilder
                .fromUriString(eventManagementBaseUrl + "/api/v1/events/attendance-summary")
                .queryParam("companyName", "{companyName}")
                .queryParam("fromYear", "{fromYear}")
                .encode()
                .buildAndExpand(companyName, fromYear)
                .toUri();

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
     * Get event summary from event-management-service for ICS generation, from the internal
     * {@code GET /internal/events/{eventCode}/schedule} (ems-internal spec). It needs no JWT, and its
     * start and end are the times EMS gives the event everywhere else ({@code EventTimeResolver}), so
     * a partner caller or a scheduler gets the same times as an organizer, and the invite matches the
     * registration mails (17.0 deferred work). Cached 1 hour (Story 8.3, AC8 performance).
     */
    @Override
    @Cacheable(value = "eventSummaryCache", key = "#eventCode")
    public EventSummaryDTO getEventSummary(String eventCode) {
        log.debug("Fetching event schedule from event-management-service: eventCode={}", eventCode);

        URI url = UriComponentsBuilder
                .fromUriString(eventManagementBaseUrl + "/internal/events/{eventCode}/schedule")
                .encode()
                .buildAndExpand(eventCode)
                .toUri();

        try {
            // The user's JWT is forwarded when there is one (attribution); the operation needs none.
            HttpEntity<Void> request = new HttpEntity<>(createHeadersWithJwtToken());
            EventSchedule body = restTemplate.exchange(url, HttpMethod.GET, request, EventSchedule.class).getBody();
            if (body == null) {
                throw new RuntimeException("Empty response from event-management-service for eventCode: " + eventCode);
            }

            // Derive LocalDate from the event's date Instant in Europe/Zurich zone
            ZonedDateTime zurichDt = body.date() != null
                    ? body.date().atZone(ZURICH)
                    : ZonedDateTime.now(ZURICH);
            LocalDate eventDate = zurichDt.toLocalDate();

            // EMS resolves the times as it does for registration mails and event pages; the ICS wants
            // Zurich local times (IcsGeneratorService.toUtc()).
            LocalTime startTime = body.startTime().atZone(ZURICH).toLocalTime();
            LocalTime endTime = body.endTime().atZone(ZURICH).toLocalTime();
            String venue = buildVenueString(body.venueName(), body.venueAddress());

            log.debug("Event summary fetched: eventCode={}, date={}, venue={}", eventCode, eventDate, venue);
            return new EventSummaryDTO(eventCode, body.title(), eventDate, startTime, endTime, venue);

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
                log.debug("No JWT token in SecurityContext for event-management-service call");
            }
        } catch (Exception e) {
            log.warn("Failed to extract JWT token for event-management-service: {}", e.getMessage());
        }
        return headers;
    }

    /** Schema {@code EventSchedule} of the ems-internal spec; unknown properties are ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventSchedule(
            String eventCode,
            String title,
            Instant date,
            String venueName,
            String venueAddress,
            String eventType,
            Instant startTime,
            Instant endTime) {
    }
}
