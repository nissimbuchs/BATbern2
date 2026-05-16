package ch.batbern.events.service;

import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
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
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.workflow.SecurityPrincipal;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.events.service.workflow.TransitionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static ch.batbern.events.config.CacheConfig.STATUS_HISTORY_CACHE;
import static ch.batbern.events.config.CacheConfig.STATUS_SUMMARY_CACHE;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing speaker status transitions
 * Story 5.4: Speaker Status Management - Task 5 (GREEN Phase)
 *
 * Handles:
 * - Status updates with validation (AC1-2) — delegates to {@link SpeakerWorkflowService#transition}
 *   (Story 11.B.2: sole status writer per ADR-009).
 * - Status history reads (AC3-4)
 * - Status summary calculation with acceptance rate (AC5-6)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SpeakerStatusService {

    private final SpeakerStatusHistoryRepository repository;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final EventTypeService eventTypeService;
    private final SpeakerWorkflowService speakerWorkflowService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * Update speaker status by delegating to {@link SpeakerWorkflowService#transition} — the
     * sole writer of {@code speaker_pool.status} per ADR-009.
     *
     * <p>This method:
     * <ul>
     *   <li>Loads the speaker for an early 404 check.</li>
     *   <li>Builds a {@link TransitionPayload} with {@code reason} from the request.</li>
     *   <li>Constructs the {@link SecurityPrincipal} from the controller-supplied
     *       organizer username + the current Spring Security roles.</li>
     *   <li>Calls {@code speakerWorkflowService.transition(...)} which validates the
     *       transition, runs preconditions + side-effect hooks, persists, writes a
     *       {@code speaker_status_history} row, and publishes the canonical
     *       {@code SpeakerWorkflowStateChangeEvent} + state-specific events.</li>
     *   <li>Evicts the status-summary and status-history caches for the event.</li>
     * </ul>
     *
     * @param eventCode event code
     * @param speakerId speaker pool ID
     * @param organizerUsername username of organizer making the change
     * @param request update request with new status and optional reason
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

        // Early existence check
        SpeakerPool speaker = speakerPoolRepository.findById(speakerId)
                .orElseThrow(() -> new NotFoundException("Speaker not found: " + speakerId));

        // Organizer-facing API: forbid same-state writes on terminal DECLINED to prevent
        // audit-trail pollution via reason-less "re-affirm" clicks. Programmatic same-state
        // writes via transition() remain possible (e.g. system replay).
        if (request.getNewStatus() == SpeakerWorkflowState.DECLINED
                && speaker.getStatus() == SpeakerWorkflowState.DECLINED) {
            throw new ValidationException(
                    "Cannot re-affirm a DECLINED speaker — terminal state has no outgoing transitions.");
        }

        TransitionPayload payload = TransitionPayload.builder()
                .reason(request.getReason())
                .build();

        List<String> roles;
        try {
            roles = securityContextHelper.getCurrentUserRoles();
        } catch (SecurityException ex) {
            // No security context (e.g., system-driven path): fall back to empty roles.
            roles = List.of();
        }
        SecurityPrincipal actor = new SecurityPrincipal(organizerUsername, roles);

        TransitionResult result = speakerWorkflowService.transition(
                speakerId, request.getNewStatus(), actor, payload);

        return mapToResponse(result.history());
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
     * Cached for 60 seconds per event (Story 5.4 cache requirement - TTL: 60s)
     *
     * @param eventCode Event code
     * @return Status summary with counts, rate, and overflow detection
     */
    @Transactional(readOnly = true)
    @Cacheable(value = STATUS_SUMMARY_CACHE, key = "#eventCode")
    public StatusSummaryResponse getStatusSummary(String eventCode) {
        log.debug("Calculating status summary for event {}", eventCode);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        EventSlotConfigurationResponse typeConfig = eventTypeService.getEventType(event.getEventType());
        int minSlots = typeConfig.getMinSlots();
        int maxSlots = typeConfig.getMaxSlots();

        log.debug("Event {} type {} has slot config: min={}, max={}",
                eventCode, event.getEventType(), minSlots, maxSlots);

        List<SpeakerPool> speakers = speakerPoolRepository.findByEventId(event.getId());

        Map<UUID, SpeakerWorkflowState> speakerStatuses = speakers.stream()
            .collect(Collectors.toMap(
                SpeakerPool::getId,
                SpeakerPool::getStatus
            ));

        Map<SpeakerWorkflowState, Long> statusCounts = speakerStatuses.values().stream()
            .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        long totalSpeakers = speakerStatuses.size();
        // ADR-009 §0.1: CONFIRMED is removed; "accepted-track" speakers are ACCEPTED + later
        // content-lifecycle states. The derived is_publishable predicate (QUALITY_REVIEWED AND
        // slot_assigned) is exposed in 11.B.3, not here.
        long acceptedCount = statusCounts.getOrDefault(SpeakerWorkflowState.ACCEPTED, 0L)
            + statusCounts.getOrDefault(SpeakerWorkflowState.CONTENT_SUBMITTED, 0L)
            + statusCounts.getOrDefault(SpeakerWorkflowState.QUALITY_REVIEWED, 0L);
        long declinedCount = statusCounts.getOrDefault(SpeakerWorkflowState.DECLINED, 0L);
        long pendingCount = totalSpeakers - acceptedCount - declinedCount;

        double acceptanceRate = (totalSpeakers > 0)
            ? (acceptedCount * 100.0 / totalSpeakers)
            : 0.0;

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
        response.setMinSlotsRequired(minSlots);
        response.setMaxSlotsAllowed(maxSlots);
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
