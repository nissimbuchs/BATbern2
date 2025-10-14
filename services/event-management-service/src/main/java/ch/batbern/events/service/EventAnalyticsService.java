package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for generating event analytics data.
 * Story 1.15a.1: Events API Consolidation - AC13
 */
@Service
@RequiredArgsConstructor
public class EventAnalyticsService {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final SessionRepository sessionRepository;

    /**
     * Generate analytics for an event with specified metrics and optional timeframe.
     *
     * @param eventId Event identifier
     * @param metrics Comma-separated list of metrics (attendance, registrations, engagement)
     * @param timeframe Optional timeframe as "startTime,endTime" (ISO-8601 format)
     * @return Map containing analytics data
     */
    public Map<String, Object> generateAnalytics(UUID eventId, String metrics, String timeframe) {
        // Verify event exists
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("eventId", eventId);

        // Parse timeframe if provided
        Instant startTime = null;
        Instant endTime = null;
        if (timeframe != null && !timeframe.isEmpty()) {
            String[] times = timeframe.split(",");
            if (times.length == 2) {
                startTime = Instant.parse(times[0].trim());
                endTime = Instant.parse(times[1].trim());

                Map<String, String> timeframeMap = new HashMap<>();
                timeframeMap.put("start", times[0].trim());
                timeframeMap.put("end", times[1].trim());
                analytics.put("timeframe", timeframeMap);
            }
        }

        // Parse requested metrics
        String[] requestedMetrics = metrics != null ? metrics.split(",") : new String[0];
        Map<String, Object> metricsData = new HashMap<>();

        for (String metric : requestedMetrics) {
            String metricTrimmed = metric.trim();
            switch (metricTrimmed) {
                case "registrations":
                    metricsData.put("registrations", calculateRegistrationMetrics(eventId, startTime, endTime));
                    break;
                case "attendance":
                    metricsData.put("attendance", calculateAttendanceMetrics(eventId, startTime, endTime));
                    break;
                case "engagement":
                    metricsData.put("engagement", calculateEngagementMetrics(eventId, startTime, endTime));
                    break;
                default:
                    // Ignore unknown metrics
                    break;
            }
        }

        analytics.put("metrics", metricsData);
        return analytics;
    }

    /**
     * Calculate registration metrics for an event.
     */
    private Map<String, Object> calculateRegistrationMetrics(UUID eventId, Instant startTime, Instant endTime) {
        List<Registration> registrations = registrationRepository.findByEventId(eventId);

        // Filter by timeframe if provided
        if (startTime != null && endTime != null) {
            registrations = registrations.stream()
                    .filter(r -> r.getRegistrationDate() != null)
                    .filter(r -> !r.getRegistrationDate().isBefore(startTime) && !r.getRegistrationDate().isAfter(endTime))
                    .toList();
        }

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("total", registrations.size());

        long confirmed = registrations.stream().filter(r -> "confirmed".equals(r.getStatus())).count();
        long pending = registrations.stream().filter(r -> "pending".equals(r.getStatus())).count();
        long cancelled = registrations.stream().filter(r -> "cancelled".equals(r.getStatus())).count();

        Map<String, Long> byStatus = new HashMap<>();
        byStatus.put("confirmed", confirmed);
        byStatus.put("pending", pending);
        byStatus.put("cancelled", cancelled);

        metrics.put("byStatus", byStatus);
        return metrics;
    }

    /**
     * Calculate attendance metrics for an event.
     */
    private Map<String, Object> calculateAttendanceMetrics(UUID eventId, Instant startTime, Instant endTime) {
        // For now, attendance is similar to confirmed registrations
        List<Registration> registrations = registrationRepository.findByEventId(eventId);

        // Filter by timeframe if provided
        if (startTime != null && endTime != null) {
            registrations = registrations.stream()
                    .filter(r -> r.getRegistrationDate() != null)
                    .filter(r -> !r.getRegistrationDate().isBefore(startTime) && !r.getRegistrationDate().isAfter(endTime))
                    .toList();
        }

        long confirmedAttendees = registrations.stream()
                .filter(r -> "confirmed".equals(r.getStatus()))
                .count();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("expected", confirmedAttendees);
        metrics.put("actual", confirmedAttendees); // Stub: in real system, track actual attendance
        metrics.put("rate", confirmedAttendees > 0 ? 100.0 : 0.0); // Stub: percentage

        return metrics;
    }

    /**
     * Calculate engagement metrics for an event.
     */
    private Map<String, Object> calculateEngagementMetrics(UUID eventId, Instant startTime, Instant endTime) {
        List<Session> sessions = sessionRepository.findByEventId(eventId);
        List<Registration> registrations = registrationRepository.findByEventId(eventId);

        // Filter by timeframe if provided
        if (startTime != null && endTime != null) {
            registrations = registrations.stream()
                    .filter(r -> r.getRegistrationDate() != null)
                    .filter(r -> !r.getRegistrationDate().isBefore(startTime) && !r.getRegistrationDate().isAfter(endTime))
                    .toList();
        }

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalSessions", sessions.size());
        metrics.put("totalParticipants", registrations.size());
        metrics.put("averageSessionsPerParticipant", sessions.size() > 0 && registrations.size() > 0
                ? (double) sessions.size() / registrations.size()
                : 0.0); // Stub calculation

        return metrics;
    }
}
