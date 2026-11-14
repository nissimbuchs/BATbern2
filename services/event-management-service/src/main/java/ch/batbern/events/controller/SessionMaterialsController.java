package ch.batbern.events.controller;

import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.SessionMaterialUploadHandler;
import ch.batbern.events.service.SessionMaterialsService;
import ch.batbern.events.sessions.api.generated.SessionMaterialsApi;
import ch.batbern.events.sessions.dto.generated.DownloadUrlResponse;
import ch.batbern.events.sessions.dto.generated.SessionMaterialAssociationRequest;
import ch.batbern.events.sessions.dto.generated.SessionMaterialResponse;
import ch.batbern.events.sessions.dto.generated.SessionMaterialsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * <p>RBAC (AC7): speakers may only attach/delete materials for their own
 * sessions; organizers may operate on any session. Enforced via
 * {@code @PreAuthorize} + {@link SessionMaterialUploadHandler#checkMayChangeMaterials}.
 * Files are uploaded through the upload contract, purpose {@code session-material} (Story 17.11d).
 */
@RestController
@RequestMapping("/api/v1")
public class SessionMaterialsController implements SessionMaterialsApi {

    @Autowired
    private SessionMaterialsService sessionMaterialsService;

    @Autowired
    private SessionMaterialUploadHandler materialUploads;

    @Autowired
    private SecurityContextHelper securityContextHelper;

    /**
     * Story 17.11d: attaches confirmed {@code session-material} uploads (the session editor's case;
     * an upload for an existing session is attached by its confirm). The service checks the session
     * and the caller's right to change its materials, and clears the event cache after the commit.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('SPEAKER')")
    public ResponseEntity<SessionMaterialsResponse> associateMaterials(
            String eventCode,
            String sessionSlug,
            SessionMaterialAssociationRequest sessionMaterialAssociationRequest) {

        String username = securityContextHelper.getCurrentUsername();
        List<SessionMaterialResponse> materials = sessionMaterialsService
                .associateMaterialsWithSession(eventCode, sessionSlug, sessionMaterialAssociationRequest, username);

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
    public ResponseEntity<DownloadUrlResponse> getMaterialDownloadUrl(
            String eventCode,
            String sessionSlug,
            UUID materialId) {

        String downloadUrl = sessionMaterialsService.generateDownloadUrl(materialId);

        return ResponseEntity.ok(new DownloadUrlResponse().downloadUrl(downloadUrl));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('SPEAKER')")
    public ResponseEntity<Void> deleteSessionMaterial(
            String eventCode,
            String sessionSlug,
            UUID materialId) {

        String username = securityContextHelper.getCurrentUsername();

        // AC7: Speakers may only delete from their own sessions (organizers bypass).
        // Story 17.11d: the session must be in the event (404), and a speaker must be on it (403)
        materialUploads.checkMayChangeMaterials(materialUploads.session(eventCode, sessionSlug));

        sessionMaterialsService.deleteMaterial(eventCode, sessionSlug, materialId, username);
        return ResponseEntity.noContent().build();
    }
}
