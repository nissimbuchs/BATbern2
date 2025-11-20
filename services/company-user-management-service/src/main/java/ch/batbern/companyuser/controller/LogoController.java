package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.dto.LogoUploadConfirmRequest;
import ch.batbern.companyuser.dto.LogoUploadRequest;
import ch.batbern.companyuser.dto.PresignedUploadUrl;
import ch.batbern.companyuser.service.GenericLogoService;
import ch.batbern.companyuser.service.LogoCleanupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller for generic file upload operations
 * Story 1.16.3: Generic File Upload Service
 * ADR-002: Generic File Upload Service Architecture
 *
 * Provides entity-agnostic file upload endpoints:
 * - Generate presigned URL for upload (no entity required)
 * - Confirm upload completion
 * - Delete unused uploads
 *
 * Three-phase upload flow:
 * 1. POST /logos/presigned-url → Get upload URL
 * 2. PUT (presigned URL) → Upload file to S3
 * 3. POST /logos/{uploadId}/confirm → Confirm upload
 * 4. POST /companies (with logoUploadId) → Associate with entity
 */
@RestController
@RequestMapping("/api/v1/logos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "File Upload", description = "Generic file upload operations for logos and images")
public class LogoController {

    private final GenericLogoService logoService;
    private final LogoCleanupService cleanupService;

    /**
     * Phase 1: Request presigned URL for file upload
     * No authentication required - anyone can upload
     * No entity association required at this stage
     *
     * @param request Upload request with file metadata
     * @return Presigned URL and upload metadata
     */
    @PostMapping("/presigned-url")
    @Operation(
            summary = "Generate presigned URL for file upload",
            description = "Creates a presigned S3 URL for uploading a logo. "
                    + "The URL expires after 15 minutes. Supports PNG, JPEG, and SVG files up to 5MB. "
                    + "After uploading to S3, call the confirm endpoint to complete the upload. "
                    + "No entity association required - works standalone."
    )
    @ApiResponses(value = {
        @ApiResponse(
                responseCode = "200",
                description = "Presigned URL generated successfully",
                content = @Content(schema = @Schema(implementation = PresignedUploadUrl.class))
            ),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid file type or size exceeds limit (5MB)"
            )
    })
    public ResponseEntity<PresignedUploadUrl> requestUploadUrl(
            @Valid @RequestBody LogoUploadRequest request) {
        log.info("Requesting presigned upload URL for file: {}, size: {} bytes",
                request.getFileName(), request.getFileSize());

        PresignedUploadUrl response = logoService.generatePresignedUrl(
                request.getFileName(),
                request.getFileSize(),
                request.getMimeType()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Phase 2: Confirm upload completion
     * Called by client after successfully uploading file to S3
     * Updates logo status from PENDING to CONFIRMED
     *
     * @param uploadId Upload identifier from presigned URL response
     * @param request  Confirmation request with checksum
     * @return Success response
     */
    @PostMapping("/{uploadId}/confirm")
    @Operation(
            summary = "Confirm file upload completion",
            description = "Confirms that the file has been successfully uploaded to S3. "
                    + "Updates the logo status from PENDING to CONFIRMED. "
                    + "Logo will expire after 7 days if not associated with an entity."
    )
    @ApiResponses(value = {
        @ApiResponse(
                responseCode = "200",
                description = "Upload confirmed successfully"
            ),
        @ApiResponse(
                responseCode = "404",
                description = "Upload ID not found"
            ),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid upload state (not PENDING)"
            )
    })
    public ResponseEntity<Void> confirmUpload(
            @Parameter(description = "Upload ID from presigned URL response", required = true)
            @PathVariable String uploadId,
            @Valid @RequestBody LogoUploadConfirmRequest request) {
        log.info("Confirming upload for uploadId: {}, extension: {}",
                uploadId, request.getFileExtension());

        logoService.confirmUpload(uploadId, request.getChecksum());

        return ResponseEntity.ok().build();
    }

    /**
     * Delete unused logo
     * Can only delete logos in PENDING or CONFIRMED status
     * ASSOCIATED logos cannot be deleted (must delete entity first)
     *
     * @param uploadId Upload identifier
     * @return Success response
     */
    @DeleteMapping("/{uploadId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Delete unused logo",
            description = "Deletes a logo that has not been associated with an entity. "
                    + "Can only delete logos in PENDING or CONFIRMED status. "
                    + "ASSOCIATED logos cannot be deleted directly."
    )
    @ApiResponses(value = {
        @ApiResponse(
                responseCode = "204",
                description = "Logo deleted successfully"
            ),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized - missing or invalid JWT token"
            ),
        @ApiResponse(
                responseCode = "404",
                description = "Upload ID not found"
            ),
        @ApiResponse(
                responseCode = "400",
                description = "Cannot delete ASSOCIATED logo"
            )
    })
    public ResponseEntity<Void> deleteUnusedLogo(
            @Parameter(description = "Upload ID to delete", required = true)
            @PathVariable String uploadId) {
        log.info("Deleting unused logo: {}", uploadId);

        logoService.deleteUnusedLogo(uploadId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Get cleanup statistics
     * Admin endpoint for monitoring orphaned uploads
     * Requires ORGANIZER role
     *
     * @return Cleanup statistics
     */
    @GetMapping("/cleanup/statistics")
    @PreAuthorize("hasRole('ORGANIZER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get cleanup statistics",
            description = "Returns statistics about logo uploads in various states. "
                    + "Used for monitoring and identifying orphaned uploads. "
                    + "Requires ORGANIZER role."
    )
    @ApiResponses(value = {
        @ApiResponse(
                responseCode = "200",
                description = "Statistics retrieved successfully",
                content = @Content(
                                schema = @Schema(implementation = LogoCleanupService.CleanupStatistics.class))
            ),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized - missing or invalid JWT token"
            ),
        @ApiResponse(
                responseCode = "403",
                description = "Forbidden - requires ORGANIZER role"
            )
    })
    public ResponseEntity<LogoCleanupService.CleanupStatistics> getCleanupStatistics() {
        log.debug("Fetching cleanup statistics");

        LogoCleanupService.CleanupStatistics stats = cleanupService.getCleanupStatistics();

        return ResponseEntity.ok(stats);
    }

    /**
     * Manually trigger cleanup job
     * Admin endpoint for emergency cleanup
     * Requires ORGANIZER role
     *
     * @return Success response
     */
    @PostMapping("/cleanup/trigger")
    @PreAuthorize("hasRole('ORGANIZER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Manually trigger cleanup job",
            description = "Manually triggers the cleanup job to remove orphaned uploads. "
                    + "Normally runs automatically at 2 AM daily. "
                    + "Use this endpoint for testing or emergency cleanup. "
                    + "Requires ORGANIZER role."
    )
    @ApiResponses(value = {
        @ApiResponse(
                responseCode = "202",
                description = "Cleanup job triggered successfully"
            ),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized - missing or invalid JWT token"
            ),
        @ApiResponse(
                responseCode = "403",
                description = "Forbidden - requires ORGANIZER role"
            )
    })
    public ResponseEntity<Void> triggerManualCleanup() {
        log.warn("Manual cleanup triggered by admin");

        cleanupService.triggerManualCleanup();

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
