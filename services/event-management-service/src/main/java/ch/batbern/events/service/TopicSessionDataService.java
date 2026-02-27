package ch.batbern.events.service;

import ch.batbern.events.client.PartnerApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.dto.TopicSessionDataResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Aggregates all data needed to bootstrap the blob topic selector canvas (Story 10.4 AC: 29–31).
 *
 * Four data sources:
 * 1. partnerTopics — cross-service call to partner-coordination-service
 * 2. pastEvents    — EMS events table, enriched with cluster from BatbernTopicClusterService
 * 3. organizerBacklog — EMS topics table, status=AVAILABLE (stalenessScore >= 83)
 * 4. trendingTopics — OpenAI GPT-4o-mini, 1-hour in-process cache
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TopicSessionDataService {

    private static final int AVAILABLE_STALENESS_THRESHOLD = 83;
    private static final int BACKLOG_LIMIT = 50;

    private final PartnerApiClient partnerApiClient;
    private final BatbernTopicClusterService clusterService;
    private final TrendingTopicsService trendingTopicsService;
    private final StalenessScoreService stalenessScoreService;
    private final EventRepository eventRepository;
    private final TopicRepository topicRepository;

    /**
     * Build the complete session data payload for the given event code.
     * All aggregation failures are handled gracefully: each source returns an empty list on error.
     *
     * @param eventCode the requesting event code (used for context; not filtered on)
     * @return aggregated session data
     */
    public TopicSessionDataResponse getSessionData(String eventCode) {
        log.debug("Building topic session data for event={}", eventCode);

        List<TopicSessionDataResponse.PartnerTopicGroup> partnerTopics = fetchPartnerTopics();
        List<TopicSessionDataResponse.PastEventEntry> pastEvents = fetchPastEvents();
        List<TopicSessionDataResponse.BacklogItem> organizerBacklog = fetchOrganizerBacklog();
        List<String> trendingTopics = fetchTrendingTopics();

        return TopicSessionDataResponse.builder()
                .partnerTopics(partnerTopics)
                .pastEvents(pastEvents)
                .organizerBacklog(organizerBacklog)
                .trendingTopics(trendingTopics)
                .build();
    }

    // ==================== Private aggregation methods ====================

    private List<TopicSessionDataResponse.PartnerTopicGroup> fetchPartnerTopics() {
        try {
            return partnerApiClient.getPartnerTopics().stream()
                    .map(g -> {
                        List<TopicSessionDataResponse.TopicEntry> entries = g.topics().stream()
                                .map(t -> TopicSessionDataResponse.TopicEntry.builder()
                                        .title(t.title())
                                        .cluster(clusterService.matchCluster(t.title()).name())
                                        .voteCount(t.voteCount())
                                        .createdAt(t.createdAt())
                                        .build())
                                .toList();
                        return TopicSessionDataResponse.PartnerTopicGroup.builder()
                                .companyName(g.companyName())
                                .logoUrl(g.logoUrl())
                                .topics(entries)
                                .build();
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to fetch partner topics: {}", e.getMessage());
            return List.of();
        }
    }

    private List<TopicSessionDataResponse.PastEventEntry> fetchPastEvents() {
        try {
            List<Event> events = eventRepository.findAll(Sort.by(Sort.Direction.ASC, "eventNumber"));
            return events.stream()
                    .filter(e -> e.getEventNumber() != null)
                    .map(e -> TopicSessionDataResponse.PastEventEntry.builder()
                            .eventNumber(e.getEventNumber())
                            .topicName(e.getTitle() != null ? e.getTitle() : "BATbern" + e.getEventNumber())
                            .cluster(clusterService.getCluster(e.getEventNumber()).name())
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to fetch past events: {}", e.getMessage());
            return List.of();
        }
    }

    private List<TopicSessionDataResponse.BacklogItem> fetchOrganizerBacklog() {
        try {
            List<Topic> allActive = topicRepository.findAllActive();
            java.util.Map<java.util.UUID, StalenessScoreService.StalenessData> stalenessMap =
                    stalenessScoreService.computeStalenessDataBatch(allActive);
            return allActive.stream()
                    .filter(t -> stalenessMap.getOrDefault(
                            t.getId(), StalenessScoreService.StalenessData.NEVER_USED)
                            .staleness() >= AVAILABLE_STALENESS_THRESHOLD)
                    .limit(BACKLOG_LIMIT)
                    .map(t -> TopicSessionDataResponse.BacklogItem.builder()
                            .title(t.getTitle())
                            .topicCode(t.getTopicCode())
                            .stalenessScore(stalenessMap.getOrDefault(
                                    t.getId(), StalenessScoreService.StalenessData.NEVER_USED)
                                    .staleness())
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to fetch organizer backlog: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> fetchTrendingTopics() {
        try {
            return trendingTopicsService.getTrendingTopics();
        } catch (Exception e) {
            log.warn("Failed to fetch trending topics: {}", e.getMessage());
            return TrendingTopicsService.FALLBACK_TOPICS;
        }
    }
}
