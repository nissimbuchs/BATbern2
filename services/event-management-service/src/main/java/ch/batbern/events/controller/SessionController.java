package ch.batbern.events.controller;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.BatchImportSessionRequest;
import ch.batbern.events.dto.BatchImportSessionResult;
import ch.batbern.events.dto.CreateSessionRequest;
import ch.batbern.events.dto.SessionResponse;
import ch.batbern.events.dto.UpdateSessionRequest;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.ContentSubmissionRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.service.SessionBatchImportService;
import ch.batbern.events.service.SessionService;
import ch.batbern.shared.api.FilterCriteria;
import ch.batbern.shared.api.FilterOperator;
import ch.batbern.shared.api.FilterParser;
import ch.batbern.shared.api.PaginationMetadata;
import ch.batbern.shared.api.PaginationParams;
import ch.batbern.shared.api.PaginationUtils;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.service.SlugGenerationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Event Session sub-resources
 * Story 1.15a.1: Events API Consolidation - AC9-10
 * Story 1.16.2: Updated to use eventCode and sessionSlug (meaningful IDs)
 *
 * Endpoints:
 * - GET    /api/v1/events/{eventCode}/sessions
 * - POST   /api/v1/events/{eventCode}/sessions
 * - PUT    /api/v1/events/{eventCode}/sessions/{sessionSlug}
 * - DELETE /api/v1/events/{eventCode}/sessions/{sessionSlug}
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/sessions")
public class SessionController {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ContentSubmissionRepository contentSubmissionRepository;

    @Autowired
    private SlugGenerationService slugGenerationService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SessionBatchImportService sessionBatchImportService;

    /**
     * AC9: List sessions for an event with optional filtering
     * Story 1.16.2: Updated to use eventCode instead of UUID
     * GET /api/v1/events/{eventCode}/sessions?filter={}&page={}&limit={}
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listSessions(
            @PathVariable String eventCode,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int limit) {

        // Find event by eventCode
        UUID eventId = eventRepository.findByEventCode(eventCode)
                .map(event -> event.getId())
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        try {
            // Parse pagination parameters
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

            // Set eventCode on all sessions for API response
            sessionsPage.getContent().forEach(session -> session.setEventCode(eventCode));

            // Generate pagination metadata
            PaginationMetadata metadata = PaginationUtils.generateMetadata(
                    pageNum,
                    pageSize,
                    sessionsPage.getTotalElements()
            );

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("data", sessionsPage.getContent());
            response.put("pagination", metadata);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            throw new ValidationException("Invalid filter or pagination parameters: " + e.getMessage());
        }
    }

    /**
     * Get a single session by sessionSlug
     * Story 1.15a.1b: Returns SessionResponse with speakers array
     * GET /api/v1/events/{eventCode}/sessions/{sessionSlug}
     *
     * @param eventCode Event code (for API consistency, not used in lookup)
     * @param sessionSlug Session slug (globally unique identifier)
     * @param expand Optional expand parameter (e.g., "speakers")
     * @return SessionResponse with optional speaker details
     */
    @GetMapping("/{sessionSlug}")
    public ResponseEntity<SessionResponse> getSession(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug,
            @RequestParam(required = false) String expand) {

        // Find session by slug (globally unique, no need for eventCode in query)
        Session session = sessionRepository.findBySessionSlug(sessionSlug)
                .orElseThrow(() -> new EventNotFoundException("Session not found: " + sessionSlug));

        // Convert to SessionResponse with speakers (Story 1.15a.1b)
        SessionResponse response = sessionService.toSessionResponse(session, eventCode);

        return ResponseEntity.ok(response);
    }

