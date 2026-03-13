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
import java.util.Map;

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

        String url = eventManagementBaseUrl + "/api/v1/events/" + eventCode + "?include=sessions";

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

            // Derive LocalDate from the event's date Instant in Europe/Zurich zone
            ZonedDateTime zurichDt = body.getDate() != null
                    ? body.getDate().atZone(ZURICH)
                    : ZonedDateTime.now(ZURICH);

            LocalDate eventDate = zurichDt.toLocalDate();

            // Derive start/end: earliest/latest session times, falling back to event-type defaults
            LocalTime[] times = deriveEventTimes(body);
            LocalTime startTime = times[0];
            LocalTime endTime = times[1];

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

    /**
     * Derive start/end times for the BATbern event VEVENT:
     * 1. Use min(session.startTime) / max(session.endTime) if sessions have times.
     * 2. Fall back to the event type's typical start/end times.
     *
     * Times are returned as Zurich local time for use by IcsGeneratorService.toUtc().
     */
    private LocalTime[] deriveEventTimes(EventResponse body) {
        List<Map<String, Object>> sessions = body.getSessions();
        if (sessions != null && !sessions.isEmpty()) {
            LocalTime minStart = null;
            LocalTime maxEnd = null;
            for (Map<String, Object> session : sessions) {
                Object startObj = session.get("startTime");
                Object endObj = session.get("endTime");
                if (startObj != null) {
                    try {
                        LocalTime st = Instant.parse(startObj.toString()).atZone(ZURICH).toLocalTime();
                        if (minStart == null || st.isBefore(minStart)) minStart = st;
                    } catch (Exception e) {
                        log.debug("Could not parse session startTime: {}", startObj);
                    }
                }
                if (endObj != null) {
                    try {
                        LocalTime et = Instant.parse(endObj.toString()).atZone(ZURICH).toLocalTime();
                        if (maxEnd == null || et.isAfter(maxEnd)) maxEnd = et;
                    } catch (Exception e) {
                        log.debug("Could not parse session endTime: {}", endObj);
                    }
                }
            }
            if (minStart != null && maxEnd != null) {
                log.debug("Derived event times from {} sessions: {}–{}", sessions.size(), minStart, maxEnd);
                return new LocalTime[]{minStart, maxEnd};
            }
        }
        return typicalTimesForType(body.getEventType());
    }

    /**
     * Typical start/end times by event type — mirrors the values in V10__Create_event_types_table.sql.
     * Used when no sessions with times are available yet.
     */
    private LocalTime[] typicalTimesForType(String eventType) {
        if (eventType == null) {
            return new LocalTime[]{LocalTime.of(18, 0), LocalTime.of(22, 0)};
        }
        return switch (eventType.toLowerCase()) {
            case "full_day"  -> new LocalTime[]{LocalTime.of(9, 0),  LocalTime.of(16, 0)};
            case "afternoon" -> new LocalTime[]{LocalTime.of(13, 0), LocalTime.of(19, 0)};
            default          -> new LocalTime[]{LocalTime.of(18, 0), LocalTime.of(22, 0)};
        };
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
        private String eventType;
        private List<Map<String, Object>> sessions;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getEventCode() { return eventCode; }
        public void setEventCode(String eventCode) { this.eventCode = eventCode; }

        public Instant getDate() { return date; }
        public void setDate(Instant date) { this.date = date; }

        public String getVenueName() { return venueName; }
        public void setVenueName(String venueName) { this.venueName = venueName; }

        public String getVenueAddress() { return venueAddress; }
        public void setVenueAddress(String venueAddress) { this.venueAddress = venueAddress; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public List<Map<String, Object>> getSessions() { return sessions; }
        public void setSessions(List<Map<String, Object>> sessions) { this.sessions = sessions; }
    }
}
