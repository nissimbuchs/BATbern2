package ch.batbern.events.controller;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Session;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.mapper.SessionMapper;
import ch.batbern.events.repository.SessionContentHistoryRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.service.SessionBatchImportService;
import ch.batbern.events.service.SessionService;
import ch.batbern.events.service.StructuralSessionService;
import ch.batbern.events.sessions.api.generated.SessionsApi;
import ch.batbern.events.sessions.dto.generated.BatchImportSessionRequest;
import ch.batbern.events.sessions.dto.generated.BatchImportSessionResult;
import ch.batbern.events.sessions.dto.generated.CreateSessionRequest;
import ch.batbern.events.sessions.dto.generated.ListSessions200Response;
import ch.batbern.events.sessions.dto.generated.PatchSessionRequest;
import ch.batbern.events.sessions.dto.generated.SessionResponse;
import ch.batbern.shared.api.FilterCriteria;
import ch.batbern.shared.api.FilterOperator;
import ch.batbern.shared.api.FilterParser;
import ch.batbern.shared.api.PaginationMetadata;
import ch.batbern.shared.api.PaginationParams;
import ch.batbern.shared.api.PaginationUtils;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.service.SlugGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Event Session sub-resources.
 * Implements the generated {@link SessionsApi} contract (event-sessions-api.openapi.yml),
 * so the HTTP verb/path/validation annotations are inherited from the interface and the
 * OpenAPI spec is the enforced contract (Phase 7 — ADR-006).
 *
 * Story 1.15a.1: Events API Consolidation - AC9-10
 * Story 1.16.2: Updated to use eventCode and sessionSlug (meaningful IDs)
 */
@RestController
@RequestMapping("/api/v1")
public class SessionController implements SessionsApi {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionContentHistoryRepository sessionContentHistoryRepository;

    @Autowired
    private SlugGenerationService slugGenerationService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SessionMapper sessionMapper;

    @Autowired
    private SessionBatchImportService sessionBatchImportService;

    @Autowired
    private StructuralSessionService structuralSessionService;

    /**
     * AC9: List sessions for an event with optional filtering.
     * GET /api/v1/events/{eventCode}/sessions?filter={}&page={}&limit={}
     */
    @Override
    public ResponseEntity<ListSessions200Response> listSessions(
            String eventCode,
            String filter,
            Integer page,
            Integer limit) {

        // Find event by eventCode
        UUID eventId = eventRepository.findByEventCode(eventCode)
                .map(event -> event.getId())
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        try {
            // Parse pagination parameters (1-indexed page)
            PaginationParams paginationParams = PaginationUtils.parseParams(page, limit);
            int pageNum = paginationParams.getPage();
            int pageSize = paginationParams.getLimit();

            // Build query specification
            Specification<Session> spec = (root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("eventId"), eventId);

            // Apply filters if provided
            if (filter != null && !filter.isEmpty()) {
                FilterCriteria filterCriteria = FilterParser.parse(filter);
                Specification<Session> filterSpec = buildSessionSpecification(filterCriteria);
                spec = spec.and(filterSpec);
            }

            // Apply pagination
            Pageable pageable = PageRequest.of(pageNum - 1, pageSize); // Convert to 0-indexed
            Page<Session> sessionsPage = sessionRepository.findAll(spec, pageable);

            // Map entities to the generated SessionResponse via the pure mapper (no speaker
            // enrichment — list payloads stay lean, matching prior raw-entity behaviour).
            List<SessionResponse> data = sessionsPage.getContent().stream()
                    .map(session -> {
                        session.setEventCode(eventCode);
                        return sessionMapper.toDto(session);
                    })
                    .toList();

            // Generate pagination metadata
            PaginationMetadata metadata = PaginationUtils.generateMetadata(
                    pageNum,
                    pageSize,
                    sessionsPage.getTotalElements()
            );

            ListSessions200Response response = new ListSessions200Response()
                    .data(data)
                    .pagination(metadata);

            return ResponseEntity.ok(response);

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Invalid filter or pagination parameters: " + e.getMessage());
        }
    }

