package ch.batbern.events.service.publishing;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.PublishingConfig;
import ch.batbern.events.domain.Session;
import ch.batbern.events.entity.EventTypeConfiguration;
import ch.batbern.events.dto.AutoPublishScheduleRequest;
import ch.batbern.events.dto.AutoPublishScheduleResponse;
import ch.batbern.events.dto.PublishPhaseResponse;
import ch.batbern.events.dto.PublishPreviewResponse;
import ch.batbern.events.dto.PublishValidationError;
import ch.batbern.events.dto.PublishingStatusResponse;
import ch.batbern.events.dto.UnpublishPhaseResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTypeRepository;
import ch.batbern.events.repository.PublishingConfigRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.CdnInvalidationService;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Integrated Publishing Service
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 *
 * Handles:
 * - Phase publishing (topic, speakers, agenda) with validation
 * - Unpublishing phases
 * - CDN cache invalidation
 * - Auto-publish scheduling
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
    private final PublishingConfigRepository publishingConfigRepository;
    private final EventTypeRepository eventTypeRepository;
    private final CacheManager cacheManager;
    private final CdnInvalidationService cdnInvalidationService;

    /**
     * Publish a specific phase (topic, speakers, or agenda)
     */
    public PublishPhaseResponse publishPhase(String eventCode, String phase) {
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

        // Invalidate CDN cache (Story BAT-16, AC6)
        String cdnInvalidationId = cdnInvalidationService.invalidateCache(event.getEventCode(), phase);
        boolean cdnInvalidated = cdnInvalidationId != null && !cdnInvalidationId.startsWith("error");

        return PublishPhaseResponse.builder()
                .phase(phase)
                .published(true)
                .publishedAt(Instant.now())
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

        // Invalidate CDN cache (Story BAT-16, AC6)
        String cdnInvalidationId = cdnInvalidationService.invalidateCache(event.getEventCode(), phase);
        boolean cdnInvalidated = cdnInvalidationId != null && !cdnInvalidationId.startsWith("error");

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

        // Speakers validation - minimum number of sessions based on event type
        // Sessions are created when speakers submit material, so session count correlates with speaker submissions
        // Get event type configuration for minimum slot requirements
        EventTypeConfiguration typeConfig = eventTypeRepository.findByType(event.getEventType())
                .orElseThrow(() -> new RuntimeException(
                        "Event type configuration not found for type: " + event.getEventType()));

        long sessionCount = sessionRepository.findByEventId(event.getId()).size();
        int minSlotsRequired = typeConfig.getMinSlots();
        boolean speakersValid = sessionCount >= minSlotsRequired;

        List<String> speakersErrors = new ArrayList<>();
        if (!speakersValid) {
            speakersErrors.add(String.format(
                    "At least %d sessions required for %s event type (currently: %d)",
                    minSlotsRequired, event.getEventType().getValue(), sessionCount));
        }

        // Sessions validation - all speaker sessions must have timing (excludes structural: moderation/break/lunch)
        Set<String> structuralSessionTypes = Set.of("moderation", "break", "lunch");
        List<Session> allSessions = sessionRepository.findByEventId(event.getId()).stream()
                .filter(s -> !structuralSessionTypes.contains(s.getSessionType()))
                .collect(Collectors.toList());
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

    // NOTE: finalizeAgenda() and unfinalizeAgenda() removed in V82.
    // AGENDA_FINALIZED state was removed from the workflow. The scheduler now transitions
    // AGENDA_PUBLISHED → EVENT_LIVE automatically on event day (daily 00:01 Bern time).

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
