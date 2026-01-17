package ch.batbern.events.controller;

import ch.batbern.events.dto.SessionMaterialAssociationRequest;
import ch.batbern.events.dto.SessionMaterialResponse;
import ch.batbern.events.service.SessionMaterialsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
 * - POST   /api/v1/sessions/{sessionSlug}/materials     - Associate materials with session (AC5)
 * - GET    /api/v1/sessions/{sessionSlug}/materials     - List session materials (AC5)
 * - DELETE /api/v1/sessions/{sessionSlug}/materials/{materialId} - Remove material (AC5)
 *
 * RBAC (AC7):
 * - Speakers can only upload/delete materials for their own sessions
 * - Organizers can upload/delete materials for any session
 * - RBAC enforced via @PreAuthorize annotations + SessionSecurityService
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionSlug}/materials")
public class SessionMaterialsController {

    @Autowired
    private SessionMaterialsService sessionMaterialsService;

    @Autowired
    private SessionSecurityService sessionSecurityService;

    /**
     * AC5: Associate uploaded materials with session
     * POST /api/v1/sessions/{sessionSlug}/materials
     *
     * RBAC (AC7):
     * - Organizers: Can upload to any session
     * - Speakers: Can only upload to their own sessions
     *
     * @param sessionSlug Session identifier
     * @param request Association request with uploadIds and materialTypes
     * @param authentication Current user authentication
     * @return 201 Created with materials list
     */
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER') or @sessionSecurityService.isSpeakerOfSession(#sessionSlug, authentication.name)")
    public ResponseEntity<Map<String, Object>> associateMaterials(
            @PathVariable String sessionSlug,
            @Valid @RequestBody SessionMaterialAssociationRequest request,
            Authentication authentication) {

        String uploadedBy = authentication.getName();
        List<SessionMaterialResponse> materials = sessionMaterialsService.associateMaterialsWithSession(
                sessionSlug, request, uploadedBy);

        Map<String, Object> response = new HashMap<>();
        response.put("materials", materials);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * AC5: List all materials for a session
     * GET /api/v1/sessions/{sessionSlug}/materials
     *
     * Public endpoint (no authentication required for archived events)
     * For upcoming events, authentication required
     *
     * @param sessionSlug Session identifier
     * @return 200 OK with materials list
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getMaterials(
            @PathVariable String sessionSlug) {

        List<SessionMaterialResponse> materials = sessionMaterialsService.getMaterialsBySession(sessionSlug);

        Map<String, Object> response = new HashMap<>();
        response.put("materials", materials);

        return ResponseEntity.ok(response);
    }

    /**
     * AC5 & AC7: Delete a material from session
     * DELETE /api/v1/sessions/{sessionSlug}/materials/{materialId}
     *
     * RBAC (AC7):
     * - Organizers: Can delete any material
     * - Speakers: Can only delete materials from their own sessions
     *
     * @param sessionSlug Session identifier
     * @param materialId Material UUID
     * @param authentication Current user authentication
     * @return 204 No Content
     */
    @DeleteMapping("/{materialId}")
    @PreAuthorize("hasRole('ORGANIZER') or @sessionSecurityService.isSpeakerOfSession(#sessionSlug, authentication.name)")
    public ResponseEntity<Void> deleteMaterial(
            @PathVariable String sessionSlug,
            @PathVariable UUID materialId,
            Authentication authentication) {

        String username = authentication.getName();
        sessionMaterialsService.deleteMaterial(sessionSlug, materialId, username);
        return ResponseEntity.noContent().build();
    }
}
