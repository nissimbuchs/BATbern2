package ch.batbern.events.controller;

import ch.batbern.events.dto.PresignedMaterialUploadUrl;
import ch.batbern.events.service.MaterialsUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Materials upload controller for generating presigned URLs
 * Story 5.9: Session Materials Upload
 *
 * Provides endpoints for 3-phase material upload:
 * 1. POST /materials/presigned-url - Generate presigned URL
 * 2. Client uploads directly to S3 using presigned URL
 * 3. POST /materials/{uploadId}/confirm - Confirm upload
 * 4. POST /sessions/{sessionSlug}/materials - Associate with session (in SessionMaterialsController)
 */
@RestController
@RequestMapping("/api/v1/materials")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Materials Upload", description = "Material upload management endpoints")
public class MaterialsUploadController {

    private final MaterialsUploadService materialsUploadService;

    /**
     * Generate presigned URL for material upload
     * Story 5.9 - AC6: File type validation
     *
     * @param request Upload request with fileName, fileSize, mimeType
     * @return Presigned URL and upload metadata
     */
    @PostMapping("/presigned-url")
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('SPEAKER')")
    @Operation(summary = "Generate presigned URL for material upload")
    public ResponseEntity<PresignedMaterialUploadUrl> generatePresignedUrl(
            @RequestBody MaterialUploadRequest request) {
        log.info("Generating presigned URL for material: {}", request.getFileName());

        PresignedMaterialUploadUrl response = materialsUploadService.generatePresignedUrl(
                request.getFileName(),
                request.getFileSize(),
                request.getMimeType()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Confirm material upload to S3
     * Story 5.9 - Phase 2 of 3-phase upload
     *
     * @param uploadId Upload ID from presigned URL generation (path variable)
     * @param request Confirmation request with fileId, fileExtension, checksum
     * @return 204 No Content
     */
    @PostMapping("/{uploadId}/confirm")
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('SPEAKER')")
    @Operation(summary = "Confirm material upload")
    public ResponseEntity<Void> confirmUpload(
            @PathVariable String uploadId,
            @RequestBody MaterialUploadConfirmRequest request) {
        log.info("Confirming material upload for uploadId: {} (fileId from request: {})",
                uploadId, request.getFileId());

        materialsUploadService.confirmUpload(uploadId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Material upload request DTO
     */
    public static class MaterialUploadRequest {
        private String fileName;
        private long fileSize;
        private String mimeType;

        public MaterialUploadRequest() {}

        public MaterialUploadRequest(String fileName, long fileSize, String mimeType) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.mimeType = mimeType;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }
    }

    /**
     * Material upload confirmation request DTO
     * Matches pattern from LogoUploadConfirmRequest for consistency
     */
    public static class MaterialUploadConfirmRequest {
        private String fileId; // Changed from uploadId to match frontend and existing DTOs
        private String fileExtension;
        private String checksum;

        public MaterialUploadConfirmRequest() {}

        public MaterialUploadConfirmRequest(String fileId, String fileExtension, String checksum) {
            this.fileId = fileId;
            this.fileExtension = fileExtension;
            this.checksum = checksum;
        }

        public String getFileId() {
            return fileId;
        }

        public void setFileId(String fileId) {
            this.fileId = fileId;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        public void setFileExtension(String fileExtension) {
            this.fileExtension = fileExtension;
        }

        public String getChecksum() {
            return checksum;
        }

        public void setChecksum(String checksum) {
            this.checksum = checksum;
        }
    }
}
