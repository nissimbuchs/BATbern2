package ch.batbern.events.controller;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.core.api.generated.TeaserImagesApi;
import ch.batbern.events.core.dto.generated.TeaserImageConfirmRequest;
import ch.batbern.events.core.dto.generated.TeaserImageItem;
import ch.batbern.events.core.dto.generated.TeaserImageUpdateRequest;
import ch.batbern.events.core.dto.generated.TeaserImageUploadUrlRequest;
import ch.batbern.events.core.dto.generated.TeaserImageUploadUrlResponse;
import ch.batbern.events.service.EventTeaserImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * <p>
 * {@code implements} the generated {@link TeaserImagesApi} interface (events-core spec,
 * ADR-006 contract-first). The interface supplies the verb/path/param mapping and the
 * generated DTO types; the class-level {@code @RequestMapping("/api/v1")} supplies the
 * version prefix (interface paths are {@code /events/{eventCode}/teaser-images...}).
 * Method-level {@code @PreAuthorize}/{@code @CacheEvict} are orthogonal to the mapping and
 * stay on the overrides; the {@code _global} bypass on list relies on no eventCode
 * {@code @Pattern} in the spec.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class EventTeaserImageController implements TeaserImagesApi {

    static final String GLOBAL_CODE = "_global";

    private final EventTeaserImageService teaserImageService;

    private static String resolveEventCode(String eventCode) {
        return GLOBAL_CODE.equals(eventCode) ? null : eventCode;
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER') or #eventCode == '_global'")
    public ResponseEntity<List<TeaserImageItem>> listTeaserImages(String eventCode) {
        return ResponseEntity.ok(teaserImageService.listByEventCode(resolveEventCode(eventCode)));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TeaserImageUploadUrlResponse> generateTeaserImageUploadUrl(
            String eventCode,
            TeaserImageUploadUrlRequest request) {
        return ResponseEntity.ok(
                teaserImageService.generateUploadUrl(
                        resolveEventCode(eventCode), request.getContentType(), request.getFileName()));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<TeaserImageItem> confirmTeaserImageUpload(
            String eventCode,
            TeaserImageConfirmRequest request) {
        return ResponseEntity.ok(
                teaserImageService.confirmUpload(resolveEventCode(eventCode), request.getS3Key()));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<TeaserImageItem> updateTeaserImage(
            String eventCode,
            UUID imageId,
            TeaserImageUpdateRequest request) {
        return ResponseEntity.ok(
                teaserImageService.updatePresentationPosition(
                        resolveEventCode(eventCode), imageId, request.getPresentationPosition()));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)
    public ResponseEntity<Void> deleteTeaserImage(
            String eventCode,
            UUID imageId) {
        teaserImageService.deleteTeaserImage(resolveEventCode(eventCode), imageId);
        return ResponseEntity.noContent().build();
    }
}
