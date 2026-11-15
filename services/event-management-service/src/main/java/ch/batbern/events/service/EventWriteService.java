package ch.batbern.events.service;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.QnaOpenTrigger;
import ch.batbern.events.dto.CreateEventRequest;
import ch.batbern.events.dto.EventResponse;
import ch.batbern.events.dto.PatchEventRequest;
import ch.batbern.events.dto.UpdateEventRequest;
import ch.batbern.events.event.EventCreatedEvent;
import ch.batbern.events.event.EventUpdatedEvent;
import ch.batbern.events.exception.BusinessValidationException;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.mapper.EventGeneratedMapper;
import ch.batbern.events.notification.NotificationRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Event writes (event core): create, full and partial update, delete. Moved from
 * {@code EventController}, which keeps only HTTP (and the cache evictions, which run after the write
 * committed).
 *
 * <p>The modules above event core take part through {@link EventWriteHooks} (the waitlist fill when the
 * capacity is saved, the delete guard) and {@link EventThemeAttachment} (the theme image), which they
 * implement in their own module.
 *
 * <p>Transactions:
 * <ul>
 *   <li>create has none of its own: the {@code EventCreatedEvent} listener that auto-enrols the
 *       stakeholders runs REQUIRES_NEW and reads the event by code, so the event must be committed
 *       when it runs.</li>
 *   <li>PUT and PATCH save the event and fill the waitlist in one transaction (#1316: one write, one
 *       transaction). The theme image association and the domain event stay outside it: the
 *       association is transactional itself, and a failure inside a shared transaction would mark it
 *       rollback-only and turn today's logged-and-ignored failure into a 500.</li>
 *   <li>delete runs in one transaction: the guard, the event's notifications, the event.</li>
 * </ul>
 */
@Service
@Slf4j
public class EventWriteService {

    private final EventRepository eventRepository;
    private final NotificationRepository notificationRepository;
    private final EventReadService eventReadService;
    private final EventGeneratedMapper eventGeneratedMapper;
    private final EventThemeAttachment themeAttachment;
    private final List<EventWriteHooks> writeHooks;
    private final DomainEventPublisher eventPublisher;
    private final SecurityContextHelper securityContextHelper;
    private final CacheManager cacheManager;
    private final TransactionTemplate transactionTemplate;

