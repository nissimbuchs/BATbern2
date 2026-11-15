package ch.batbern.events.controller;

import ch.batbern.events.analytics.api.generated.EventReportingApi;
import ch.batbern.events.analytics.dto.generated.EventAnalytics;
import ch.batbern.events.service.EventAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analytics of one event ({@code EventReportingApi}, AC13), analytics module; moved out of
 * {@code EventController}.
 *
 * <p>GET /api/v1/events/{eventCode}/analytics?metrics=attendance,registrations,engagement
 * &amp;timeframe=start,end. Deliberately open to every authenticated user, not only organizers: the
 * answer holds aggregate counts (registrations, attendance, engagement), no personal data (owner
 * decision 2026-11-09). Anonymous callers get 401 from the filter chain.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class EventAnalyticsController implements EventReportingApi {

    private final EventAnalyticsService eventAnalyticsService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EventAnalytics> getEventAnalytics(String eventCode, String metrics, String timeframe) {
        log.debug("GET /api/v1/events/{}/analytics - metrics: {}, timeframe: {}", eventCode, metrics, timeframe);
        return ResponseEntity.ok(eventAnalyticsService.generateAnalytics(eventCode, metrics, timeframe));
    }
}
