package ch.batbern.events.service;

import ch.batbern.events.dto.generated.AnalyticsAttendanceResponse;
import ch.batbern.events.dto.generated.AnalyticsCompaniesResponse;
import ch.batbern.events.dto.generated.AnalyticsOverviewResponse;
import ch.batbern.events.dto.generated.AnalyticsTopicsResponse;
import ch.batbern.events.dto.generated.AttendanceEventItem;
import ch.batbern.events.dto.generated.CategoryEventCount;
import ch.batbern.events.dto.generated.CompanyAttendanceShare;
import ch.batbern.events.dto.generated.CompanyDistributionResponse;
import ch.batbern.events.dto.generated.CompanySessionItem;
import ch.batbern.events.dto.generated.CompanyYearAttendanceItem;
import ch.batbern.events.dto.generated.EventTimelineItem;
import ch.batbern.events.dto.generated.TopicScatterItem;
import ch.batbern.events.repository.AnalyticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service orchestrating analytics queries for the Analytics Dashboard (Story 10.5).
 *
 * Implements the returning/new attendees classification algorithm
 * using in-memory processing of at most ~3,000 rows (58 events × ~50 attendees).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    private final AnalyticsRepository analyticsRepository;

    // ── Overview ─────────────────────────────────────────────────────────────

    /**
     * Returns all-time KPI totals and event cadence timeline.
     * Neither KPIs nor timeline respect the global time range filter — always all-time.
     * AC2.
     */
    public AnalyticsOverviewResponse getOverview() {
        log.debug("Computing analytics overview (all-time)");

        long totalEvents = analyticsRepository.countTotalEvents();
        long totalAttendees = analyticsRepository.countTotalAttendees();
        long companies = analyticsRepository.countDistinctCompanies();
        long totalSessions = analyticsRepository.countTotalSessions();
        long totalSpeakers = analyticsRepository.countTotalSpeakers();

        List<Object[]> timelineRows = analyticsRepository.findAllEventsForTimeline();
        List<EventTimelineItem> timeline = timelineRows.stream()
                .map(row -> new EventTimelineItem()
                        .eventCode((String) row[1])
                        .eventNumber((Integer) row[2])
                        .title((String) row[3])
                        .eventDate(toOffsetDateTime((Instant) row[4]))
                        .category((String) row[5])
                        .attendeeCount(0)) // attendeeCount not in timeline query; set 0 as placeholder
                .toList();

        // enrich timeline with attendee counts (all-time: use EPOCH as "no filter")
        List<Object[]> totals = analyticsRepository.findPerEventAttendanceTotals(Instant.EPOCH);
        Map<String, Long> attendeesByCode = new HashMap<>();
        for (Object[] row : totals) {
            attendeesByCode.put((String) row[1], toLong(row[6]));
        }
        List<EventTimelineItem> enrichedTimeline = new ArrayList<>(timeline.size());
        for (EventTimelineItem item : timeline) {
            Long count = attendeesByCode.getOrDefault(item.getEventCode(), 0L);
            enrichedTimeline.add(new EventTimelineItem()
                    .eventCode(item.getEventCode())
                    .eventNumber(item.getEventNumber())
                    .title(item.getTitle())
                    .eventDate(item.getEventDate())
                    .category(item.getCategory())
                    .attendeeCount(count.intValue()));
        }

        return new AnalyticsOverviewResponse(
                (int) totalEvents,
                (int) totalAttendees,
                (int) companies,
                (int) totalSessions,
                (int) totalSpeakers,
                enrichedTimeline);
    }

    // ── Attendance ───────────────────────────────────────────────────────────

    /**
     * Returns per-event attendance with returning/new attendees breakdown.
     * AC3.
     *
     * @param fromYear optional year filter (inclusive); null = all-time
     */
    public AnalyticsAttendanceResponse getAttendance(Integer fromYear) {
        log.debug("Computing attendance analytics fromYear={}", fromYear);

        Instant fromDate = fromYear != null ? toInstant(fromYear) : Instant.EPOCH;

        // Load ALL historical attendances (no date filter) for the returning/new algorithm
        List<Object[]> allAttendances = analyticsRepository.findAllAttendancesForReturningAlgorithm();

        // Compute returning/new breakdown per event using in-memory algorithm
        // Algorithm: first appearance of a username = "new", subsequent = "returning"
        Map<String, Instant> firstEventByUser = new HashMap<>();
        // eventId → breakdown (new, returning)
        Map<UUID, AttendanceBreakdown> breakdown = new LinkedHashMap<>();

        for (Object[] row : allAttendances) {
            String username = (String) row[0];
            UUID eventId = (UUID) row[1];
            Instant eventDate = (Instant) row[2];

            breakdown.computeIfAbsent(eventId, id -> new AttendanceBreakdown());

            if (!firstEventByUser.containsKey(username)) {
                firstEventByUser.put(username, eventDate);
                breakdown.get(eventId).newCount++;
            } else {
                breakdown.get(eventId).returningCount++;
            }
        }

        // Load per-event totals with the optional fromYear filter
        List<Object[]> totals = analyticsRepository.findPerEventAttendanceTotals(fromDate);

        List<AttendanceEventItem> events = totals.stream()
                .map(row -> {
                    UUID eventId = (UUID) row[0];
                    String eventCode = (String) row[1];
                    Integer eventNumber = (Integer) row[2];
                    String title = (String) row[3];
                    Instant date = (Instant) row[4];
                    String category = (String) row[5];
                    int total = toLong(row[6]).intValue();

                    AttendanceBreakdown bd = breakdown.getOrDefault(eventId, new AttendanceBreakdown());
                    return new AttendanceEventItem(
                            eventCode, eventNumber, title, toOffsetDateTime(date), total,
                            bd.returningCount, bd.newCount)
                            .category(category);
                })
                .toList();

        return new AnalyticsAttendanceResponse().events(events);
    }

    // ── Topics ───────────────────────────────────────────────────────────────

    /**
     * Returns events per category and topic scatter data.
     * AC4.
     *
     * @param fromYear optional year filter; null = all-time
     */
    public AnalyticsTopicsResponse getTopics(Integer fromYear) {
        log.debug("Computing topics analytics fromYear={}", fromYear);

        Instant fromDate = fromYear != null ? toInstant(fromYear) : Instant.EPOCH;

        List<Object[]> categoryRows = analyticsRepository.findEventsPerCategory(fromDate);
        List<CategoryEventCount> eventsPerCategory = categoryRows.stream()
                .map(row -> new CategoryEventCount()
                        .category((String) row[0])
                        .eventCount(toLong(row[1]).intValue()))
                .toList();

        List<Object[]> scatterRows = analyticsRepository.findTopicScatterData(fromDate);
        List<TopicScatterItem> topicScatter = scatterRows.stream()
                .map(row -> new TopicScatterItem()
                        .topicCode((String) row[0])
                        .topicTitle((String) row[1])
                        .category((String) row[2])
                        .eventCount(toInt(row[3]))
                        .avgAttendees(toDouble(row[4])))
                .toList();

        return new AnalyticsTopicsResponse()
                .eventsPerCategory(eventsPerCategory)
                .topicScatter(topicScatter);
    }

    // ── Companies ────────────────────────────────────────────────────────────

    /**
     * Returns company analytics: attendance over time, sessions, and distribution.
     * AC5.
     *
     * @param fromYear optional year filter; null = all-time
     */
    public AnalyticsCompaniesResponse getCompanies(Integer fromYear) {
        log.debug("Computing companies analytics fromYear={}", fromYear);

        Instant fromDate = fromYear != null ? toInstant(fromYear) : Instant.EPOCH;

        List<Object[]> overTimeRows = analyticsRepository.findAttendanceByYearAndCompany(fromDate);
        List<CompanyYearAttendanceItem> attendanceOverTime = overTimeRows.stream()
                .map(row -> new CompanyYearAttendanceItem()
                        .year(toInt(row[0]))
                        .companyName((String) row[1])
                        .displayName((String) row[2])
                        .attendeeCount(toLong(row[3]).intValue()))
                .toList();

        List<Object[]> sessionRows = analyticsRepository.findSessionsPerCompany(fromDate);
        List<CompanySessionItem> sessionsPerCompany = sessionRows.stream()
                .map(row -> new CompanySessionItem()
                        .companyName((String) row[0])
                        .displayName((String) row[1])
                        .sessionCount(toLong(row[2]).intValue())
                        .uniqueSpeakers(toLong(row[3]).intValue()))
                .toList();

        List<Object[]> distRows = analyticsRepository.findCompanyDistribution(fromDate);
        List<CompanyAttendanceShare> distribution = distRows.stream()
                .map(row -> new CompanyAttendanceShare()
                        .companyName((String) row[0])
                        .displayName((String) row[1])
                        .attendeeCount(toLong(row[2]).intValue()))
                .toList();

        return new AnalyticsCompaniesResponse()
                .attendanceOverTime(attendanceOverTime)
                .sessionsPerCompany(sessionsPerCompany)
                .distribution(distribution);
    }

    /**
     * Returns per-event company attendee distribution.
     * AC5.
     *
     * @param eventCode event code (e.g. "BATbern57")
     */
    public CompanyDistributionResponse getCompanyDistribution(String eventCode) {
        log.debug("Computing company distribution for eventCode={}", eventCode);

        List<Object[]> rows = analyticsRepository.findCompanyDistributionByEvent(eventCode);
        List<CompanyAttendanceShare> distribution = rows.stream()
                .map(row -> new CompanyAttendanceShare()
                        .companyName((String) row[0])
                        .displayName((String) row[1])
                        .attendeeCount(toLong(row[2]).intValue()))
                .toList();

        return new CompanyDistributionResponse()
                .eventCode(eventCode)
                .distribution(distribution);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Instant toInstant(int year) {
        return LocalDate.of(year, 1, 1).atStartOfDay(ZURICH).toInstant();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(ZURICH).toOffsetDateTime();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Double d) {
            return d;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }

    /**
     * Internal mutable holder for the returning/new attendees algorithm.
     */
    private static class AttendanceBreakdown {
        int returningCount = 0;
        int newCount = 0;
    }
}