    public EventWriteService(EventRepository eventRepository, NotificationRepository notificationRepository,
                             EventReadService eventReadService, EventGeneratedMapper eventGeneratedMapper,
                             EventThemeAttachment themeAttachment, List<EventWriteHooks> writeHooks,
                             DomainEventPublisher eventPublisher, SecurityContextHelper securityContextHelper,
                             CacheManager cacheManager, PlatformTransactionManager transactionManager) {
        this.eventRepository = eventRepository;
        this.notificationRepository = notificationRepository;
        this.eventReadService = eventReadService;
        this.eventGeneratedMapper = eventGeneratedMapper;
        this.themeAttachment = themeAttachment;
        this.writeHooks = List.copyOf(writeHooks);
        this.eventPublisher = eventPublisher;
        this.securityContextHelper = securityContextHelper;
        this.cacheManager = cacheManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * POST /events (Story 1.16.2: eventCode "BATbern{number}", the number generated when not given).
     */
    public ch.batbern.events.core.dto.generated.Event create(
            ch.batbern.events.core.dto.generated.CreateEventRequest gen) {
        CreateEventRequest request = toHandCreate(gen);
        log.debug("POST /api/v1/events - title: {}", request.getTitle());

        // Generate eventCode from event number (format: "BATbern{number}")
        Integer eventNumber = request.getEventNumber();
        if (eventNumber == null) {
            // Auto-generate event number if not provided
            eventNumber = generateNextEventNumber();
        }
        String eventCode = "BATbern" + eventNumber;

        // Validate eventCode is unique
        if (eventRepository.existsByEventCode(eventCode)) {
            throw new BusinessValidationException("eventCode",
                "Event code already exists: " + eventCode);
        }

        // Create new event entity
        Event event = Event.builder()
                .eventCode(eventCode)
                .title(request.getTitle())
                .eventNumber(eventNumber)
                .date(parseDate(request.getDate()))
                .registrationDeadline(request.getRegistrationDeadline() != null
                    ? parseDate(request.getRegistrationDeadline()) : null)
                .venueName(request.getVenueName())
                .venueAddress(request.getVenueAddress())
                .venueCapacity(request.getVenueCapacity())
                .organizerUsername(request.getOrganizerUsername())
                .currentAttendeeCount(request.getCurrentAttendeeCount() != null ? request.getCurrentAttendeeCount() : 0)
                .publishedAt(request.getPublishedAt() != null ? parseDate(request.getPublishedAt()) : null)
                .metadata(request.getMetadata())
                .description(request.getDescription())
                .eventType(request.getEventType())
                .themeImageUploadId(request.getThemeImageUploadId())
                .workflowState(request.getWorkflowState()) // Use workflowState from request directly
                .registrationCapacity(request.getRegistrationCapacity())
                .build();

        // A theme image upload that cannot be attached fails the create before anything is saved
        // (404 unknown or of another purpose, 409 not CONFIRMED; Story 17.11)
        themeAttachment.requireAttachable(request.getThemeImageUploadId(), null);

        Event savedEvent = eventRepository.save(event);

        // Associate theme image if provided (Story 2.5.3a)
        if (request.getThemeImageUploadId() != null && !request.getThemeImageUploadId().isBlank()) {
            associateThemeImage(savedEvent, request.getThemeImageUploadId());
        }

        // Publish domain event (Story 2.2: Architecture Compliance)
        try {
            String userId = securityContextHelper.getCurrentUsername();
            EventCreatedEvent domainEvent = new EventCreatedEvent(
                    savedEvent.getId(),           // Internal UUID
                    savedEvent.getEventCode(),    // Public business identifier
                    savedEvent.getTitle(),
                    savedEvent.getEventNumber(),
                    savedEvent.getDate(),
                    savedEvent.getRegistrationDeadline(),
                    savedEvent.getVenueName(),
                    savedEvent.getVenueAddress(),
                    savedEvent.getVenueCapacity(),
                    savedEvent.getWorkflowState() != null ? savedEvent.getWorkflowState().name() : null,
                    savedEvent.getOrganizerUsername(),
                    savedEvent.getDescription(),
                    userId
            );
            eventPublisher.publish(domainEvent);
            log.info("Published EventCreatedEvent for event: {}", savedEvent.getEventCode());
        } catch (Exception e) {
            log.warn("Failed to publish EventCreatedEvent for event {}: {}", savedEvent.getEventCode(), e.getMessage());
            // Continue - event creation succeeded, publishing failure is non-critical
        }

        return toWire(savedEvent);
    }

    /**
     * PUT /events/{eventCode}: replaces every field (Story 1.16.2); a changed capacity fills free places
     * from the waitlist in the save's transaction.
     */
    public ch.batbern.events.core.dto.generated.Event update(String eventCode,
            ch.batbern.events.core.dto.generated.UpdateEventRequest gen) {
        UpdateEventRequest request = toHandUpdate(gen);
        log.debug("PUT /api/v1/events/{} - title: {}", eventCode, request.getTitle());

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(
                    "Event not found with code: " + eventCode));

        // Track original themeImageUploadId before updates (Story 2.5.3a bug fix)
        String originalUploadId = event.getThemeImageUploadId();
        themeAttachment.requireAttachable(request.getThemeImageUploadId(), originalUploadId);

        // Replace all fields
        event.setTitle(request.getTitle());

        // Validate that the new event number is not already in use by another event
        // Only update eventNumber if provided in request (null-safe check)
        if (request.getEventNumber() != null && !request.getEventNumber().equals(event.getEventNumber())) {
            Optional<Event> existingEvent = eventRepository.findByEventNumber(request.getEventNumber());
            if (existingEvent.isPresent() && !existingEvent.get().getId().equals(event.getId())) {
                throw new BusinessValidationException("Event number",
                    "Event number " + request.getEventNumber() + " is already in use by event "
                    + existingEvent.get().getEventCode());
            }
            event.setEventNumber(request.getEventNumber());
            // Regenerate eventCode when eventNumber changes
            String newEventCode = "BATbern" + request.getEventNumber();
            event.setEventCode(newEventCode);
            log.info("Event number updated to {} via PUT, regenerated eventCode to: {}",
                request.getEventNumber(), newEventCode);
        }
        event.setDate(parseDate(request.getDate()));
        event.setRegistrationDeadline(request.getRegistrationDeadline() != null
            ? parseDate(request.getRegistrationDeadline()) : null);
        event.setVenueName(request.getVenueName());
        event.setVenueAddress(request.getVenueAddress());
        event.setVenueCapacity(request.getVenueCapacity());
        event.setOrganizerUsername(request.getOrganizerUsername());
        event.setCurrentAttendeeCount(request.getCurrentAttendeeCount());
        event.setPublishedAt(request.getPublishedAt() != null ? parseDate(request.getPublishedAt()) : null);
        event.setMetadata(request.getMetadata());
        event.setDescription(request.getDescription());
        if (request.getEventType() != null) {
            event.setEventType(ch.batbern.events.core.dto.generated.EventType.fromValue(request.getEventType()));
        }
        // The workflow state is not part of PUT/PATCH: POST /events/{eventCode}/transition (Story 4.9b)
        boolean capacityChanged = !Objects.equals(
                event.getRegistrationCapacity(), request.getRegistrationCapacity());
        event.setRegistrationCapacity(request.getRegistrationCapacity()); // null = unlimited

        // Set theme image upload ID before save (Story 2.5.3a)
        if (request.getThemeImageUploadId() != null && !request.getThemeImageUploadId().isBlank()) {
            event.setThemeImageUploadId(request.getThemeImageUploadId());
        }

        Event updatedEvent = saveWithHooks(event, capacityChanged);

        // Associate theme image if new uploadId provided (Story 2.5.3a)
        // Must be done after save to ensure event has persisted eventCode
        if (request.getThemeImageUploadId() != null && !request.getThemeImageUploadId().isBlank()) {
            // Only associate if uploadId has changed (avoid re-associating already associated images)
            boolean uploadIdChanged = !request.getThemeImageUploadId().equals(originalUploadId);
            if (uploadIdChanged) {
                log.info("Theme image uploadId changed from {} to {} for event {}, associating new image",
                        originalUploadId, request.getThemeImageUploadId(), eventCode);
                associateThemeImage(updatedEvent, request.getThemeImageUploadId());
                // Reload event to get updated themeImageUrl from database
                updatedEvent = eventRepository.findByEventCode(updatedEvent.getEventCode())
                        .orElseThrow(() -> new EventNotFoundException("Event not found after theme image association"));

                // Evict again after the association: the controller's @CacheEvict runs only after the
                // whole method, and a read in between must not keep the image-less entry (Story 2.5.3a)
                clearEventCache("theme image association", eventCode);
            } else {
                log.debug("Theme image uploadId unchanged ({}), skipping re-association for event {}",
                        request.getThemeImageUploadId(), eventCode);
            }
        }

        publishUpdated(updatedEvent, "");

        return toWire(updatedEvent);
    }