    /**
     * Get a single session by sessionSlug.
     * GET /api/v1/events/{eventCode}/sessions/{sessionSlug}
     */
    @Override
    public ResponseEntity<SessionResponse> getSession(
            String eventCode,
            String sessionSlug,
            String expand) {

        // Find session by slug (globally unique, no need for eventCode in query)
        Session session = sessionRepository.findBySessionSlug(sessionSlug)
                .orElseThrow(() -> new EventNotFoundException("Session not found: " + sessionSlug));

        // Convert to SessionResponse with speakers (Story 1.15a.1b)
        SessionResponse response = sessionService.toSessionResponse(session, eventCode);

        return ResponseEntity.ok(response);
    }

    /**
     * AC10: Create a new session for an event.
     * POST /api/v1/events/{eventCode}/sessions
     */
    @Override
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<SessionResponse> createSession(
            String eventCode,
            CreateSessionRequest createSessionRequest) {

        // Find event by eventCode
        UUID eventId = eventRepository.findByEventCode(eventCode)
                .map(event -> event.getId())
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Generate unique session slug from title
        String baseSlug = slugGenerationService.generateSessionSlug(createSessionRequest.getTitle());
        String sessionSlug = slugGenerationService.ensureUniqueSlug(
                baseSlug,
                sessionRepository::existsBySessionSlug
        );

        // Create session
        Session session = Session.builder()
                .sessionSlug(sessionSlug)
                .eventId(eventId)
                .eventCode(eventCode)
                .title(createSessionRequest.getTitle())
                .description(createSessionRequest.getDescription())
                .sessionType(createSessionRequest.getSessionType())
                .startTime(toInstant(createSessionRequest.getStartTime()))
                .endTime(toInstant(createSessionRequest.getEndTime()))
                .room(createSessionRequest.getRoom())
                .capacity(createSessionRequest.getCapacity())
                .language(createSessionRequest.getLanguage())
                .build();

        Session savedSession = sessionRepository.save(session);

        // Convert to SessionResponse with speakers (Story 1.15a.1b)
        SessionResponse response = sessionService.toSessionResponse(savedSession, eventCode);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Partially update a session (PATCH). Only provided fields change.
     * Supported fields: title, description, durationMinutes.
     * PATCH /api/v1/events/{eventCode}/sessions/{sessionSlug}
     */
    @Override
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<SessionResponse> patchSession(
            String eventCode,
            String sessionSlug,
            PatchSessionRequest patchSessionRequest) {

        // Find event by eventCode
        UUID eventId = eventRepository.findByEventCode(eventCode)
                .map(event -> event.getId())
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Find existing session by sessionSlug
        Session session = sessionRepository.findBySessionSlug(sessionSlug)
                .orElseThrow(() -> new ValidationException("Session not found: " + sessionSlug));

        // Verify session belongs to the event
        if (!session.getEventId().equals(eventId)) {
            throw new ValidationException("Session does not belong to this event");
        }

        // Apply partial updates (null = field not provided)
        if (patchSessionRequest.getTitle() != null) {
            session.setTitle(patchSessionRequest.getTitle());
        }

        if (patchSessionRequest.getDescription() != null) {
            session.setDescription(patchSessionRequest.getDescription());
        }

        Integer durationMinutes = patchSessionRequest.getDurationMinutes();
        if (durationMinutes != null && durationMinutes > 0) {
            // Update endTime based on startTime + duration
            Instant startTime = session.getStartTime();
            if (startTime != null) {
                Instant newEndTime = startTime.plusSeconds(durationMinutes * 60L);
                if (!newEndTime.equals(session.getEndTime())) {
                    session.setEndTime(newEndTime);
                    // Clear actual execution data when scheduled end time changes (W4.x).
                    session.setActualStartTime(null);
                    session.setActualEndTime(null);
                    session.setOverrunMinutes(null);
                    session.setCompletedByUsername(null);
                }
            }
        }

        Session updatedSession = sessionRepository.save(session);

        // Convert to SessionResponse with speakers
        SessionResponse response = sessionService.toSessionResponse(updatedSession, eventCode);

        return ResponseEntity.ok(response);
    }

    /**
     * AC10: Delete a session.
     * DELETE /api/v1/events/{eventCode}/sessions/{sessionSlug}
     *
     * Note: @Transactional ensures atomic deletion of session + related content
     * submissions. The schema lacks ON DELETE CASCADE (V53), requiring application-level
     * cascade deletion.
     */
    @Override
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> deleteSession(
            String eventCode,
            String sessionSlug) {

        // Find event by eventCode
        UUID eventId = eventRepository.findByEventCode(eventCode)
                .map(event -> event.getId())
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Find existing session by sessionSlug
        Session session = sessionRepository.findBySessionSlug(sessionSlug)
                .orElseThrow(() -> new ValidationException("Session not found: " + sessionSlug));

        // Verify session belongs to the event
        if (!session.getEventId().equals(eventId)) {
            throw new ValidationException("Session does not belong to this event");
        }

        // Delete related content submissions first to avoid foreign key constraint violation
        sessionContentHistoryRepository.deleteBySessionId(session.getId());

        // Delete session
        sessionRepository.deleteById(session.getId());

        return ResponseEntity.noContent().build();
    }

    /**
     * Batch import sessions from legacy JSON (sessions.json).
     * POST /api/v1/events/{eventCode}/sessions/batch-import
     */
    @Override
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<BatchImportSessionResult> batchImportSessions(
            String eventCode,
            List<BatchImportSessionRequest> batchImportSessionRequest) {

        BatchImportSessionResult result =
                sessionBatchImportService.importSessions(eventCode, batchImportSessionRequest);

        return ResponseEntity.ok(result);
    }

    /**
     * Generate structural sessions (moderation, break, lunch) for an event.
     * ORGANIZER role required.
     * POST /api/v1/events/{eventCode}/sessions/structural?overwrite={bool}
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<List<SessionResponse>> generateStructuralSessions(
            String eventCode,
            Boolean overwrite) {

        List<SessionResponse> sessions = structuralSessionService.generateStructuralSessions(
                eventCode, Boolean.TRUE.equals(overwrite));

        return ResponseEntity.status(HttpStatus.CREATED).body(sessions);
    }

    /**
     * Build JPA Specification from FilterCriteria for sessions
     */
    private Specification<Session> buildSessionSpecification(FilterCriteria criteria) {
        return (root, query, criteriaBuilder) -> {
            FilterOperator operator = criteria.getOperator();

            switch (operator) {
                case EQUALS:
                    return criteriaBuilder.equal(root.get(criteria.getField()), criteria.getValue());
                case IN:
                    Object value = criteria.getValue();
                    if (value instanceof List) {
                        return root.get(criteria.getField()).in((List<?>) value);
                    }
                    return root.get(criteria.getField()).in(value);
                case AND:
                    List<FilterCriteria> children = criteria.getChildren();
                    if (children == null || children.isEmpty()) {
                        return criteriaBuilder.conjunction();
                    }
                    Specification<Session> andSpec = buildSessionSpecification(children.get(0));
                    for (int i = 1; i < children.size(); i++) {
                        Specification<Session> childSpec = buildSessionSpecification(children.get(i));
                        if (childSpec != null) {
                            andSpec = andSpec == null ? childSpec : andSpec.and(childSpec);
                        }
                    }
                    return andSpec.toPredicate(root, query, criteriaBuilder);
                default:
                    throw new ValidationException("Unsupported filter operator: " + operator);
            }
        };
    }

    /**
     * Convert an OffsetDateTime (wire) to an Instant (entity storage).
     */
    private Instant toInstant(OffsetDateTime dateTime) {
        return dateTime != null ? dateTime.toInstant() : null;
    }
}
