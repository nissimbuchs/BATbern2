package ch.batbern.events.controller;

import ch.batbern.events.media.api.generated.MaterialsUploadApi;
import ch.batbern.events.media.dto.generated.MaterialUploadConfirmRequest;
import ch.batbern.events.media.dto.generated.MaterialUploadRequest;
import ch.batbern.events.media.dto.generated.PresignedMaterialUploadUrl;
import ch.batbern.events.service.MaterialsUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Materials upload controller for generating presigned URLs.
 * Story 5.9: Session Materials Upload.
 *
 * <p>3-phase material upload:
 * <ol>
 *   <li>POST /materials/presigned-url - Generate presigned URL</li>
 *   <li>Client uploads directly to S3 using the presigned URL</li>
 *   <li>POST /materials/{uploadId}/confirm - Confirm upload</li>
 * </ol>
 * Association with a session happens in SessionMaterialsController.
 *
 * <p>API-consolidation Phase 7: implements the generated {@link MaterialsUploadApi}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class MaterialsUploadController implements MaterialsUploadApi {

    private final MaterialsUploadService materialsUploadService;

    @Override
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('SPEAKER')")
    public ResponseEntity<PresignedMaterialUploadUrl> generateMaterialPresignedUrl(
            MaterialUploadRequest request) {
        log.info("Generating presigned URL for material: {}", request.getFileName());

        PresignedMaterialUploadUrl response = materialsUploadService.generatePresignedUrl(
                request.getFileName(),
                request.getFileSize(),
                request.getMimeType()
        );

        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('SPEAKER')")
    public ResponseEntity<Void> confirmMaterialUpload(
            String uploadId,
            MaterialUploadConfirmRequest request) {
        log.info("Confirming material upload for uploadId: {} (fileId from request: {})",
                uploadId, request.getFileId());

        materialsUploadService.confirmUpload(uploadId);

        return ResponseEntity.noContent().build();
    }
}
