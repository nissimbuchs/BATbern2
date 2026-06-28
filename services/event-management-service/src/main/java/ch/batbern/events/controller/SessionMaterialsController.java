package ch.batbern.events.controller;

import ch.batbern.events.domain.Session;
import ch.batbern.events.exception.SessionNotFoundException;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.SessionMaterialsService;
import ch.batbern.events.sessions.api.generated.SessionMaterialsApi;
import ch.batbern.events.sessions.dto.generated.MaterialDownloadUrlResponse;
import ch.batbern.events.sessions.dto.generated.SessionMaterialAssociationRequest;
import ch.batbern.events.sessions.dto.generated.SessionMaterialResponse;
import ch.batbern.events.sessions.dto.generated.SessionMaterialsResponse;
import ch.batbern.events.sessions.dto.generated.UploadMaterialFromUrlRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Session Materials Management.
 * Story 5.9 - Session Materials Upload.
 *
 * <p>Implements the generated {@link SessionMaterialsApi} contract (Phase 7
 * contract-first wiring) — the interface carries the {@code @RequestMapping}
 * annotations, paths, and bean-validation, so this class only supplies
 * {@code /api/v1} as the prefix and the method bodies.
 *
 * <p>RBAC (AC7): speakers may only upload/delete materials for their own
 * sessions; organizers may operate on any session. Enforced via
 * {@code @PreAuthorize} + inline ownership checks.
 */
@RestController
@RequestMapping("/api/v1")
public class SessionMaterialsController implements SessionMaterialsApi {

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

    @Override
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('SPEAKER')")
    public ResponseEntity<SessionMaterialsResponse> associateMaterials(
            String eventCode,
            String sessionSlug,
            SessionMaterialAssociationRequest sessionMaterialAssociationRequest) {

        String username = securityContextHelper.getCurrentUsername();

        // AC7: Speakers may only upload to their own sessions (organizers bypass).
        assertSessionOwnershipUnlessOrganizer(sessionSlug, username,
                "Speaker can only upload materials to their own sessions");

        List<SessionMaterialResponse> materials = sessionMaterialsService
                .associateMaterialsWithSession(sessionSlug, sessionMaterialAssociationRequest, username);

        // Story 5.9: Clear event cache so new materials appear in the next fetch.
        clearEventCache(eventCode);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SessionMaterialsResponse().materials(materials));
    }

    @Override
    public ResponseEntity<SessionMaterialsResponse> getSessionMaterials(
            String eventCode,
            String sessionSlug) {

        List<SessionMaterialResponse> materials = sessionMaterialsService
                .getMaterialsBySession(sessionSlug);

        return ResponseEntity.ok(new SessionMaterialsResponse().materials(materials));
    }

    @Override
    public ResponseEntity<MaterialDownloadUrlResponse> getMaterialDownloadUrl(
            String eventCode,
            String sessionSlug,
            UUID materialId) {

        String downloadUrl = sessionMaterialsService.generateDownloadUrl(materialId);

        return ResponseEntity.ok(new MaterialDownloadUrlResponse().downloadUrl(downloadUrl));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('SPEAKER')")
    public ResponseEntity<Void> deleteSessionMaterial(
            String eventCode,
            String sessionSlug,
            UUID materialId) {

        String username = securityContextHelper.getCurrentUsername();

        // AC7: Speakers may only delete from their own sessions (organizers bypass).
        assertSessionOwnershipUnlessOrganizer(sessionSlug, username,
                "Speaker can only delete materials from their own sessions");

        sessionMaterialsService.deleteMaterial(sessionSlug, materialId, username);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SessionMaterialResponse> uploadMaterialFromUrl(
            String eventCode,
            String sessionSlug,
            UploadMaterialFromUrlRequest uploadMaterialFromUrlRequest) {

        String url = uploadMaterialFromUrlRequest.getUrl();
        String filename = uploadMaterialFromUrlRequest.getFilename();
        String materialType = uploadMaterialFromUrlRequest.getMaterialType() != null
                ? uploadMaterialFromUrlRequest.getMaterialType()
                : "DOCUMENT";

        if (url == null || url.isBlank() || filename == null || filename.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String username = securityContextHelper.getCurrentUsername();

        try {
            SessionMaterialResponse material = sessionMaterialsService
                    .uploadMaterialFromUrl(sessionSlug, url, filename, materialType, username);

            // Clear event cache to include the new material.
            clearEventCache(eventCode);

            return ResponseEntity.status(HttpStatus.CREATED).body(material);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * AC7: throw {@link AccessDeniedException} when a non-organizer is not assigned
     * to the session. Organizers bypass the check entirely.
     */
    private void assertSessionOwnershipUnlessOrganizer(String sessionSlug, String username, String message) {
        if (securityContextHelper.hasRole("ORGANIZER")) {
            return;
        }
        Session session = sessionRepository.findBySessionSlug(sessionSlug)
                .orElseThrow(() -> new SessionNotFoundException(sessionSlug));
        if (!sessionUserRepository.existsBySessionIdAndUsername(session.getId(), username)) {
            throw new AccessDeniedException(message);
        }
    }

    /**
     * Clear event cache for the given event code.
     * Story 5.9: invalidate cache when materials change.
     */
    private void clearEventCache(String eventCode) {
        Cache cache = cacheManager.getCache("eventWithIncludes");
        if (cache != null) {
            cache.evict(eventCode + "_venue,topics,sessions,workflow,metrics,registrations");
            cache.evict(eventCode + "_sessions");
            cache.evict(eventCode + "_none");
            // Clear the entire cache to be safe (sessions appear in various include combinations).
            cache.clear();
        }
    }
}
