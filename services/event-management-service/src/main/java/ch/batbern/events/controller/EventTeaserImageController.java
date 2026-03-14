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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for teaser image management (event-specific and global).
 * <p>
 * Uses the same endpoints for both. eventCode="_global" maps to null in the
 * service layer, meaning the image has no event association and is shown on
 * all event presentations.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class EventTeaserImageController {

    static final String GLOBAL_CODE = "_global";

    private final EventTeaserImageService teaserImageService;

    private static String resolveEventCode(String eventCode) {
        return GLOBAL_CODE.equals(eventCode) ? null : eventCode;
    }

    @GetMapping("/events/{eventCode}/teaser-images")
    @PreAuthorize("hasRole('ORGANIZER') or #eventCode == '_global'")
    public ResponseEntity<List<TeaserImageItem>> listImages(@PathVariable String eventCode) {
        return ResponseEntity.ok(teaserImageService.listByEventCode(resolveEventCode(eventCode)));
    }

    @PostMapping("/events/{eventCode}/teaser-images/upload-url")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TeaserImageUploadUrlResponse> generateUploadUrl(
            @PathVariable String eventCode,
            @RequestBody TeaserImageUploadUrlRequest request) {
        return ResponseEntity.ok(
                teaserImageService.generateUploadUrl(
                        resolveEventCode(eventCode), request.getContentType(), request.getFileName()));
    }

    @PostMapping("/events/{eventCode}/teaser-images/confirm")
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<TeaserImageItem> confirmUpload(
            @PathVariable String eventCode,
            @RequestBody TeaserImageConfirmRequest request) {
        return ResponseEntity.ok(
                teaserImageService.confirmUpload(resolveEventCode(eventCode), request.getS3Key()));
    }

    @PatchMapping("/events/{eventCode}/teaser-images/{imageId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<TeaserImageItem> updateTeaserImage(
            @PathVariable String eventCode,
            @PathVariable UUID imageId,
            @RequestBody TeaserImageUpdateRequest request) {
        return ResponseEntity.ok(
                teaserImageService.updatePresentationPosition(
                        resolveEventCode(eventCode), imageId, request.getPresentationPosition()));
    }

    @DeleteMapping("/events/{eventCode}/teaser-images/{imageId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<Void> deleteTeaserImage(
            @PathVariable String eventCode,
            @PathVariable UUID imageId) {
        teaserImageService.deleteTeaserImage(resolveEventCode(eventCode), imageId);
        return ResponseEntity.noContent().build();
    }
}
