package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.api.generated.LogosApi;
import ch.batbern.companyuser.dto.generated.FetchImageFromUrlRequest;
import ch.batbern.companyuser.dto.generated.LogoCleanupStatistics;
import ch.batbern.companyuser.dto.generated.LogoPresignedUploadUrl;
import ch.batbern.companyuser.dto.generated.LogoUploadConfirmRequest;
import ch.batbern.companyuser.dto.generated.LogoUploadRequest;
import ch.batbern.companyuser.dto.generated.UploadImageFromUrlRequest;
import ch.batbern.companyuser.dto.generated.UploadImageFromUrlResponse;
import ch.batbern.companyuser.service.GenericLogoService;
import ch.batbern.companyuser.service.ImageUrlFetcher;
import ch.batbern.companyuser.service.LogoCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generic logo/image upload — implements the generated {@link LogosApi}
 * (companies-api {@code Logos} tag, Story 1.16.3 / ADR-002).
 *
 * <p>Three-phase upload flow: presigned-url → (client PUTs to S3) → confirm → associate with an
 * entity. Plus a URL proxy/import path (for batch logo import) and admin cleanup ops.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class LogoController implements LogosApi {

    private final GenericLogoService logoService;
    private final LogoCleanupService cleanupService;

    /** Phase 1: request a presigned upload URL. No authentication required. */
    @Override
    public ResponseEntity<LogoPresignedUploadUrl> requestLogoUploadUrl(LogoUploadRequest request) {
        log.info("Requesting presigned upload URL for file: {}, size: {} bytes",
                request.getFileName(), request.getFileSize());

        LogoPresignedUploadUrl response = logoService.generatePresignedUrl(
                request.getFileName(),
                request.getFileSize(),
                request.getMimeType());

        return ResponseEntity.ok(response);
    }

    /** Phase 2: confirm upload completion (PENDING → CONFIRMED). No authentication required. */
    @Override
    public ResponseEntity<Void> confirmLogoUpload(String uploadId, LogoUploadConfirmRequest request) {
        log.info("Confirming upload for uploadId: {}, extension: {}",
                uploadId, request.getFileExtension());

        logoService.confirmUpload(uploadId, request.getChecksum());

        return ResponseEntity.ok().build();
    }

    /** Delete an unused (PENDING/CONFIRMED) logo. Requires authentication. */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteUnusedLogo(String uploadId) {
        log.info("Deleting unused logo: {}", uploadId);

        logoService.deleteUnusedLogo(uploadId);

        return ResponseEntity.noContent().build();
    }

    /** Admin: cleanup statistics for orphaned uploads. Requires ORGANIZER role. */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<LogoCleanupStatistics> getLogoCleanupStatistics() {
        log.debug("Fetching cleanup statistics");

        LogoCleanupService.CleanupStatistics stats = cleanupService.getCleanupStatistics();

        return ResponseEntity.ok(new LogoCleanupStatistics()
                .pendingCount(stats.pendingCount())
                .confirmedCount(stats.confirmedCount())
                .associatedCount(stats.associatedCount())
                .expiredPendingCount(stats.expiredPendingCount())
                .expiredConfirmedCount(stats.expiredConfirmedCount()));
    }

    /** Admin: manually trigger the orphaned-upload cleanup job. Requires ORGANIZER role. */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> triggerLogoCleanup() {
        log.warn("Manual cleanup triggered by admin");

        cleanupService.triggerManualCleanup();

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Fetch an image from an external URL and return the raw bytes (CORS proxy for logo import).
     * Up to 10MB; validates the content is an image. No authentication required.
     */
    @Override
    public ResponseEntity<Resource> fetchImageFromUrl(FetchImageFromUrlRequest request) {
        String url = request.getUrl();

        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Fetching image from URL: {}", url);

        try {
            ImageUrlFetcher.FetchedImage image = ImageUrlFetcher.fetch(url, 10 * 1024 * 1024);

            log.info("Successfully fetched image: {} bytes, type: {}",
                    image.body().length, image.contentType());

            // Use MediaType.valueOf() (not parseMediaType) to avoid charset addition.
            MediaType mediaType = MediaType.valueOf(image.contentType());

            return ResponseEntity.ok()
                    .contentLength(image.body().length)
                    .contentType(mediaType)
                    .body(new ByteArrayResource(image.body()));

        } catch (ImageUrlFetcher.ImageFetchException e) {
            log.error("Error fetching image from URL: {}: {}", url, e.getMessage());
            return switch (e.getReason()) {
                case HTTP_STATUS -> ResponseEntity.status(e.getStatusCode()).build();
                case NOT_AN_IMAGE -> ResponseEntity.badRequest().build();
                case TOO_LARGE -> ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
                case IO -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            };
        }
    }

    /**
     * Fetch an image from a URL and upload it directly to S3 (server-side; bypasses the browser
     * to avoid binary corruption). Returns the upload ID. No authentication required.
     */
    @Override
    public ResponseEntity<UploadImageFromUrlResponse> uploadImageFromUrl(UploadImageFromUrlRequest request) {
        String url = request.getUrl();
        String suggestedFilename = request.getFilename() != null ? request.getFilename() : "logo";

        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Uploading image from URL: {}", url);

        try {
            ImageUrlFetcher.FetchedImage image = ImageUrlFetcher.fetch(url, 10 * 1024 * 1024);

            String filename = suggestedFilename + "." + image.extension();

            String uploadId = logoService.uploadLogoDirectly(image.body(), filename, image.contentType());

            log.info("Successfully uploaded image: {} bytes, uploadId: {}", image.body().length, uploadId);

            return ResponseEntity.ok(new UploadImageFromUrlResponse().uploadId(uploadId));

        } catch (ImageUrlFetcher.ImageFetchException e) {
            log.error("Error uploading image from URL: {}: {}", url, e.getMessage());
            return switch (e.getReason()) {
                case HTTP_STATUS -> ResponseEntity.status(e.getStatusCode()).build();
                case NOT_AN_IMAGE -> ResponseEntity.badRequest().build();
                case TOO_LARGE -> ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
                case IO -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            };
        }
    }
}
