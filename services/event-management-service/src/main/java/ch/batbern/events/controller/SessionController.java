package ch.batbern.events.controller;

import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.CreateSessionRequest;
import ch.batbern.events.dto.UpdateSessionRequest;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.api.*;
import ch.batbern.shared.exception.ValidationException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for Event Session sub-resources
 * Story 1.15a.1: Events API Consolidation - AC9-10
 *
 * Endpoints:
 * - GET    /api/v1/events/{eventId}/sessions
 * - POST   /api/v1/events/{eventId}/sessions
 * - PUT    /api/v1/events/{eventId}/sessions/{sessionId}
 * - DELETE /api/v1/events/{eventId}/sessions/{sessionId}
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/sessions")
public class SessionController {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventRepository eventRepository;

    /**
     * AC9: List sessions for an event with optional filtering
     * GET /api/v1/events/{eventId}/sessions?filter={}&page={}&limit={}
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listSessions(
            @PathVariable String eventId,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int limit) {

        // Verify event exists
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }

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
     * AC10: Create a new session for an event
     * POST /api/v1/events/{eventId}/sessions
     */
    @PostMapping
    public ResponseEntity<Session> createSession(
            @PathVariable String eventId,
            @Valid @RequestBody CreateSessionRequest request) {

        // Verify event exists
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }

        // Create session
        Session session = Session.builder()
                .eventId(eventId)
                .title(request.getTitle())
                .description(request.getDescription())
                .startTime(parseInstant(request.getStartTime()))
                .duration(request.getDuration())
                .type(request.getType())
                .build();

        Session savedSession = sessionRepository.save(session);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedSession);
    }

    /**
     * AC10: Update an existing session (full replacement)
     * PUT /api/v1/events/{eventId}/sessions/{sessionId}
     */
    @PutMapping("/{sessionId}")
    public ResponseEntity<Session> updateSession(
            @PathVariable String eventId,
            @PathVariable String sessionId,
            @Valid @RequestBody UpdateSessionRequest request) {

        // Verify event exists
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }

        // Find existing session
        Optional<Session> existingSession = sessionRepository.findById(sessionId);
        if (existingSession.isEmpty()) {
            throw new ValidationException("Session not found: " + sessionId);
        }

        // Verify session belongs to the event
        if (!existingSession.get().getEventId().equals(eventId)) {
            throw new ValidationException("Session does not belong to this event");
        }

        // Update session
        Session session = existingSession.get();
        session.setTitle(request.getTitle());
        session.setDescription(request.getDescription());
        session.setStartTime(parseInstant(request.getStartTime()));
        session.setDuration(request.getDuration());
        session.setType(request.getType());

        Session updatedSession = sessionRepository.save(session);

        return ResponseEntity.ok(updatedSession);
    }

    /**
     * AC10: Delete a session
     * DELETE /api/v1/events/{eventId}/sessions/{sessionId}
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable String eventId,
            @PathVariable String sessionId) {

        // Verify event exists
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }

        // Find existing session
        Optional<Session> existingSession = sessionRepository.findById(sessionId);
        if (existingSession.isEmpty()) {
            throw new ValidationException("Session not found: " + sessionId);
        }

        // Verify session belongs to the event
        if (!existingSession.get().getEventId().equals(eventId)) {
            throw new ValidationException("Session does not belong to this event");
        }

        // Delete session
        sessionRepository.deleteById(sessionId);

        return ResponseEntity.noContent().build();
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
