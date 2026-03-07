package ch.batbern.events.controller;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.dto.generated.TeaserImageConfirmRequest;
import ch.batbern.events.dto.generated.TeaserImageItem;
import ch.batbern.events.dto.generated.TeaserImageUpdateRequest;
import ch.batbern.events.dto.generated.TeaserImageUploadUrlRequest;
import ch.batbern.events.dto.generated.TeaserImageUploadUrlResponse;
import ch.batbern.events.service.EventTeaserImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for event teaser image management.
 * <p>
 * Story 10.22: Event Teaser Images for Moderator Presentation Page
 * <p>
 * All endpoints are ORGANIZER-only.
 * No API gateway changes needed: POST/DELETE to /api/v1/events/** already route
 * to event-management-service and require auth.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class EventTeaserImageController {

    private final EventTeaserImageService teaserImageService;

    /**
     * Phase 1: Generate presigned S3 PUT URL for teaser image upload.
     * AC2 — Upload flow
     */
    @PostMapping("/events/{eventCode}/teaser-images/upload-url")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TeaserImageUploadUrlResponse> generateUploadUrl(
            @PathVariable String eventCode,
            @RequestBody TeaserImageUploadUrlRequest request) {
        return ResponseEntity.ok(
                teaserImageService.generateUploadUrl(eventCode, request.getContentType(), request.getFileName())
        );
    }

    /**
     * Phase 3: Confirm upload and persist new teaser image.
     * AC2 — Upload flow; AC6 — 422 on limit
     */
    @PostMapping("/events/{eventCode}/teaser-images/confirm")
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<TeaserImageItem> confirmUpload(
            @PathVariable String eventCode,
            @RequestBody TeaserImageConfirmRequest request) {
        return ResponseEntity.ok(
                teaserImageService.confirmUpload(eventCode, request.getS3Key())
        );
    }

    /**
     * Update the presentation position of a teaser image.
     */
    @PatchMapping("/events/{eventCode}/teaser-images/{imageId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<TeaserImageItem> updateTeaserImage(
            @PathVariable String eventCode,
            @PathVariable UUID imageId,
            @RequestBody TeaserImageUpdateRequest request) {
        return ResponseEntity.ok(
                teaserImageService.updatePresentationPosition(eventCode, imageId, request.getPresentationPosition())
        );
    }

    /**
     * Delete a teaser image (DB record + S3 object, best-effort).
     * AC3 — Delete
     */
    @DeleteMapping("/events/{eventCode}/teaser-images/{imageId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<Void> deleteTeaserImage(
            @PathVariable String eventCode,
            @PathVariable UUID imageId) {
        teaserImageService.deleteTeaserImage(eventCode, imageId);
        return ResponseEntity.noContent().build();
    }
}