    /**
     * PATCH /events/{eventCode}: only the given fields (Story 1.16.2). A given capacity (raised, or
     * saved again unchanged) fills free places from the waitlist in the save's transaction; saving it
     * again is how an organizer fills places already free.
     */
    public ch.batbern.events.core.dto.generated.Event patch(String eventCode,
            ch.batbern.events.core.dto.generated.PatchEventRequest gen) {
        PatchEventRequest request = toHandPatch(gen);
        log.debug("PATCH /api/v1/events/{}", eventCode);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Track original themeImageUploadId before updates (Story 2.5.3a bug fix)
        String originalUploadId = event.getThemeImageUploadId();
        themeAttachment.requireAttachable(request.getThemeImageUploadId(), originalUploadId);

        // Update only provided fields
        applyPatchUpdates(event, request);

        Event patchedEvent = saveWithHooks(event, request.getRegistrationCapacity() != null);

        // Handle theme image changes (Story 2.5.3a)
        // Check if themeImageUploadId was provided in the request (including null to clear it)
        if (request.getThemeImageUploadId() != null) {
            if (!request.getThemeImageUploadId().isBlank()) {
                // Only associate if uploadId has changed (avoid re-associating already associated images)
                boolean uploadIdChanged = !request.getThemeImageUploadId().equals(originalUploadId);
                if (uploadIdChanged) {
                    log.info("Theme image uploadId changed from {} to {} for event {}, associating new image",
                            originalUploadId, request.getThemeImageUploadId(), eventCode);
                    associateThemeImage(patchedEvent, request.getThemeImageUploadId());
                    // Reload event to get updated themeImageUrl from database
                    patchedEvent = eventRepository.findByEventCode(patchedEvent.getEventCode())
                            .orElseThrow(() -> new EventNotFoundException(
                                    "Event not found after theme image association"));
                } else {
                    log.debug("Theme image uploadId unchanged ({}), skipping re-association for event {}",
                            request.getThemeImageUploadId(), eventCode);
                }
            } else {
                // Clear theme image (blank/empty string means remove)
                log.info("Clearing theme image for event: {}", eventCode);
                patchedEvent.setThemeImageUploadId(null);
                patchedEvent.setThemeImageUrl(null);
                patchedEvent = eventRepository.save(patchedEvent);
            }

            // Evict again after the theme image change (Story 2.5.3a), see update
            clearEventCache("theme image changes", eventCode);
        }

        publishUpdated(patchedEvent, " (patch)");

        return toWire(patchedEvent);
    }

