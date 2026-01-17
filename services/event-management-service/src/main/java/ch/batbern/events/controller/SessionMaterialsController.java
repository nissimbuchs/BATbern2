package ch.batbern.events.controller;

import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.SessionMaterialAssociationRequest;
import ch.batbern.events.dto.SessionMaterialResponse;
import ch.batbern.events.exception.SessionNotFoundException;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.SessionMaterialsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Session Materials Management
 * Story 5.9 - Session Materials Upload
 *
 * Endpoints:
 * - POST   /api/v1/events/{eventCode}/sessions/{sessionSlug}/materials     - Associate materials with session (AC5)
 * - GET    /api/v1/events/{eventCode}/sessions/{sessionSlug}/materials     - List session materials (AC5)
 * - DELETE /api/v1/events/{eventCode}/sessions/{sessionSlug}/materials/{materialId} - Remove material (AC5)
 *
 * RBAC (AC7):
 * - Speakers can only upload/delete materials for their own sessions
 * - Organizers can upload/delete materials for any session
 * - RBAC enforced via @PreAuthorize annotations + inline permission checks
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/sessions/{sessionSlug}/materials")
public class SessionMaterialsController {

    @Autowired
    private SessionMaterialsService sessionMaterialsService;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionUserRepository sessionUserRepository;

    @Autowired
    private SecurityContextHelper securityContextHelper;

    @Autowired
    private CacheManager cacheManager;

    /**
     * AC5: Associate uploaded materials with session
     * POST /api/v1/events/{eventCode}/sessions/{sessionSlug}/materials
     *
     * RBAC (AC7):
     * - Organizers: Can upload to any session
     * - Speakers: Can only upload to their own sessions
     *
     * @param eventCode Event code identifier
     * @param sessionSlug Session identifier
     * @param request Association request with uploadIds and materialTypes
     * @return 201 Created with materials list
     */
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('SPEAKER')")
    public ResponseEntity<Map<String, Object>> associateMaterials(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug,
            @Valid @RequestBody SessionMaterialAssociationRequest request) {

        String username = securityContextHelper.getCurrentUsername();

        // AC7: Check speaker owns session (organizers bypass this check)
        if (!securityContextHelper.hasRole("ORGANIZER")) {
            Session session = sessionRepository.findBySessionSlug(sessionSlug)
                    .orElseThrow(() -> new SessionNotFoundException(sessionSlug));

            if (!sessionUserRepository.existsBySessionIdAndUsername(session.getId(), username)) {
                throw new AccessDeniedException(
                        "Speaker can only upload materials to their own sessions");
            }
        }

        List<SessionMaterialResponse> materials = sessionMaterialsService
                .associateMaterialsWithSession(sessionSlug, request, username);

        // Story 5.9: Clear event cache to include new materials in next fetch
        clearEventCache(eventCode);

        Map<String, Object> response = new HashMap<>();
        response.put("materials", materials);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Clear event cache for the given event code
     * Story 5.9: Invalidate cache when materials change
     */
    private void clearEventCache(String eventCode) {
        Cache cache = cacheManager.getCache("eventWithIncludes");
        if (cache != null) {
            // Clear all cache entries for this event (different include combinations)
            cache.evict(eventCode + "_venue,topics,sessions,workflow,metrics,registrations");
            cache.evict(eventCode + "_sessions");
            cache.evict(eventCode + "_none");
            // Clear the entire cache to be safe (sessions are included in various combinations)
            cache.clear();
        }
    }

    /**
     * AC5: List all materials for a session
     * GET /api/v1/events/{eventCode}/sessions/{sessionSlug}/materials
     *
     * Public endpoint (no authentication required for archived events)
     * For upcoming events, authentication required
     *
     * @param eventCode Event code identifier
     * @param sessionSlug Session identifier
     * @return 200 OK with materials list
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getMaterials(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug) {

        List<SessionMaterialResponse> materials = sessionMaterialsService
                .getMaterialsBySession(sessionSlug);

        Map<String, Object> response = new HashMap<>();
        response.put("materials", materials);

        return ResponseEntity.ok(response);
    }

    /**
     * AC5: Generate presigned download URL for a material
     * GET /api/v1/events/{eventCode}/sessions/{sessionSlug}/materials/{materialId}/download
     *
     * Public endpoint for downloading materials
     *
     * @param eventCode Event code identifier
     * @param sessionSlug Session identifier
     * @param materialId Material UUID
     * @return 200 OK with presigned download URL
     */
    @GetMapping("/{materialId}/download")
    public ResponseEntity<Map<String, String>> getDownloadUrl(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug,
            @PathVariable UUID materialId) {

        String downloadUrl = sessionMaterialsService.generateDownloadUrl(materialId);

        Map<String, String> response = new HashMap<>();
        response.put("downloadUrl", downloadUrl);

        return ResponseEntity.ok(response);
    }

    /**
     * AC5 & AC7: Delete a material from session
     * DELETE /api/v1/events/{eventCode}/sessions/{sessionSlug}/materials/{materialId}
     *
     * RBAC (AC7):
     * - Organizers: Can delete any material
     * - Speakers: Can only delete materials from their own sessions
     *
     * @param eventCode Event code identifier
     * @param sessionSlug Session identifier
     * @param materialId Material UUID
     * @return 204 No Content
     */
    @DeleteMapping("/{materialId}")
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('SPEAKER')")
    public ResponseEntity<Void> deleteMaterial(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug,
            @PathVariable UUID materialId) {

        String username = securityContextHelper.getCurrentUsername();

        // AC7: Check speaker owns session (organizers bypass this check)
        if (!securityContextHelper.hasRole("ORGANIZER")) {
            Session session = sessionRepository.findBySessionSlug(sessionSlug)
                    .orElseThrow(() -> new SessionNotFoundException(sessionSlug));

            if (!sessionUserRepository.existsBySessionIdAndUsername(session.getId(), username)) {
                throw new AccessDeniedException(
                        "Speaker can only delete materials from their own sessions");
            }
        }

        sessionMaterialsService.deleteMaterial(sessionSlug, materialId, username);
        return ResponseEntity.noContent().build();
    }
}
