package ch.batbern.events.service.publishing;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.PublishingConfig;
import ch.batbern.events.domain.Session;
import ch.batbern.events.core.dto.generated.PatchPublishingRequest;
import ch.batbern.events.core.dto.generated.PublishPhaseResponse;
import ch.batbern.events.core.dto.generated.PublishingStatusResponse;
import ch.batbern.events.core.dto.generated.SessionValidationStatus;
import ch.batbern.events.core.dto.generated.UnassignedSession;
import ch.batbern.events.core.dto.generated.UnpublishPhaseResponse;
import ch.batbern.events.core.dto.generated.ValidationStatus;
import ch.batbern.events.dto.PublishValidationError;
import ch.batbern.events.event.EventPublishedEvent;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.PublishingConfigRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.service.AgendaConfigResolver;
import ch.batbern.events.service.CdnInvalidationService;
import ch.batbern.events.service.EventWorkflowStateMachine;
import ch.batbern.events.service.StructuralSessionService;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Integrated Publishing Service
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 *
 * Handles (Story 4.9b: one transition idiom, see ADR-013 section 1):
 * - Phase publishing (topic, speakers, agenda) with validation; the agenda publish moves the
 *   workflow into AGENDA_PUBLISHED
 * - Unpublishing phases
 * - CDN cache invalidation
 * - The publishing resource: status and auto-publish schedule
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PublishingService {

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final PublishingConfigRepository publishingConfigRepository;
    private final AgendaConfigResolver agendaConfigResolver;
    private final CacheManager cacheManager;
    private final EventWorkflowStateMachine eventWorkflowStateMachine;
    private final SecurityContextHelper securityContextHelper;
    private final CdnInvalidationService cdnInvalidationService;
    private final org.springframework.context.ApplicationEventPublisher applicationEventPublisher;
    private final StructuralSessionService structuralSessionService;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * Publish a phase on the public website (Story 4.9b: the one way to publish, folds the former
     * {@code POST /events/{eventCode}/publish}).
     *
     * <p>The agenda is validated first ({@link #validateAgenda}), and nothing changes when it is not
     * ready. Publishing the agenda of an event before AGENDA_PUBLISHED moves it there through the
     * state machine, so {@code EventWorkflowTransitionEvent} fires and the agenda_published tasks
     * activate (setting the state directly left BATbern60's tasks pending on 2026-10-23, organizer
     * cockpit plan T1), sets {@code publishedAt} and fires {@link EventPublishedEvent}. Override =
     * true: this path never ran the transition rules ({@link #validateAgenda} is its gate), and the
     * auto-publish job must not start failing on rules it bypassed before. It never moves an event
     * backwards: re-publishing the agenda of a live or completed event leaves its state alone.
     */
    public PublishPhaseResponse publishPhase(String eventCode, PublishingPhase phase) {
        log.info("Publishing phase {} for event {}", phase.value(), eventCode);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        boolean movesWorkflow = phase == PublishingPhase.AGENDA
                && isBefore(event.getWorkflowState(), EventWorkflowState.AGENDA_PUBLISHED);
        if (phase == PublishingPhase.AGENDA) {
            validateAgenda(event, movesWorkflow);
        }

        Instant now = Instant.now();
        event.setCurrentPublishedPhase(phase.value());
        event.setLastPublishedAt(now);
        eventRepository.save(event);

        if (movesWorkflow) {
            EventWorkflowState previousState = event.getWorkflowState();
            String actor = securityContextHelper.getCurrentUsernameOrNull();
            String username = actor != null ? actor : "system";
            event = eventWorkflowStateMachine.transitionToState(
                    eventCode,
                    EventWorkflowState.AGENDA_PUBLISHED,
                    username,
                    true,
                    "Agenda phase published");
            event.setPublishedAt(now);
            event = eventRepository.save(event);
            domainEventPublisher.publish(new EventPublishedEvent(
                    event.getId(),
                    event.getEventCode(),
                    event.getTitle(),
                    event.getEventNumber(),
                    event.getDate(),
                    event.getPublishedAt(),
                    previousState != null ? previousState.name() : null,
                    username));
        }

        // #1108: the public agenda shows stored sessions only. Generate moderation, breaks and
        // apéro if nobody pressed "Generate structure"; never overwrites an edited structure.
        if (phase == PublishingPhase.AGENDA) {
            structuralSessionService.generateIfMissing(eventCode);
        }

        evictEventCache(event.getEventCode());
        boolean cdnInvalidated = invalidateCdn(event.getEventCode(), phase);

        // Story 7.5 rework: when the speakers phase publishes, signal the Q&A listener — it opens
        // the windows iff this event's qnaOpenTrigger == SPEAKERS_PUBLISHED (idempotent; no-op
        // otherwise). Decoupled via an application event, mirroring the EVENT_COMPLETED path.
        if (phase == PublishingPhase.SPEAKERS) {
            applicationEventPublisher.publishEvent(
                    new ch.batbern.events.event.SpeakersPhasePublishedEvent(event.getEventCode()));
        }

        return PublishPhaseResponse.builder()
                .phase(phase.value())
                .published(true)
                .publishedAt(now.atOffset(ZoneOffset.UTC))
                .cdnInvalidated(cdnInvalidated)
                .build();
    }

    /**
     * Unpublish a phase: the published phase goes back to the one before it. The workflow state is
     * not changed.
     */
    public UnpublishPhaseResponse unpublishPhase(String eventCode, PublishingPhase phase) {
        log.info("Unpublishing phase {} for event {}", phase.value(), eventCode);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        PublishingPhase previous = phase.previous();
        event.setCurrentPublishedPhase(previous != null ? previous.value() : null);
        eventRepository.save(event);

        evictEventCache(event.getEventCode());
        boolean cdnInvalidated = invalidateCdn(event.getEventCode(), phase);

        return UnpublishPhaseResponse.builder()
                .phase(phase.value())
                .published(false)
                .unpublishedAt(Instant.now().atOffset(ZoneOffset.UTC))
                .cdnInvalidated(cdnInvalidated)
                .build();
    }

    /**
     * The auto-publish schedule of an event: the stored row, or the defaults when there is none.
     * The scheduler reads it through here too, so the page and the job never disagree.
     */
    @Transactional(readOnly = true)
    public PublishingConfig scheduleOf(Event event) {
        return publishingConfigRepository.findByEventId(event.getId())
                .orElseGet(() -> PublishingConfig.builder().eventId(event.getId()).build());
    }

    /**
     * PATCH of the publishing resource: stores the present schedule fields, leaves the others.
     */
    public PublishingStatusResponse patchPublishing(String eventCode, PatchPublishingRequest request) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        PublishingConfig config = scheduleOf(event);
        if (request.getPhase2Enabled() != null) {
            config.setAutoPublishSpeakers(request.getPhase2Enabled());
        }
        if (request.getPhase2DaysBeforeEvent() != null) {
            config.setAutoPublishSpeakersDaysBefore(request.getPhase2DaysBeforeEvent());
        }
        if (request.getPhase3Enabled() != null) {
            config.setAutoPublishAgenda(request.getPhase3Enabled());
        }
        if (request.getPhase3DaysBeforeEvent() != null) {
            config.setAutoPublishAgendaDaysBefore(request.getPhase3DaysBeforeEvent());
        }
        publishingConfigRepository.save(config);

        return getPublishing(eventCode);
    }

    /**
     * The publishing resource: published phases, per-phase validation and the auto-publish
     * schedule (GET /events/{eventCode}/publishing).
     */
    @Transactional(readOnly = true)
    public PublishingStatusResponse getPublishing(String eventCode) {
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
        // Minimum from the effective agenda config (per-event override, else event-type template)
        long sessionCount = sessionRepository.findByEventId(event.getId()).size();
        int minSlotsRequired = agendaConfigResolver.resolve(event).getMinSlots();
        boolean speakersValid = sessionCount >= minSlotsRequired;

        List<String> speakersErrors = new ArrayList<>();
        if (!speakersValid) {
            speakersErrors.add(String.format(
                    "At least %d sessions required for %s event type (currently: %d)",
                    minSlotsRequired, event.getEventType().getValue(), sessionCount));
        }

        // Sessions validation - all speaker sessions must have timing (excludes structural: moderation/break/lunch)
        List<Session> allSessions = sessionRepository.findByEventId(event.getId()).stream()
                .filter(s -> !Session.isStructuralType(s.getSessionType()))
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
        List<UnassignedSession> unassignedList = unassignedSessions.stream()
                .map(s -> UnassignedSession.builder()
                        .sessionSlug(s.getSessionSlug())
                        .title(s.getTitle())
                        .build())
                .collect(Collectors.toList());

        // Determine published phases from currentPublishedPhase
        List<String> publishedPhases = determinePublishedPhases(event.getCurrentPublishedPhase());
        PublishingConfig schedule = scheduleOf(event);

        return PublishingStatusResponse.builder()
                .currentPhase(event.getCurrentPublishedPhase())
                .publishedPhases(publishedPhases)
                .topic(ValidationStatus.builder()
                        .isValid(topicValid)
                        .errors(topicErrors)
                        .build())
                .speakers(ValidationStatus.builder()
                        .isValid(speakersValid)
                        .errors(speakersErrors)
                        .build())
                .sessions(SessionValidationStatus.builder()
                        .isValid(sessionsValid)
                        .errors(sessionsErrors)
                        .assignedCount(allSessions.size() - unassignedSessions.size())
                        .totalCount(allSessions.size())
                        .unassignedSessions(unassignedList)
                        .build())
                .phase2Enabled(schedule.getAutoPublishSpeakers())
                .phase2DaysBeforeEvent(schedule.getAutoPublishSpeakersDaysBefore())
                .phase2TriggerDate(triggerDate(event, schedule.getAutoPublishSpeakersDaysBefore()))
                .phase3Enabled(schedule.getAutoPublishAgenda())
                .phase3DaysBeforeEvent(schedule.getAutoPublishAgendaDaysBefore())
                .phase3TriggerDate(triggerDate(event, schedule.getAutoPublishAgendaDaysBefore()))
                .build();
    }

    private static OffsetDateTime triggerDate(Event event, int daysBefore) {
        return event.getDate().minus(daysBefore, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC);
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
     * The agenda is ready when the event has a title and a date, every session is timed, and, when
     * the publish moves the workflow into AGENDA_PUBLISHED, the date is in the future (the rules of
     * the former {@code POST /events/{eventCode}/publish}, Story 4.9b). Re-publishing the agenda of
     * an event already published, live or archived does not need a future date: organizers correct
     * the agenda on the event day. Every failed rule is reported, not only the first.
     */
    private void validateAgenda(Event event, boolean movesWorkflow) {
        List<PublishValidationError.ValidationDetail> details = new ArrayList<>();
        if (event.getTitle() == null || event.getTitle().isBlank()) {
            details.add(detail(null, "title", "Event title is required for publishing"));
        }
        if (event.getDate() == null) {
            details.add(detail(null, "date", "Event date is required for publishing"));
        } else if (movesWorkflow && event.getWorkflowState() != EventWorkflowState.ARCHIVED
                && event.getDate().isBefore(Instant.now())) {
            details.add(detail(null, "date", "Event date must be in the future"));
        }
        sessionRepository.findByEventId(event.getId()).stream()
                .filter(s -> s.getStartTime() == null || s.getEndTime() == null)
                .forEach(s -> details.add(detail(s.getSessionSlug(), "timing", "Session has no timing assigned")));

        if (details.isEmpty()) {
            return;
        }
        List<String> reasons = new ArrayList<>();
        details.stream()
                .map(d -> "timing".equals(d.getField()) ? "All sessions must have timing assigned" : d.getMessage())
                .distinct()
                .forEach(reasons::add);
        throw new PublishValidationException(String.join("; ", reasons), details);
    }

    private static PublishValidationError.ValidationDetail detail(String sessionSlug, String field, String message) {
        return PublishValidationError.ValidationDetail.builder()
                .sessionSlug(sessionSlug)
                .field(field)
                .message(message)
                .build();
    }

    private boolean invalidateCdn(String eventCode, PublishingPhase phase) {
        String invalidationId = cdnInvalidationService.invalidateCache(eventCode, phase.value());
        return invalidationId != null && !invalidationId.startsWith("error");
    }

    private static boolean isBefore(EventWorkflowState state, EventWorkflowState reference) {
        return state == null || state.ordinal() < reference.ordinal();
    }

    /**
     * Evict what a publish changes: the event views (every include combination) and the archive
     * list, which shows the workflow state an agenda publish may move.
     */
    private void evictEventCache(String eventCode) {
        if (cacheManager == null) {
            return;
        }
        for (String name : List.of(CacheConfig.EVENT_WITH_INCLUDES_CACHE, CacheConfig.ARCHIVE_EVENTS_CACHE)) {
            org.springframework.cache.Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
        log.debug("Event caches cleared after publishing change of {}", eventCode);
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