    /**
     * DELETE /events/{eventCode}: refused while real attendees are registered (409, cancel the event
     * instead). The delete cascades programmatic registrations, sessions and the rest through the
     * FK ON DELETE CASCADE; notifications reference the event by code without a FK, so they go
     * explicitly (#1068).
     */
    @Transactional
    public void delete(String eventCode) {
        log.debug("DELETE /api/v1/events/{}", eventCode);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        writeHooks.forEach(hooks -> hooks.beforeDelete(event));

        notificationRepository.deleteByEventCode(eventCode);
        eventRepository.deleteById(event.getId());
    }

    /** One write, one transaction (#1316): the event save and what the modules do when the capacity was saved. */
    private Event saveWithHooks(Event event, boolean capacitySaved) {
        return transactionTemplate.execute(status -> {
            Event saved = eventRepository.save(event);
            if (capacitySaved) {
                writeHooks.forEach(hooks -> hooks.capacitySaved(saved));
            }
            return saved;
        });
    }

    private ch.batbern.events.core.dto.generated.Event toWire(Event event) {
        EventResponse response = eventReadService.toResponse(event);
        return eventGeneratedMapper.toEvent(response);
    }

    private void clearEventCache(String reason, String eventCode) {
        Cache cache = cacheManager.getCache(CacheConfig.EVENT_WITH_INCLUDES_CACHE);
        if (cache != null) {
            cache.clear();
            log.debug("Cache cleared after {} for event: {}", reason, eventCode);
        }
    }

    /** Publishes the EventUpdatedEvent (Story 2.2); a publishing failure is logged, the write stands. */
    private void publishUpdated(Event updatedEvent, String kind) {
        try {
            String userId = securityContextHelper.getCurrentUsername();
            EventUpdatedEvent domainEvent = new EventUpdatedEvent(
                    updatedEvent.getId(),           // Internal UUID
                    updatedEvent.getEventCode(),    // Public business identifier
                    updatedEvent.getTitle(),
                    updatedEvent.getEventNumber(),
                    updatedEvent.getDate(),
                    updatedEvent.getRegistrationDeadline(),
                    updatedEvent.getVenueName(),
                    updatedEvent.getVenueAddress(),
                    updatedEvent.getVenueCapacity(),
                    updatedEvent.getWorkflowState() != null ? updatedEvent.getWorkflowState().name() : null,
                    updatedEvent.getOrganizerUsername(),
                    updatedEvent.getDescription(),
                    updatedEvent.getCurrentAttendeeCount(),
                    userId
            );
            eventPublisher.publish(domainEvent);
            log.info("Published EventUpdatedEvent{} for event: {}", kind, updatedEvent.getEventCode());
        } catch (Exception e) {
            log.warn("Failed to publish EventUpdatedEvent{} for event {}: {}",
                kind, updatedEvent.getEventCode(), e.getMessage());
            // Continue - event update succeeded, publishing failure is non-critical
        }
    }

    /**
     * Attaches the theme image (Story 2.5.3a; through the upload contract since Story 17.11). The upload
     * was checked before the save; a storage failure here leaves the event without the image and is
     * logged, the write stands.
     */
    private void associateThemeImage(Event event, String uploadId) {
        try {
            String url = themeAttachment.attach(uploadId, event.getEventCode());
            event.setThemeImageUrl(url);
            eventRepository.save(event);
            log.info("Theme image associated for event {}: {}", event.getEventCode(), url);
        } catch (RuntimeException e) {
            log.error("FAILED to associate theme image for event {} with uploadId {}: {}",
                    event.getEventCode(), uploadId, e.getMessage(), e);
        }
    }

