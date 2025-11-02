package ch.batbern.events.controller;

import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.CreateRegistrationRequest;
import ch.batbern.events.dto.PatchRegistrationRequest;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.api.*;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.service.SlugGenerationService;
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
 * Story 1.16.2: Updated to use eventCode instead of UUID
 *
 * Endpoints:
 * - GET    /api/v1/events/{eventCode}/registrations
 * - POST   /api/v1/events/{eventCode}/registrations
 * - PATCH  /api/v1/events/{eventCode}/registrations/{registrationCode}
 * - DELETE /api/v1/events/{eventCode}/registrations/{registrationCode}
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/registrations")
public class RegistrationController {

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SlugGenerationService slugGenerationService;

    /**
     * AC11: List registrations for an event with optional filtering
     * Story 1.16.2: Updated to use eventCode instead of UUID
     * GET /api/v1/events/{eventCode}/registrations?filter={}&page={}&limit={}
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listRegistrations(
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

            // Set eventCode on all registrations for API response
            registrationsPage.getContent().forEach(registration -> registration.setEventCode(eventCode));

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
     * Story 1.16.2: Uses attendeeUsername and auto-generates registrationCode
     * POST /api/v1/events/{eventCode}/registrations
     */
    @PostMapping
    public ResponseEntity<Registration> createRegistration(
            @PathVariable String eventCode,
            @Valid @RequestBody CreateRegistrationRequest request) {

        // Find event by eventCode
        UUID eventId = eventRepository.findByEventCode(eventCode)
                .map(event -> event.getId())
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Generate unique registration code
        String baseCode = eventCode + "-" + request.getAttendeeUsername();
        String registrationCode = slugGenerationService.ensureUniqueSlug(
                baseCode,
                registrationRepository::existsByRegistrationCode
        );

        // Create registration
        Registration registration = Registration.builder()
                .registrationCode(registrationCode)
                .eventId(eventId)
                .attendeeUsername(request.getAttendeeUsername())
                .attendeeName(request.getAttendeeName())
                .attendeeEmail(request.getAttendeeEmail())
                .status(request.getStatus())
                .registrationDate(parseInstant(request.getRegistrationDate()))
                .build();

        Registration savedRegistration = registrationRepository.save(registration);
        savedRegistration.setEventCode(eventCode); // Set for API response

        return ResponseEntity.status(HttpStatus.CREATED).body(savedRegistration);
    }

    /**
     * AC12: Partially update a registration (PATCH)
     * Story 1.16.2: Uses registrationCode as path parameter
     * PATCH /api/v1/events/{eventCode}/registrations/{registrationCode}
     */
    @PatchMapping("/{registrationCode}")
    public ResponseEntity<Registration> patchRegistration(
            @PathVariable String eventCode,
            @PathVariable String registrationCode,
            @Valid @RequestBody PatchRegistrationRequest request) {

        // Find event by eventCode
        UUID eventId = eventRepository.findByEventCode(eventCode)
                .map(event -> event.getId())
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Find existing registration by registrationCode
        Registration registration = registrationRepository.findByRegistrationCode(registrationCode)
                .orElseThrow(() -> new ValidationException("Registration not found: " + registrationCode));

        // Verify registration belongs to the event
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
        updatedRegistration.setEventCode(eventCode); // Set for API response

        return ResponseEntity.ok(updatedRegistration);
    }

    /**
     * AC12: Delete a registration
     * Story 1.16.2: Uses registrationCode as path parameter
     * DELETE /api/v1/events/{eventCode}/registrations/{registrationCode}
     */
    @DeleteMapping("/{registrationCode}")
    public ResponseEntity<Void> deleteRegistration(
            @PathVariable String eventCode,
            @PathVariable String registrationCode) {

        // Find event by eventCode
        UUID eventId = eventRepository.findByEventCode(eventCode)
                .map(event -> event.getId())
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // Find existing registration by registrationCode
        Registration registration = registrationRepository.findByRegistrationCode(registrationCode)
                .orElseThrow(() -> new ValidationException("Registration not found: " + registrationCode));

        // Verify registration belongs to the event
        if (!registration.getEventId().equals(eventId)) {
            throw new ValidationException("Registration does not belong to this event");
        }

        // Delete registration
        registrationRepository.deleteById(registration.getId());

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
