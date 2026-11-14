package ch.batbern.events.controller;

import ch.batbern.events.media.api.generated.EventPhotosApi;
import ch.batbern.events.media.dto.generated.EventPhotoResponse;
import ch.batbern.events.service.EventPhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for event photo management.
 * <p>
 * Story 10.21: Event Photos Gallery
 * <p>
 * Implements the generated {@link EventPhotosApi} contract (Phase 7 contract-first
 * wiring) — the interface carries the {@code @RequestMapping} annotations, paths, and
 * bean-validation, so this class only supplies {@code /api/v1} as the prefix and the
 * method bodies.
 * <p>
 * Mixed auth: public GET endpoints + ORGANIZER-only delete. Uploads go through the upload
 * contract, purpose {@code event-photo} (Story 17.11b).
 * The homepage sample is {@code GET /public/event-photos} (Story 4.10b; it was
 * {@code /events/recent-photos}, a literal in the {@code {eventCode}} namespace).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class EventPhotoController implements EventPhotosApi {

    private final EventPhotoService photoService;

    /**
     * Public: recent photos from last N events (homepage marquee). AC5.
     * GET /api/v1/public/event-photos
     */
    @Override
    public ResponseEntity<List<EventPhotoResponse>> getRecentEventPhotos(Integer limit, Integer lastNEvents) {
        int clampedLimit = Math.min(Math.max(limit, 1), 100);
        int clampedLastNEvents = Math.min(Math.max(lastNEvents, 1), 20);
        return ResponseEntity.ok(photoService.getRecentPhotos(clampedLimit, clampedLastNEvents));
    }

    /**
     * Public: list photos for an event (archive detail page). AC4.
     */
    @Override
    public ResponseEntity<List<EventPhotoResponse>> listEventPhotos(String eventCode) {
        return ResponseEntity.ok(photoService.listPhotos(eventCode));
    }

    /**
     * Organizer: delete a photo (DB record + S3 object, best-effort). AC3.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteEventPhoto(String eventCode, UUID photoId) {
        photoService.deletePhoto(eventCode, photoId);
        return ResponseEntity.noContent().build();
    }
}