    /** The next event number: the highest existing one plus 1, or 1 when there is none. */
    private Integer generateNextEventNumber() {
        return eventRepository.findAll().stream()
                .map(Event::getEventNumber)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(max -> max + 1)
                .orElse(1); // Start with 1 if no events exist
    }

    private static Instant parseDate(String dateString) {
        return Instant.parse(dateString);
    }

    private void applyPatchUpdates(Event event, PatchEventRequest request) {
        if (request.getTitle() != null) {
            event.setTitle(request.getTitle());
        }
        if (request.getEventNumber() != null) {
            // Validate that the new event number is not already in use by another event
            if (!request.getEventNumber().equals(event.getEventNumber())) {
                Optional<Event> existingEvent = eventRepository.findByEventNumber(request.getEventNumber());
                if (existingEvent.isPresent() && !existingEvent.get().getId().equals(event.getId())) {
                    throw new BusinessValidationException("Event number",
                        "Event number " + request.getEventNumber() + " is already in use by event "
                        + existingEvent.get().getEventCode());
                }

                // Validate that the generated event code is not already in use by another event
                String newEventCode = "BATbern" + request.getEventNumber();
                Optional<Event> existingCodeEvent = eventRepository.findByEventCode(newEventCode);
                if (existingCodeEvent.isPresent() && !existingCodeEvent.get().getId().equals(event.getId())) {
                    throw new BusinessValidationException("Event code",
                        "Generated event code " + newEventCode + " is already in use by another event");
                }
            }
            event.setEventNumber(request.getEventNumber());
            // Regenerate eventCode when eventNumber changes
            String newEventCode = "BATbern" + request.getEventNumber();
            event.setEventCode(newEventCode);
            log.info("Event number updated to {}, regenerated eventCode to: {}",
                request.getEventNumber(), newEventCode);
        }
        if (request.getDate() != null) {
            event.setDate(parseDate(request.getDate()));
        }
        if (request.getRegistrationDeadline() != null) {
            event.setRegistrationDeadline(parseDate(request.getRegistrationDeadline()));
        }
        if (request.getVenueName() != null) {
            event.setVenueName(request.getVenueName());
        }
        if (request.getVenueAddress() != null) {
            event.setVenueAddress(request.getVenueAddress());
        }
        if (request.getVenueCapacity() != null) {
            event.setVenueCapacity(request.getVenueCapacity());
        }
        if (request.getOrganizerUsername() != null) {
            event.setOrganizerUsername(request.getOrganizerUsername());
        }
        if (request.getCurrentAttendeeCount() != null) {
            event.setCurrentAttendeeCount(request.getCurrentAttendeeCount());
        }
        if (request.getPublishedAt() != null) {
            event.setPublishedAt(parseDate(request.getPublishedAt()));
        }
        if (request.getMetadata() != null) {
            event.setMetadata(request.getMetadata());
        }
        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }
        if (request.getEventType() != null) {
            event.setEventType(ch.batbern.events.core.dto.generated.EventType.fromValue(request.getEventType()));
        }
        // Story 2.5.3a: Set theme image upload ID (association happens after save in PATCH handler)
        if (request.getThemeImageUploadId() != null && !request.getThemeImageUploadId().isBlank()) {
            event.setThemeImageUploadId(request.getThemeImageUploadId());
        }
        // The workflow state is not part of PUT/PATCH: POST /events/{eventCode}/transition (Story 4.9b)
        // Story 10.4: Set topicCode directly via PATCH (blob selector accept flow)
        if (request.getTopicCode() != null) {
            event.setTopicCode(request.getTopicCode());
        }
        // Story 10.4: Record selection note from blob selector session
        if (request.getTopicSelectionNote() != null) {
            event.setTopicSelectionNote(request.getTopicSelectionNote());
        }
        // Story 10.11: only set if explicitly provided (non-null); to clear use PUT with null
        if (request.getRegistrationCapacity() != null) {
            event.setRegistrationCapacity(request.getRegistrationCapacity());
        }
        // Story 7.5 rework: per-event Q&A settings.
        if (request.getQnaEnabled() != null) {
            event.setQnaEnabled(request.getQnaEnabled());
        }
        if (request.getQnaOpenTrigger() != null) {
            event.setQnaOpenTrigger(request.getQnaOpenTrigger());
        }
        if (request.getQnaWindowDays() != null) {
            event.setQnaWindowDays(request.getQnaWindowDays());
        }
    }

    // ---- generated → hand request adapters (Phase 7 EventController wiring) -------------------
    // The generated request DTOs use OffsetDateTime + the generated EventType/EventWorkflowState
    // enums; the proven mutation bodies use the hand DTOs (String dates, generated EventType,
    // shared EventWorkflowState). Adapt at the method boundary so the bodies stay byte-identical.

    private static String toIso(OffsetDateTime odt) {
        return odt == null ? null : odt.toInstant().toString();
    }

    private static EventWorkflowState toSharedWorkflowState(
            ch.batbern.events.core.dto.generated.EventWorkflowState ws) {
        return ws == null ? null : EventWorkflowState.valueOf(ws.name());
    }

    private static CreateEventRequest toHandCreate(ch.batbern.events.core.dto.generated.CreateEventRequest g) {
        return CreateEventRequest.builder()
                .title(g.getTitle())
                .eventNumber(g.getEventNumber())
                .date(toIso(g.getDate()))
                .registrationDeadline(toIso(g.getRegistrationDeadline()))
                .venueName(g.getVenueName())
                .venueAddress(g.getVenueAddress())
                .venueCapacity(g.getVenueCapacity())
                .organizerUsername(g.getOrganizerUsername())
                .currentAttendeeCount(g.getCurrentAttendeeCount())
                .publishedAt(toIso(g.getPublishedAt()))
                .metadata(g.getMetadata())
                .description(g.getDescription())
                .eventType(g.getEventType())
                .themeImageUploadId(g.getThemeImageUploadId())
                .workflowState(toSharedWorkflowState(g.getWorkflowState()))
                .registrationCapacity(g.getRegistrationCapacity())
                .build();
    }

    private static UpdateEventRequest toHandUpdate(ch.batbern.events.core.dto.generated.UpdateEventRequest g) {
        return UpdateEventRequest.builder()
                .title(g.getTitle())
                .eventNumber(g.getEventNumber())
                .date(toIso(g.getDate()))
                .registrationDeadline(toIso(g.getRegistrationDeadline()))
                .venueName(g.getVenueName())
                .venueAddress(g.getVenueAddress())
                .venueCapacity(g.getVenueCapacity())
                .organizerUsername(g.getOrganizerUsername())
                .currentAttendeeCount(g.getCurrentAttendeeCount())
                .publishedAt(toIso(g.getPublishedAt()))
                .metadata(g.getMetadata())
                .description(g.getDescription())
                .themeImageUploadId(g.getThemeImageUploadId())
                .eventType(g.getEventType() == null ? null : g.getEventType().getValue())
                .registrationCapacity(g.getRegistrationCapacity())
                .build();
    }

    private static PatchEventRequest toHandPatch(ch.batbern.events.core.dto.generated.PatchEventRequest g) {
        return PatchEventRequest.builder()
                .title(g.getTitle())
                .eventNumber(g.getEventNumber())
                .date(toIso(g.getDate()))
                .registrationDeadline(toIso(g.getRegistrationDeadline()))
                .venueName(g.getVenueName())
                .venueAddress(g.getVenueAddress())
                .venueCapacity(g.getVenueCapacity())
                .organizerUsername(g.getOrganizerUsername())
                .currentAttendeeCount(g.getCurrentAttendeeCount())
                .publishedAt(toIso(g.getPublishedAt()))
                .metadata(g.getMetadata())
                .description(g.getDescription())
                .themeImageUploadId(g.getThemeImageUploadId())
                .eventType(g.getEventType() == null ? null : g.getEventType().getValue())
                .topicCode(g.getTopicCode())
                .topicSelectionNote(g.getTopicSelectionNote())
                .registrationCapacity(g.getRegistrationCapacity())
                .qnaEnabled(g.getQnaEnabled())
                .qnaOpenTrigger(g.getQnaOpenTrigger() == null ? null
                        : QnaOpenTrigger.valueOf(g.getQnaOpenTrigger().name()))
                .qnaWindowDays(g.getQnaWindowDays())
                .build();
    }
}
