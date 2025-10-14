package ch.batbern.events.controller;

import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.CreateRegistrationRequest;
import ch.batbern.events.dto.PatchRegistrationRequest;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
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
import java.util.UUID;

/**
 * REST Controller for Event Registration sub-resources
 * Story 1.15a.1: Events API Consolidation - AC11-12
 *
 * Endpoints:
 * - GET    /api/v1/events/{eventId}/registrations
 * - POST   /api/v1/events/{eventId}/registrations
 * - PATCH  /api/v1/events/{eventId}/registrations/{registrationId}
 * - DELETE /api/v1/events/{eventId}/registrations/{registrationId}
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/registrations")
public class RegistrationController {

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private EventRepository eventRepository;

    /**
     * AC11: List registrations for an event with optional filtering
     * GET /api/v1/events/{eventId}/registrations?filter={}&page={}&limit={}
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listRegistrations(
            @PathVariable UUID eventId,
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
            Specification<Registration> spec = (root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("eventId"), eventId);

            // Apply filters if provided
            if (filter != null && !filter.isEmpty()) {
                FilterCriteria filterCriteria = FilterParser.parse(filter);
                Specification<Registration> filterSpec = buildRegistrationSpecification(filterCriteria);
                spec = spec.and(filterSpec);
            }

            // Apply pagination
            Pageable pageable = PageRequest.of(pageNum - 1, pageSize); // Convert to 0-indexed
            Page<Registration> registrationsPage = registrationRepository.findAll(spec, pageable);

            // Generate pagination metadata
            PaginationMetadata metadata = PaginationUtils.generateMetadata(
                    pageNum,
                    pageSize,
                    registrationsPage.getTotalElements()
            );

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("data", registrationsPage.getContent());
            response.put("pagination", metadata);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            throw new ValidationException("Invalid filter or pagination parameters: " + e.getMessage());
        }
    }

    /**
     * AC12: Create a new registration for an event
     * POST /api/v1/events/{eventId}/registrations
     */
    @PostMapping
    public ResponseEntity<Registration> createRegistration(
            @PathVariable UUID eventId,
            @Valid @RequestBody CreateRegistrationRequest request) {

        // Verify event exists
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }

        // Create registration
        Registration registration = Registration.builder()
                .eventId(eventId)
                .attendeeId(request.getAttendeeId())
                .attendeeName(request.getAttendeeName())
                .attendeeEmail(request.getAttendeeEmail())
                .status(request.getStatus())
                .registrationDate(parseInstant(request.getRegistrationDate()))
                .build();

        Registration savedRegistration = registrationRepository.save(registration);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedRegistration);
    }

    /**
     * AC12: Partially update a registration (PATCH)
     * PATCH /api/v1/events/{eventId}/registrations/{registrationId}
     */
    @PatchMapping("/{registrationId}")
    public ResponseEntity<Registration> patchRegistration(
            @PathVariable UUID eventId,
            @PathVariable UUID registrationId,
            @Valid @RequestBody PatchRegistrationRequest request) {

        // Verify event exists
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }

        // Find existing registration
        Optional<Registration> existingRegistration = registrationRepository.findById(registrationId);
        if (existingRegistration.isEmpty()) {
            throw new ValidationException("Registration not found: " + registrationId);
        }

        // Verify registration belongs to the event
        Registration registration = existingRegistration.get();
        if (!registration.getEventId().equals(eventId)) {
            throw new ValidationException("Registration does not belong to this event");
        }

        // Apply partial updates
        if (request.getAttendeeName() != null) {
            registration.setAttendeeName(request.getAttendeeName());
        }
        if (request.getAttendeeEmail() != null) {
            registration.setAttendeeEmail(request.getAttendeeEmail());
        }
        if (request.getStatus() != null) {
            registration.setStatus(request.getStatus());
        }
        if (request.getRegistrationDate() != null) {
            registration.setRegistrationDate(parseInstant(request.getRegistrationDate()));
        }

        Registration updatedRegistration = registrationRepository.save(registration);

        return ResponseEntity.ok(updatedRegistration);
    }

    /**
     * AC12: Delete a registration
     * DELETE /api/v1/events/{eventId}/registrations/{registrationId}
     */
    @DeleteMapping("/{registrationId}")
    public ResponseEntity<Void> deleteRegistration(
            @PathVariable UUID eventId,
            @PathVariable UUID registrationId) {

        // Verify event exists
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }

        // Find existing registration
        Optional<Registration> existingRegistration = registrationRepository.findById(registrationId);
        if (existingRegistration.isEmpty()) {
            throw new ValidationException("Registration not found: " + registrationId);
        }

        // Verify registration belongs to the event
        if (!existingRegistration.get().getEventId().equals(eventId)) {
            throw new ValidationException("Registration does not belong to this event");
        }

        // Delete registration
        registrationRepository.deleteById(registrationId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Build JPA Specification from FilterCriteria for registrations
     */
    private Specification<Registration> buildRegistrationSpecification(FilterCriteria criteria) {
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
                    Specification<Registration> andSpec = buildRegistrationSpecification(children.get(0));
                    for (int i = 1; i < children.size(); i++) {
                        Specification<Registration> childSpec = buildRegistrationSpecification(children.get(i));
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