    /**
     * AC10: Create a new session for an event
     * Story 1.16.2: Auto-generates sessionSlug from title
     * Story 1.15a.1b: Returns SessionResponse with speakers array
     * POST /api/v1/events/{eventCode}/sessions
     */
    @PostMapping
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<SessionResponse> createSession(
            @PathVariable String eventCode,
            @Valid @RequestBody CreateSessionRequest request) {

        // Find event by eventCode
        UUID eventId = eventRepository.findByEventCode(eventCode)
                .map(event -> event.getId())
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Generate unique session slug from title
        String baseSlug = slugGenerationService.generateSessionSlug(request.getTitle());
        String sessionSlug = slugGenerationService.ensureUniqueSlug(
                baseSlug,
                sessionRepository::existsBySessionSlug
        );

        // Create session
        Session session = Session.builder()
                .sessionSlug(sessionSlug)
                .eventId(eventId)
                .eventCode(eventCode)
                .title(request.getTitle())
                .description(request.getDescription())
                .sessionType(request.getSessionType())
                .startTime(parseInstant(request.getStartTime()))
                .endTime(parseInstant(request.getEndTime()))
                .room(request.getRoom())
                .capacity(request.getCapacity())
                .language(request.getLanguage())
                .build();

        Session savedSession = sessionRepository.save(session);

        // Convert to SessionResponse with speakers (Story 1.15a.1b)
        SessionResponse response = sessionService.toSessionResponse(savedSession, eventCode);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * AC10: Update an existing session (full replacement)
     * Story 1.16.2: Uses sessionSlug as path parameter
     * PUT /api/v1/events/{eventCode}/sessions/{sessionSlug}
     */
    @PutMapping("/{sessionSlug}")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<SessionResponse> updateSession(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug,
            @Valid @RequestBody UpdateSessionRequest request) {

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

        // Update session
        session.setTitle(request.getTitle());
        session.setDescription(request.getDescription());
        session.setSessionType(request.getSessionType());
        session.setStartTime(parseInstant(request.getStartTime()));
        session.setEndTime(parseInstant(request.getEndTime()));
        session.setRoom(request.getRoom());
        session.setCapacity(request.getCapacity());
        session.setLanguage(request.getLanguage());

        Session updatedSession = sessionRepository.save(session);

        // Convert to SessionResponse with speakers (Story 1.15a.1b)
        SessionResponse response = sessionService.toSessionResponse(updatedSession, eventCode);

        return ResponseEntity.ok(response);
    }

    /**
     * Partially update a session (PATCH)
     * PATCH /api/v1/events/{eventCode}/sessions/{sessionSlug}
     *
     * Allows updating individual fields without requiring all fields.
     * Supported fields: title, description, durationMinutes
     *
     * @param eventCode Event code
     * @param sessionSlug Session slug identifier
     * @param updates Map of field updates (only provided fields will be updated)
     * @return Updated session response
     */
    @PatchMapping("/{sessionSlug}")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<SessionResponse> patchSession(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug,
            @RequestBody Map<String, Object> updates) {

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

        // Apply partial updates
        if (updates.containsKey("title")) {
            session.setTitle((String) updates.get("title"));
        }

        if (updates.containsKey("description")) {
            session.setDescription((String) updates.get("description"));
        }

        if (updates.containsKey("durationMinutes")) {
            Integer durationMinutes = null;
            Object durationValue = updates.get("durationMinutes");
            if (durationValue instanceof Integer) {
                durationMinutes = (Integer) durationValue;
            } else if (durationValue instanceof Number) {
                durationMinutes = ((Number) durationValue).intValue();
            }

            if (durationMinutes != null && durationMinutes > 0) {
                // Update endTime based on startTime + duration
                Instant startTime = session.getStartTime();
                if (startTime != null) {
                    Instant newEndTime = startTime.plusSeconds(durationMinutes * 60L);
                    session.setEndTime(newEndTime);
                }
            }
        }

        Session updatedSession = sessionRepository.save(session);

        // Convert to SessionResponse with speakers
        SessionResponse response = sessionService.toSessionResponse(updatedSession, eventCode);

        return ResponseEntity.ok(response);
    }

    /**
     * AC10: Delete a session
     * Story 1.16.2: Uses sessionSlug as path parameter
     * DELETE /api/v1/events/{eventCode}/sessions/{sessionSlug}
     *
     * Note: @Transactional is required at controller level to ensure atomic deletion
     * of session + related content submissions. The schema lacks ON DELETE CASCADE
     * (V53), requiring application-level cascade deletion. Transaction ensures both
     * deletes succeed or both roll back, preventing orphaned records.
     */
    @DeleteMapping("/{sessionSlug}")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> deleteSession(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug) {

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
        contentSubmissionRepository.deleteBySessionId(session.getId());

        // Delete session
        sessionRepository.deleteById(session.getId());

        return ResponseEntity.noContent().build();
    }

    /**
     * Batch import sessions from legacy JSON (sessions.json)
     * POST /api/v1/events/{eventCode}/sessions/batch-import
     *
     * Imports multiple sessions from historical data with:
     * - Duplicate detection by (event_id, title)
     * - Sequential 45-minute time slots
     * - Speaker assignment by matching speakerId to username
     * - Event organizer as moderator when no speakers
     *
     * @param eventCode Event code (e.g., "BATbern142")
     * @param requests List of session import requests from legacy JSON
     * @return BatchImportSessionResult with statistics and details
     */
    @PostMapping("/batch-import")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<BatchImportSessionResult> batchImportSessions(
            @PathVariable String eventCode,
            @Valid @RequestBody List<BatchImportSessionRequest> requests) {

        BatchImportSessionResult result = sessionBatchImportService.importSessions(eventCode, requests);

        return ResponseEntity.ok(result);
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
     * Parse ISO-8601 datetime string to Instant
     */
    private Instant parseInstant(String dateTimeStr) {
        try {
            return Instant.parse(dateTimeStr);
        } catch (Exception e) {
            throw new ValidationException("Invalid datetime format. Use ISO-8601: " + dateTimeStr);
        }
    }
}
