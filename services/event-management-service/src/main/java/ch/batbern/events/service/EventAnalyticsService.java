package ch.batbern.events.service;

import ch.batbern.events.analytics.dto.generated.EventAnalytics;
import ch.batbern.events.analytics.dto.generated.EventAnalyticsMetrics;
import ch.batbern.events.analytics.dto.generated.EventAnalyticsMetricsAttendance;
import ch.batbern.events.analytics.dto.generated.EventAnalyticsMetricsEngagement;
import ch.batbern.events.analytics.dto.generated.EventAnalyticsMetricsRegistrations;
import ch.batbern.events.analytics.dto.generated.EventAnalyticsMetricsRegistrationsByStatus;
import ch.batbern.events.analytics.dto.generated.EventAnalyticsTimeframe;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for generating event analytics data.
 * Story 1.15a.1: Events API Consolidation - AC13
 *
 * <p>Returns the typed {@link EventAnalytics} contract (event-analytics-api). The
 * {@code metrics} envelope is always present; individual metric blocks and {@code timeframe}
 * are populated only when requested (the DTO's {@code @JsonInclude(NON_NULL)} omits the rest,
 * matching the historical dynamic-Map wire). The caller sets {@code eventCode}.
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
     * @return Typed analytics envelope (eventCode left unset for the caller to populate)
     */
    public EventAnalytics generateAnalytics(UUID eventId, String metrics, String timeframe) {
        // Verify event exists
        eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        EventAnalytics analytics = new EventAnalytics();

        // Parse timeframe if provided
        Instant startTime = null;
        Instant endTime = null;
        if (timeframe != null && !timeframe.isEmpty()) {
            String[] times = timeframe.split(",");
            if (times.length == 2) {
                startTime = Instant.parse(times[0].trim());
                endTime = Instant.parse(times[1].trim());

                analytics.setTimeframe(new EventAnalyticsTimeframe()
                        .start(times[0].trim())
                        .end(times[1].trim()));
            }
        }

        // Parse requested metrics — the metrics envelope is always present
        EventAnalyticsMetrics metricsData = new EventAnalyticsMetrics();
        String[] requestedMetrics = metrics != null ? metrics.split(",") : new String[0];

        for (String metric : requestedMetrics) {
            switch (metric.trim()) {
                case "registrations":
                    metricsData.setRegistrations(calculateRegistrationMetrics(eventId, startTime, endTime));
                    break;
                case "attendance":
                    metricsData.setAttendance(calculateAttendanceMetrics(eventId, startTime, endTime));
                    break;
                case "engagement":
                    metricsData.setEngagement(calculateEngagementMetrics(eventId, startTime, endTime));
                    break;
                default:
                    // Ignore unknown metrics
                    break;
            }
        }

        analytics.setMetrics(metricsData);
        return analytics;
    }

    /**
     * Calculate registration metrics for an event.
     */
    private EventAnalyticsMetricsRegistrations calculateRegistrationMetrics(
            UUID eventId, Instant startTime, Instant endTime) {
        List<Registration> registrations = filterByTimeframe(
                registrationRepository.findByEventId(eventId), startTime, endTime);

        int confirmed = (int) registrations.stream().filter(r -> "confirmed".equals(r.getStatus())).count();
        int pending = (int) registrations.stream().filter(r -> "pending".equals(r.getStatus())).count();
        int cancelled = (int) registrations.stream().filter(r -> "cancelled".equals(r.getStatus())).count();

        return new EventAnalyticsMetricsRegistrations()
                .total(registrations.size())
                .byStatus(new EventAnalyticsMetricsRegistrationsByStatus()
                        .confirmed(confirmed)
                        .pending(pending)
                        .cancelled(cancelled));
    }

    /**
     * Calculate attendance metrics for an event.
     */
    private EventAnalyticsMetricsAttendance calculateAttendanceMetrics(
            UUID eventId, Instant startTime, Instant endTime) {
        // For now, attendance is similar to confirmed registrations
        List<Registration> registrations = filterByTimeframe(
                registrationRepository.findByEventId(eventId), startTime, endTime);

        int confirmedAttendees = (int) registrations.stream()
                .filter(r -> "confirmed".equals(r.getStatus()))
                .count();

        return new EventAnalyticsMetricsAttendance()
                .expected(confirmedAttendees)
                .actual(confirmedAttendees) // Stub: in real system, track actual attendance
                .rate(confirmedAttendees > 0 ? 100.0 : 0.0); // Stub: percentage
    }

    /**
     * Calculate engagement metrics for an event.
     */
    private EventAnalyticsMetricsEngagement calculateEngagementMetrics(
            UUID eventId, Instant startTime, Instant endTime) {
        List<Session> sessions = sessionRepository.findByEventId(eventId);
        List<Registration> registrations = filterByTimeframe(
                registrationRepository.findByEventId(eventId), startTime, endTime);

        return new EventAnalyticsMetricsEngagement()
                .totalSessions(sessions.size())
                .totalParticipants(registrations.size())
                .averageSessionsPerParticipant(!sessions.isEmpty() && !registrations.isEmpty()
                        ? (double) sessions.size() / registrations.size()
                        : 0.0); // Stub calculation
    }

    private static List<Registration> filterByTimeframe(
            List<Registration> registrations, Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            return registrations;
        }
        return registrations.stream()
                .filter(r -> r.getRegistrationDate() != null)
                .filter(r -> !r.getRegistrationDate().isBefore(startTime)
                        && !r.getRegistrationDate().isAfter(endTime))
                .toList();
    }
}
