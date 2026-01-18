package ch.batbern.events.controller;

import ch.batbern.events.domain.SpeakerAvailability;
import ch.batbern.events.dto.SpeakerRequest;
import ch.batbern.events.dto.SpeakerResponse;
import ch.batbern.events.service.SpeakerService;
import ch.batbern.shared.api.PaginationMetadata;
import ch.batbern.shared.dto.PaginatedResponse;
import ch.batbern.shared.types.SpeakerWorkflowState;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for global Speaker profile management - Story 6.0.
 *
 * ADR-003: All endpoints use username as the external identifier.
 *
 * Endpoints:
 * - GET /api/v1/speakers - List speakers with filters
 * - GET /api/v1/speakers/{username} - Get speaker by username
 * - POST /api/v1/speakers - Create new speaker profile
 * - PATCH /api/v1/speakers/{username} - Update speaker profile (partial update)
 * - DELETE /api/v1/speakers/{username} - Soft delete speaker profile
 */
@RestController
@RequestMapping("/api/v1/speakers")
@RequiredArgsConstructor
@Slf4j
public class SpeakerController {

    private final SpeakerService speakerService;

    /**
     * List speakers with pagination and optional filters (AC2, AC4).
     *
     * Public endpoint - anonymous users can view speaker directory.
     *
     * @param availability Optional filter by availability
     * @param workflowState Optional filter by workflow state
     * @param expertiseAreas Optional filter by expertise areas (array contains) (AC4)
     * @param languages Optional filter by languages (array contains) (AC4)
     * @param speakingTopics Optional filter by speaking topics (array contains) (AC4)
     * @param page Page number (1-based for API, converted to 0-based for Spring)
     * @param limit Page size
     * @param sort Sort field and direction (e.g., "username,asc")
     * @return Paginated list of speakers with user enrichment
     */
    @GetMapping
    public ResponseEntity<PaginatedResponse<SpeakerResponse>> listSpeakers(
            @RequestParam(required = false) SpeakerAvailability availability,
            @RequestParam(required = false) SpeakerWorkflowState workflowState,
            @RequestParam(required = false) String expertiseAreas,
            @RequestParam(required = false) String languages,
            @RequestParam(required = false) String speakingTopics,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "username,asc") String sort) {

        log.debug("GET /api/v1/speakers - availability={}, workflowState={}, expertiseAreas={}, "
                        + "languages={}, speakingTopics={}, page={}, limit={}",
                availability, workflowState, expertiseAreas, languages, speakingTopics, page, limit);

        // Convert 1-based API page to 0-based Spring page
        int springPage = Math.max(0, page - 1);

        // Parse sort parameter
        Sort sortSpec = parseSort(sort);
        PageRequest pageable = PageRequest.of(springPage, limit, sortSpec);

        Page<SpeakerResponse> speakerPage = speakerService.listSpeakers(
                availability, workflowState, expertiseAreas, languages, speakingTopics, pageable);

        PaginationMetadata pagination = PaginationMetadata.builder()
                .page(page)
                .limit(limit)
                .totalItems(speakerPage.getTotalElements())
                .totalPages(speakerPage.getTotalPages())
                .hasNext(speakerPage.hasNext())
                .hasPrev(speakerPage.hasPrevious())
                .build();

        PaginatedResponse<SpeakerResponse> response = PaginatedResponse.<SpeakerResponse>builder()
                .data(speakerPage.getContent())
                .pagination(pagination)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Get speaker by username (AC2, AC3).
     *
     * ADR-003: Uses username as public identifier.
     * Public endpoint - anyone can view speaker profile.
     *
     * @param username Speaker's username
     * @param include Optional comma-separated list of expansions (speakingHistory,events,sessions) (AC3)
     * @return Speaker with enriched user data and optional expansions
     */
    @GetMapping("/{username}")
    public ResponseEntity<SpeakerResponse> getSpeaker(
            @PathVariable String username,
            @RequestParam(required = false) String include) {
        log.debug("GET /api/v1/speakers/{} - include={}", username, include);

        SpeakerResponse speaker = speakerService.getSpeakerByUsername(username, include);
        return ResponseEntity.ok(speaker);
    }

    /**
     * Create new speaker profile (AC2).
     *
     * Restricted to ORGANIZER role.
     * ADR-004: User must exist in User Service before creating speaker profile.
     *
     * @param request Speaker creation request
     * @return Created speaker profile
     */
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SpeakerResponse> createSpeaker(@Valid @RequestBody SpeakerRequest request) {
        log.info("POST /api/v1/speakers - username={}", request.getUsername());

        SpeakerResponse created = speakerService.createSpeaker(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update speaker profile (AC2).
     *
     * Restricted to ORGANIZER role.
     *
     * @param username Speaker's username
     * @param request Update request (partial update supported)
     * @return Updated speaker profile
     */
    @PatchMapping("/{username}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SpeakerResponse> updateSpeaker(
            @PathVariable String username,
            @Valid @RequestBody SpeakerRequest request) {

        log.info("PATCH /api/v1/speakers/{}", username);

        SpeakerResponse updated = speakerService.updateSpeaker(username, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Soft delete speaker profile (AC2).
     *
     * Restricted to ORGANIZER role.
     * Sets deletedAt timestamp - record is not physically deleted.
     *
     * @param username Speaker's username
     * @return 204 No Content on success
     */
    @DeleteMapping("/{username}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteSpeaker(@PathVariable String username) {
        log.info("DELETE /api/v1/speakers/{}", username);

        speakerService.deleteSpeaker(username);
        return ResponseEntity.noContent().build();
    }

    /**
     * Check if speaker profile exists (utility endpoint).
     *
     * @param username Speaker's username
     * @return 200 OK if exists, 404 Not Found otherwise
     */
    @GetMapping("/{username}/exists")
    public ResponseEntity<Void> speakerExists(@PathVariable String username) {
        boolean exists = speakerService.speakerExists(username);
        return exists ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    /**
     * Parse sort parameter into Spring Sort object.
     */
    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "username");
        }

        String[] parts = sort.split(",");
        String field = parts[0];
        Sort.Direction direction = parts.length > 1 && parts[1].equalsIgnoreCase("desc")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        return Sort.by(direction, field);
    }
}
