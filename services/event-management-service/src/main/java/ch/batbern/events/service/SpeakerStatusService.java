package ch.batbern.events.service;

import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.SpeakerStatusResponse;
import ch.batbern.events.dto.StatusHistoryItem;
import ch.batbern.events.dto.StatusSummaryResponse;
import ch.batbern.events.dto.UpdateStatusRequest;
import ch.batbern.events.dto.generated.EventSlotConfigurationResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.events.validator.StatusTransitionValidator;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.events.SpeakerAcceptedEvent;
import ch.batbern.shared.events.SpeakerWorkflowStateChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static ch.batbern.events.config.CacheConfig.STATUS_HISTORY_CACHE;
import static ch.batbern.events.config.CacheConfig.STATUS_SUMMARY_CACHE;

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
    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final EventTypeService eventTypeService;
    private final ApplicationEventPublisher eventPublisher;
    private final DomainEventPublisher domainEventPublisher;

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

        // Get speaker from pool
        SpeakerPool speaker = speakerPoolRepository.findById(speakerId)
                .orElseThrow(() -> new NotFoundException("Speaker not found: " + speakerId));

        // Get current status from speaker_pool entity (source of truth)
        SpeakerWorkflowState currentStatus = speaker.getStatus() != null
            ? speaker.getStatus()
            : SpeakerWorkflowState.IDENTIFIED;

        // Validate state transition - Story 5.4 AC12
        validator.validateTransition(currentStatus, request.getNewStatus());

        // Update speaker_pool status column
        speaker.setStatus(request.getNewStatus());

        // Clean up associated session when speaker is declined
        if (request.getNewStatus() == SpeakerWorkflowState.DECLINED && speaker.getSessionId() != null) {
            UUID sessionId = speaker.getSessionId();
            log.info("Declining speaker {} - deleting associated session {}", speakerId, sessionId);
            speaker.setSessionId(null);
            sessionRepository.deleteById(sessionId);
        }

        // Save updated speaker pool entry
        speakerPoolRepository.save(speaker);

        // Publish SpeakerAcceptedEvent if status changed to ACCEPTED (triggers workflow transition)
        if (request.getNewStatus() == SpeakerWorkflowState.ACCEPTED) {
            Event event = eventRepository.findById(speaker.getEventId())
                    .orElseThrow(() -> new NotFoundException("Event not found: " + speaker.getEventId()));

            SpeakerAcceptedEvent acceptedEvent = SpeakerAcceptedEvent.builder()
                    .eventId(event.getId())
                    .eventCode(event.getEventCode())
                    .speakerPoolId(speaker.getId())
                    .speakerName(speaker.getSpeakerName())
                    .company(speaker.getCompany())
                    .expertise(speaker.getExpertise())
                    .acceptedBy(organizerUsername)
                    .build();
            eventPublisher.publishEvent(acceptedEvent);
            log.debug("Published SpeakerAcceptedEvent for speaker: {}, event: {}",
                    speaker.getSpeakerName(), event.getEventCode());
        }

        // Create history record - Story 5.4 AC3-4
        SpeakerStatusHistory historyRecord = new SpeakerStatusHistory();
        historyRecord.setSpeakerPoolId(speakerId);
        // Session ID from speaker pool (null until content submitted)
        historyRecord.setSessionId(speaker.getSessionId());
        historyRecord.setEventId(speaker.getEventId()); // V29: Use eventId instead of eventCode
        historyRecord.setPreviousStatus(currentStatus);
        historyRecord.setNewStatus(request.getNewStatus());
        historyRecord.setChangedByUsername(organizerUsername);
        historyRecord.setChangeReason(request.getReason());
        historyRecord.setChangedAt(Instant.now());

        SpeakerStatusHistory saved = repository.save(historyRecord);

        // Publish SpeakerWorkflowStateChangeEvent to EventBridge (Story 6.0a CODE-001)
        publishWorkflowStateChangeEvent(speaker, currentStatus, request.getNewStatus(), organizerUsername);

        // Build response
        return mapToResponse(saved);
    }

    /**
     * Publish a speaker workflow state change event to EventBridge.
     * Story 6.0a CODE-001: Domain event publishing
     *
     * @param speaker The speaker pool entry
     * @param fromState Previous workflow state
     * @param toState New workflow state
     * @param organizerUsername Username of the organizer making the change
     */
    private void publishWorkflowStateChangeEvent(
            SpeakerPool speaker,
            SpeakerWorkflowState fromState,
            SpeakerWorkflowState toState,
            String organizerUsername
    ) {
        try {
            SpeakerWorkflowStateChangeEvent event = new SpeakerWorkflowStateChangeEvent(
                    speaker.getId(),
                    speaker.getEventId(),
                    fromState,
                    toState,
                    speaker.getUsername() != null ? speaker.getUsername() : organizerUsername
            );

            domainEventPublisher.publish(event);
            log.info("Published SpeakerWorkflowStateChangeEvent: {} -> {} for speaker {}",
                    fromState, toState, speaker.getId());
        } catch (Exception e) {
            // Log but don't fail the transaction if event publishing fails
            log.warn("Failed to publish SpeakerWorkflowStateChangeEvent for speaker {}: {}",
                    speaker.getId(), e.getMessage());
        }
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

        // Load event to get its type - Story 5.4 IMPL-002
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        // Get slot configuration from event type - Story 5.4 IMPL-002
        EventSlotConfigurationResponse typeConfig = eventTypeService.getEventType(event.getEventType());
        int minSlots = typeConfig.getMinSlots();
        int maxSlots = typeConfig.getMaxSlots();

        log.debug("Event {} type {} has slot config: min={}, max={}",
                eventCode, event.getEventType(), minSlots, maxSlots);

        // Get all speakers for the event from speaker_pool table (BUG FIX)
        // Query speaker_pool directly to get current status, not speaker_status_history
        List<SpeakerPool> speakers = speakerPoolRepository.findByEventId(event.getId());

        // Extract current status from each speaker
        Map<UUID, SpeakerWorkflowState> speakerStatuses = speakers.stream()
            .collect(Collectors.toMap(
                SpeakerPool::getId,
                SpeakerPool::getStatus
            ));

        // Calculate counts - Story 5.4 AC5
        Map<SpeakerWorkflowState, Long> statusCounts = speakerStatuses.values().stream()
            .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        long totalSpeakers = speakerStatuses.size();
        // Count speakers in ACCEPTED and all subsequent states (Story 5.5)
        long acceptedCount = statusCounts.getOrDefault(SpeakerWorkflowState.ACCEPTED, 0L)
            + statusCounts.getOrDefault(SpeakerWorkflowState.CONTENT_SUBMITTED, 0L)
            + statusCounts.getOrDefault(SpeakerWorkflowState.QUALITY_REVIEWED, 0L)
            + statusCounts.getOrDefault(SpeakerWorkflowState.CONFIRMED, 0L);
        long declinedCount = statusCounts.getOrDefault(SpeakerWorkflowState.DECLINED, 0L);
        long pendingCount = totalSpeakers - acceptedCount - declinedCount;

        // Calculate acceptance rate - Story 5.4 AC6
        double acceptanceRate = (totalSpeakers > 0)
            ? (acceptedCount * 100.0 / totalSpeakers)
            : 0.0;

        // Check thresholds and overflow using event type configuration - Story 5.4 AC13, IMPL-002
        boolean thresholdMet = acceptedCount >= minSlots;
        boolean overflowDetected = acceptedCount > maxSlots;

        StatusSummaryResponse response = new StatusSummaryResponse();
        response.setEventCode(eventCode);
        response.setStatusCounts(statusCounts);
        response.setTotalSpeakers(totalSpeakers);
        response.setAcceptedCount(acceptedCount);
        response.setDeclinedCount(declinedCount);
        response.setPendingCount(pendingCount);
        response.setAcceptanceRate(Math.round(acceptanceRate * 100.0) / 100.0);
        response.setMinSlotsRequired(minSlots);  // From event type configuration
        response.setMaxSlotsAllowed(maxSlots);   // From event type configuration
        response.setThresholdMet(thresholdMet);
        response.setOverflowDetected(overflowDetected);

        log.info("Status summary for {}: {} total, {} accepted ({}%), overflow: {}, min/max: {}/{}",
            eventCode, totalSpeakers, acceptedCount, response.getAcceptanceRate(),
            overflowDetected, minSlots, maxSlots);

        return response;
    }

    /**
     * Map entity to response DTO
     * V29: Fetch eventCode from Event entity since history now stores eventId
     */
    private SpeakerStatusResponse mapToResponse(SpeakerStatusHistory history) {
        SpeakerStatusResponse response = new SpeakerStatusResponse();
        response.setSpeakerId(history.getSpeakerPoolId());

        // V29: Look up eventCode from events table using eventId
        String eventCode = eventRepository.findById(history.getEventId())
            .map(Event::getEventCode)
            .orElse("UNKNOWN");
        response.setEventCode(eventCode);

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
