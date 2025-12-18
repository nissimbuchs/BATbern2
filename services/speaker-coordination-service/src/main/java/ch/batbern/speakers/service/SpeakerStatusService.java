package ch.batbern.speakers.service;

import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.speakers.domain.SpeakerStatusHistory;
import ch.batbern.speakers.dto.SpeakerStatusResponse;
import ch.batbern.speakers.dto.StatusHistoryItem;
import ch.batbern.speakers.dto.StatusSummaryResponse;
import ch.batbern.speakers.dto.UpdateStatusRequest;
import ch.batbern.speakers.repository.SpeakerStatusHistoryRepository;
import ch.batbern.speakers.validator.StatusTransitionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static ch.batbern.speakers.config.CacheConfig.STATUS_HISTORY_CACHE;
import static ch.batbern.speakers.config.CacheConfig.STATUS_SUMMARY_CACHE;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing speaker status transitions
 * Story 5.4: Speaker Status Management - Task 5 (GREEN Phase)
 *
 * Handles:
 * - Status updates with validation (AC1-2)
 * - Status history tracking (AC3-4)
 * - Status summary calculation with acceptance rate (AC5-6)
 * - Overflow detection (AC13)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SpeakerStatusService {

    private final SpeakerStatusHistoryRepository repository;
    private final StatusTransitionValidator validator;

    // TODO: These should come from Event configuration - hardcoded for now
    private static final int DEFAULT_MIN_SPEAKERS = 6;
    private static final int DEFAULT_MAX_SPEAKERS = 8;

    /**
     * Update speaker status with validation
     * Story 5.4 AC1-2: Manual status updates with workflow validation
     * Cache eviction: Invalidates both status summary and history caches for the event
     *
     * @param eventCode Event code
     * @param speakerId Speaker pool ID
     * @param organizerUsername Username of organizer making the change
     * @param request Update request with new status and optional reason
     * @return Status change response
     */
    @CacheEvict(value = {STATUS_SUMMARY_CACHE, STATUS_HISTORY_CACHE}, key = "#eventCode")
    public SpeakerStatusResponse updateStatus(
        String eventCode,
        UUID speakerId,
        String organizerUsername,
        UpdateStatusRequest request
    ) {
        log.info("Updating speaker {} status to {} for event {} by {}",
            speakerId, request.getNewStatus(), eventCode, organizerUsername);

        // Get current status from latest history record
        List<SpeakerStatusHistory> history = repository.findBySpeakerPoolIdOrderByChangedAtDesc(speakerId);
        SpeakerWorkflowState currentStatus = history.isEmpty()
            ? SpeakerWorkflowState.IDENTIFIED
            : history.get(0).getNewStatus();

        // Validate state transition - Story 5.4 AC12
        validator.validateTransition(currentStatus, request.getNewStatus());

        // Create history record - Story 5.4 AC3-4
        SpeakerStatusHistory historyRecord = new SpeakerStatusHistory();
        historyRecord.setSpeakerPoolId(speakerId);
        historyRecord.setSessionId(UUID.randomUUID()); // TODO: Get actual session ID
        historyRecord.setEventCode(eventCode);
        historyRecord.setPreviousStatus(currentStatus);
        historyRecord.setNewStatus(request.getNewStatus());
        historyRecord.setChangedByUsername(organizerUsername);
        historyRecord.setChangeReason(request.getReason());
        historyRecord.setChangedAt(Instant.now());

        SpeakerStatusHistory saved = repository.save(historyRecord);

        // Build response
        return mapToResponse(saved);
    }

    /**
     * Get status change history for a speaker
     * Story 5.4 AC15: Query status history
     * Cached for 60 seconds per event (Story 5.4 cache requirement)
     *
     * @param eventCode Event code
     * @param speakerId Speaker pool ID
     * @return List of status changes ordered by time descending
     */
    @Transactional(readOnly = true)
    @Cacheable(value = STATUS_HISTORY_CACHE, key = "#eventCode")
    public List<StatusHistoryItem> getStatusHistory(String eventCode, UUID speakerId) {
        log.debug("Fetching status history for speaker {} in event {}", speakerId, eventCode);

        List<SpeakerStatusHistory> history = repository.findBySpeakerPoolIdOrderByChangedAtDesc(speakerId);

        if (history.isEmpty()) {
            throw new NotFoundException("No status history found for speaker: " + speakerId);
        }

        return history.stream()
            .map(this::mapToHistoryItem)
            .collect(Collectors.toList());
    }

    /**
     * Get status summary with counts and acceptance rate
     * Story 5.4 AC5-6: Status dashboard with acceptance rate
     * Story 5.4 AC13: Overflow detection
     * Cached for 60 seconds per event (Story 5.4 cache requirement - TTL: 60s)
     *
     * @param eventCode Event code
     * @return Status summary with counts, rate, and overflow detection
     */
    @Transactional(readOnly = true)
    @Cacheable(value = STATUS_SUMMARY_CACHE, key = "#eventCode")
    public StatusSummaryResponse getStatusSummary(String eventCode) {
        log.debug("Calculating status summary for event {}", eventCode);

        // Get all speakers for the event (latest status for each)
        // Note: This is simplified - in production, we'd query speaker_pool table
        // For now, we'll get unique speakers from history and their latest status

        List<SpeakerStatusHistory> allHistory = repository.findAll();

        // Group by speaker and get latest status
        Map<UUID, SpeakerWorkflowState> speakerStatuses = allHistory.stream()
            .filter(h -> h.getEventCode().equals(eventCode))
            .collect(Collectors.groupingBy(
                SpeakerStatusHistory::getSpeakerPoolId,
                Collectors.collectingAndThen(
                    Collectors.maxBy((h1, h2) -> h1.getChangedAt().compareTo(h2.getChangedAt())),
                    opt -> opt.map(SpeakerStatusHistory::getNewStatus).orElse(SpeakerWorkflowState.IDENTIFIED)
                )
            ));

        // Calculate counts - Story 5.4 AC5
        Map<SpeakerWorkflowState, Long> statusCounts = speakerStatuses.values().stream()
            .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        long totalSpeakers = speakerStatuses.size();
        long acceptedCount = statusCounts.getOrDefault(SpeakerWorkflowState.ACCEPTED, 0L);
        long declinedCount = statusCounts.getOrDefault(SpeakerWorkflowState.DECLINED, 0L);
        long pendingCount = totalSpeakers - acceptedCount - declinedCount;

        // Calculate acceptance rate - Story 5.4 AC6
        double acceptanceRate = (totalSpeakers > 0)
            ? (acceptedCount * 100.0 / totalSpeakers)
            : 0.0;

        // Check thresholds and overflow - Story 5.4 AC13
        boolean thresholdMet = acceptedCount >= DEFAULT_MIN_SPEAKERS;
        boolean overflowDetected = acceptedCount > DEFAULT_MAX_SPEAKERS;

        StatusSummaryResponse response = new StatusSummaryResponse();
        response.setEventCode(eventCode);
        response.setStatusCounts(statusCounts);
        response.setTotalSpeakers(totalSpeakers);
        response.setAcceptedCount(acceptedCount);
        response.setDeclinedCount(declinedCount);
        response.setPendingCount(pendingCount);
        response.setAcceptanceRate(Math.round(acceptanceRate * 100.0) / 100.0);
        response.setMinSlotsRequired(DEFAULT_MIN_SPEAKERS);
        response.setMaxSlotsAllowed(DEFAULT_MAX_SPEAKERS);
        response.setThresholdMet(thresholdMet);
        response.setOverflowDetected(overflowDetected);

        log.info("Status summary for {}: {} total, {} accepted ({}%), overflow: {}",
            eventCode, totalSpeakers, acceptedCount, response.getAcceptanceRate(), overflowDetected);

        return response;
    }

    /**
     * Map entity to response DTO
     */
    private SpeakerStatusResponse mapToResponse(SpeakerStatusHistory history) {
        SpeakerStatusResponse response = new SpeakerStatusResponse();
        response.setSpeakerId(history.getSpeakerPoolId());
        response.setEventCode(history.getEventCode());
        response.setCurrentStatus(history.getNewStatus());
        response.setPreviousStatus(history.getPreviousStatus());
        response.setChangedByUsername(history.getChangedByUsername());
        response.setChangeReason(history.getChangeReason());
        response.setChangedAt(history.getChangedAt());
        return response;
    }

    /**
     * Map entity to history item DTO
     */
    private StatusHistoryItem mapToHistoryItem(SpeakerStatusHistory history) {
        StatusHistoryItem item = new StatusHistoryItem();
        item.setId(history.getId());
        item.setPreviousStatus(history.getPreviousStatus());
        item.setNewStatus(history.getNewStatus());
        item.setChangedByUsername(history.getChangedByUsername());
        item.setChangeReason(history.getChangeReason());
        item.setChangedAt(history.getChangedAt());
        return item;
    }
}
