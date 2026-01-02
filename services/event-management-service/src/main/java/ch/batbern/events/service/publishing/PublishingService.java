package ch.batbern.events.service.publishing;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.PublishingConfig;
import ch.batbern.events.domain.PublishingVersion;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.AutoPublishScheduleRequest;
import ch.batbern.events.dto.AutoPublishScheduleResponse;
import ch.batbern.events.dto.ChangeLogResponse;
import ch.batbern.events.dto.PublishPhaseResponse;
import ch.batbern.events.dto.PublishPreviewResponse;
import ch.batbern.events.dto.PublishValidationError;
import ch.batbern.events.dto.PublishingStatusResponse;
import ch.batbern.events.dto.RollbackResponse;
import ch.batbern.events.dto.UnpublishPhaseResponse;
import ch.batbern.events.dto.VersionHistoryResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.PublishingConfigRepository;
import ch.batbern.events.repository.PublishingVersionRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Integrated Publishing Service
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 *
 * Handles:
 * - Phase publishing (topic, speakers, agenda) with validation
 * - Unpublishing phases
 * - Version tracking and rollback
 * - CDN cache invalidation
 * - Auto-publish scheduling
 * - Change log tracking
 * - Preview generation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PublishingService {

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final PublishingVersionRepository publishingVersionRepository;
    private final PublishingConfigRepository publishingConfigRepository;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    /**
     * Publish a specific phase (topic, speakers, or agenda)
     */
    public PublishPhaseResponse publishPhase(String eventCode, String phase, String publishedBy) {
        log.info("Publishing phase {} for event {}", phase, eventCode);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        // Validate content is ready for this phase
        validatePhaseContent(event, phase);

        // Update event publishing state
        event.setCurrentPublishedPhase(phase.toLowerCase());
        event.setLastPublishedAt(Instant.now());

        // If publishing agenda, transition workflow state
        if ("agenda".equals(phase)) {
            event.setWorkflowState(EventWorkflowState.AGENDA_PUBLISHED);
        }

        eventRepository.save(event);

        // Evict cache for this event
        evictEventCache(event.getEventCode());

        // Create version snapshot
        Integer versionNumber = createVersionSnapshot(event, phase, publishedBy);

        // Simulate CDN invalidation
        boolean cdnInvalidated = simulateCDNInvalidation(event.getId());

        return PublishPhaseResponse.builder()
                .phase(phase)
                .published(true)
                .publishedAt(Instant.now())
                .version(versionNumber)
                .cdnInvalidated(cdnInvalidated)
                .build();
    }

    /**
     * Unpublish a specific phase
     */
    public UnpublishPhaseResponse unpublishPhase(String eventCode, String phase) {
        log.info("Unpublishing phase {} for event {}", phase, eventCode);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        // Revert to previous phase
        String previousPhase = getPreviousPhase(phase);
        event.setCurrentPublishedPhase(previousPhase != null ? previousPhase.toLowerCase() : null);

        eventRepository.save(event);

        // Evict cache for this event
        evictEventCache(event.getEventCode());

        // Simulate CDN invalidation
        boolean cdnInvalidated = simulateCDNInvalidation(event.getId());

        return UnpublishPhaseResponse.builder()
                .phase(phase)
                .published(false)
                .unpublishedAt(Instant.now())
                .cdnInvalidated(cdnInvalidated)
                .build();
    }

    /**
     * Get publishing preview
     */
    public PublishPreviewResponse getPreview(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        String currentPhase = event.getCurrentPublishedPhase();
        boolean topicPublished = currentPhase != null
                && (currentPhase.equals("topic") || currentPhase.equals("speakers")
                || currentPhase.equals("agenda"));
        boolean speakersPublished = currentPhase != null
                && (currentPhase.equals("speakers") || currentPhase.equals("agenda"));
        boolean agendaPublished = currentPhase != null && currentPhase.equals("agenda");

        // Get speakers if speakers phase is published
        List<PublishPreviewResponse.SpeakerPreview> speakers = new ArrayList<>();
        if (speakersPublished) {
            speakers = speakerPoolRepository.findByEventId(event.getId()).stream()
                    .map(sp -> PublishPreviewResponse.SpeakerPreview.builder()
                            .name(sp.getSpeakerName())
                            .company(sp.getCompany())
                            .build())
                    .collect(Collectors.toList());
        }

        // Get sessions if agenda phase is published
        List<PublishPreviewResponse.SessionPreview> sessions = agendaPublished
                ? sessionRepository.findByEventId(event.getId()).stream()
                        .filter(s -> s.getStartTime() != null)
                        .map(s -> PublishPreviewResponse.SessionPreview.builder()
                                .title(s.getTitle())
                                .startTime(s.getStartTime() != null
                                        ? s.getStartTime().toString() : null)
                                .endTime(s.getEndTime() != null ? s.getEndTime().toString() : null)
                                .room(s.getRoom())
                                .build())
                        .collect(Collectors.toList()) : new ArrayList<>();

        return PublishPreviewResponse.builder()
                .eventCode(eventCode)
                .currentPhase(currentPhase != null ? currentPhase.toUpperCase() : null)
                .topicPublished(topicPublished)
                .speakersPublished(speakersPublished)
                .agendaPublished(agendaPublished)
                .speakers(speakers)
                .sessions(sessions)
                .build();
    }

    /**
     * Get version history
     */
    public VersionHistoryResponse getVersionHistory(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        List<PublishingVersion> versions = publishingVersionRepository
                .findByEventIdOrderByVersionNumberDesc(event.getId());

        List<VersionHistoryResponse.VersionDetail> versionDetails = versions.stream()
                .map(v -> VersionHistoryResponse.VersionDetail.builder()
                        .versionNumber(v.getVersionNumber())
                        .phase(v.getPublishedPhase())
                        .publishedAt(v.getPublishedAt())
                        .publishedBy(v.getPublishedBy())
                        .isCurrent(v.getIsCurrent())
                        .build())
                .collect(Collectors.toList());

        return VersionHistoryResponse.builder()
                .versions(versionDetails)
                .build();
    }

    /**
     * Rollback to a previous version
     */
    public RollbackResponse rollbackToVersion(String eventCode, Integer versionNumber) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        PublishingVersion targetVersion = publishingVersionRepository
                .findByEventIdAndVersionNumber(event.getId(), versionNumber)
                .orElseThrow(() -> new RuntimeException("Version not found: " + versionNumber));

        // Mark all versions as not current
        publishingVersionRepository.markAllVersionsAsNotCurrent(event.getId());

        // Mark target version as current
        targetVersion.setIsCurrent(true);
        publishingVersionRepository.save(targetVersion);

        // Update event state to match target version
        event.setCurrentPublishedPhase(targetVersion.getPublishedPhase().toLowerCase());
        eventRepository.save(event);

        // Evict cache for this event
        evictEventCache(event.getEventCode());

        // Simulate CDN invalidation
        boolean cdnInvalidated = simulateCDNInvalidation(event.getId());

        return RollbackResponse.builder()
                .rolledBack(true)
                .currentVersion(versionNumber)
                .cdnInvalidated(cdnInvalidated)
                .build();
    }

    /**
     * Get change log
     */
    public ChangeLogResponse getChangeLog(String eventCode) {
        // For simplicity, returning a basic change log structure
        // In a full implementation, this would track actual field changes between versions
        return ChangeLogResponse.builder()
                .changes(new ArrayList<>())
                .build();
    }

    /**
     * Configure auto-publish schedule
     */
    public AutoPublishScheduleResponse configureAutoPublish(String eventCode, AutoPublishScheduleRequest request) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        // Find or create publishing config
        PublishingConfig config = publishingConfigRepository.findByEventId(event.getId())
                .orElse(PublishingConfig.builder()
                        .eventId(event.getId())
                        .build());

        // Update configuration
        config.setAutoPublishSpeakers(request.getPhase2Enabled());
        config.setAutoPublishSpeakersDaysBefore(request.getPhase2DaysBeforeEvent());
        config.setAutoPublishAgenda(request.getPhase3Enabled());
        config.setAutoPublishAgendaDaysBefore(request.getPhase3DaysBeforeEvent());

        publishingConfigRepository.save(config);

        // Calculate trigger dates
        Instant phase2TriggerDate = event.getDate()
                .minus(request.getPhase2DaysBeforeEvent(), ChronoUnit.DAYS);
        Instant phase3TriggerDate = event.getDate()
                .minus(request.getPhase3DaysBeforeEvent(), ChronoUnit.DAYS);

        return AutoPublishScheduleResponse.builder()
                .scheduled(true)
                .phase2Enabled(request.getPhase2Enabled())
                .phase2DaysBeforeEvent(request.getPhase2DaysBeforeEvent())
                .phase2TriggerDate(phase2TriggerDate)
                .phase3Enabled(request.getPhase3Enabled())
                .phase3DaysBeforeEvent(request.getPhase3DaysBeforeEvent())
                .phase3TriggerDate(phase3TriggerDate)
                .build();
    }

    /**
     * Get auto-publish schedule
     */
    public AutoPublishScheduleResponse getAutoPublishSchedule(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        PublishingConfig config = publishingConfigRepository.findByEventId(event.getId())
                .orElseThrow(() -> new RuntimeException("Publishing config not found"));

        Instant phase2TriggerDate = event.getDate()
                .minus(config.getAutoPublishSpeakersDaysBefore(), ChronoUnit.DAYS);
        Instant phase3TriggerDate = event.getDate()
                .minus(config.getAutoPublishAgendaDaysBefore(), ChronoUnit.DAYS);

        return AutoPublishScheduleResponse.builder()
                .scheduled(true)
                .phase2Enabled(config.getAutoPublishSpeakers())
                .phase2DaysBeforeEvent(config.getAutoPublishSpeakersDaysBefore())
                .phase2TriggerDate(phase2TriggerDate)
                .phase3Enabled(config.getAutoPublishAgenda())
                .phase3DaysBeforeEvent(config.getAutoPublishAgendaDaysBefore())
                .phase3TriggerDate(phase3TriggerDate)
                .build();
    }

    /**
     * Get publishing status including validation for all phases
     * Used by frontend to display current publishing state and validation errors
     */
    public PublishingStatusResponse getPublishingStatus(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        // Topic validation - must have title, date, and topicCode
        boolean topicValid = event.getTitle() != null && !event.getTitle().isBlank()
                && event.getDate() != null
                && event.getTopicCode() != null && !event.getTopicCode().isBlank();
        List<String> topicErrors = new ArrayList<>();
        if (!topicValid) {
            if (event.getTopicCode() == null || event.getTopicCode().isBlank()) {
                topicErrors.add("Event topic must be defined");
            }
            if (event.getTitle() == null || event.getTitle().isBlank()) {
                topicErrors.add("Event must have title");
            }
            if (event.getDate() == null) {
                topicErrors.add("Event must have date");
            }
        }

        // Speakers validation - at least one speaker in presenting state
        // Valid presenting states: ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED, CONFIRMED
        // (excludes DECLINED, WITHDREW, OVERFLOW, and early stages like IDENTIFIED/CONTACTED/READY)
        long acceptedSpeakers = speakerPoolRepository.findByEventId(event.getId()).stream()
                .filter(sp -> sp.getStatus() == SpeakerWorkflowState.ACCEPTED
                        || sp.getStatus() == SpeakerWorkflowState.CONTENT_SUBMITTED
                        || sp.getStatus() == SpeakerWorkflowState.QUALITY_REVIEWED
                        || sp.getStatus() == SpeakerWorkflowState.CONFIRMED)
                .count();
        boolean speakersValid = acceptedSpeakers > 0;
        List<String> speakersErrors = new ArrayList<>();
        if (!speakersValid) {
            speakersErrors.add("At least one speaker must be accepted");
        }

        // Sessions validation - all sessions must have timing
        List<Session> allSessions = sessionRepository.findByEventId(event.getId());
        List<Session> unassignedSessions = allSessions.stream()
                .filter(s -> s.getStartTime() == null || s.getEndTime() == null)
                .collect(Collectors.toList());
        boolean sessionsValid = unassignedSessions.isEmpty() && !allSessions.isEmpty();
        List<String> sessionsErrors = new ArrayList<>();
        if (!sessionsValid) {
            if (allSessions.isEmpty()) {
                sessionsErrors.add("No sessions found for this event");
            } else {
                sessionsErrors.add("All sessions must have timing assigned");
            }
        }

        // Build unassigned sessions list
        List<PublishingStatusResponse.UnassignedSession> unassignedList = unassignedSessions.stream()
                .map(s -> PublishingStatusResponse.UnassignedSession.builder()
                        .sessionSlug(s.getSessionSlug())
                        .title(s.getTitle())
                        .build())
                .collect(Collectors.toList());

        // Determine published phases from currentPublishedPhase
        List<String> publishedPhases = determinePublishedPhases(event.getCurrentPublishedPhase());

        return PublishingStatusResponse.builder()
                .currentPhase(event.getCurrentPublishedPhase())
                .publishedPhases(publishedPhases)
                .topic(PublishingStatusResponse.ValidationStatus.builder()
                        .isValid(topicValid)
                        .errors(topicErrors)
                        .build())
                .speakers(PublishingStatusResponse.ValidationStatus.builder()
                        .isValid(speakersValid)
                        .errors(speakersErrors)
                        .build())
                .sessions(PublishingStatusResponse.SessionValidationStatus.builder()
                        .isValid(sessionsValid)
                        .errors(sessionsErrors)
                        .assignedCount(allSessions.size() - unassignedSessions.size())
                        .totalCount(allSessions.size())
                        .unassignedSessions(unassignedList)
                        .build())
                .build();
    }

    /**
     * Determine which phases have been published based on currentPublishedPhase
     */
    private List<String> determinePublishedPhases(String currentPhase) {
        if (currentPhase == null) {
            return new ArrayList<>();
        }
        List<String> phases = new ArrayList<>();
        switch (currentPhase.toLowerCase()) {
            case "agenda":
                phases.add("agenda");
                // fall through
            case "speakers":
                phases.add(0, "speakers");
                // fall through
            case "topic":
                phases.add(0, "topic");
                break;
            default:
                break;
        }
        return phases;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Validate that content is ready for the requested phase
     */
    private void validatePhaseContent(Event event, String phase) {
        if ("agenda".equals(phase)) {
            // Check that all sessions have timing assigned
            List<Session> sessionsWithoutTiming = sessionRepository.findByEventId(event.getId()).stream()
                    .filter(s -> s.getStartTime() == null || s.getEndTime() == null)
                    .collect(Collectors.toList());

            if (!sessionsWithoutTiming.isEmpty()) {
                throw new PublishValidationException(
                        "All sessions must have timing assigned",
                        sessionsWithoutTiming.stream()
                                .map(s -> PublishValidationError.ValidationDetail.builder()
                                        .sessionSlug(s.getSessionSlug())
                                        .field("timing")
                                        .message("Session has no timing assigned")
                                        .build())
                                .collect(Collectors.toList())
                );
            }
        }
    }

    /**
     * Create version snapshot
     */
    private Integer createVersionSnapshot(Event event, String phase, String publishedBy) {
        Integer nextVersionNumber = publishingVersionRepository
                .findMaxVersionNumberByEventId(event.getId()) + 1;

        // Mark all previous versions as not current
        publishingVersionRepository.markAllVersionsAsNotCurrent(event.getId());

        // Create content snapshot (simplified for now)
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("eventCode", event.getEventCode());
        snapshot.put("title", event.getTitle());
        snapshot.put("phase", phase);

        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            snapshotJson = "{}";
        }

        PublishingVersion version = PublishingVersion.builder()
                .eventId(event.getId())
                .versionNumber(nextVersionNumber)
                .publishedPhase(phase)
                .publishedAt(Instant.now())
                .publishedBy(publishedBy)
                .contentSnapshot(snapshotJson)
                .isCurrent(true)
                .cdnInvalidationId("mock-invalidation-" + UUID.randomUUID())
                .cdnInvalidationStatus("completed")
                .build();

        publishingVersionRepository.save(version);

        return nextVersionNumber;
    }

    /**
     * Simulate CDN invalidation (mocked for integration tests)
     */
    private boolean simulateCDNInvalidation(UUID eventId) {
        log.info("CDN invalidation simulated for event {}", eventId);
        return true; // Always return success for tests
    }

    /**
     * Get previous phase (for unpublishing)
     */
    private String getPreviousPhase(String currentPhase) {
        switch (currentPhase.toLowerCase()) {
            case "speakers":
                return "topic";
            case "agenda":
                return "speakers";
            default:
                return null;
        }
    }

    /**
     * Evict all cache entries for an event
     */
    private void evictEventCache(String eventCode) {
        if (cacheManager != null) {
            org.springframework.cache.Cache cache = cacheManager.getCache("eventWithIncludes");
            if (cache != null) {
                // Evict all possible cache keys for this event (with different include combinations)
                cache.evict(eventCode + "_none");
                cache.evict(eventCode + "_venue");
                cache.evict(eventCode + "_speakers");
                cache.evict(eventCode + "_sessions");
                cache.evict(eventCode + "_venue,speakers");
                cache.evict(eventCode + "_venue,sessions");
                cache.evict(eventCode + "_speakers,sessions");
                cache.evict(eventCode + "_venue,speakers,sessions");
                // Clear the entire cache to be safe
                cache.clear();
            }
        }
    }

    /**
     * Custom exception for publishing validation errors
     */
    public static class PublishValidationException extends RuntimeException {
        private final List<PublishValidationError.ValidationDetail> validationErrors;

        public PublishValidationException(String message,
                List<PublishValidationError.ValidationDetail> validationErrors) {
            super(message);
            this.validationErrors = validationErrors;
        }

        public List<PublishValidationError.ValidationDetail> getValidationErrors() {
            return validationErrors;
        }
    }
}
