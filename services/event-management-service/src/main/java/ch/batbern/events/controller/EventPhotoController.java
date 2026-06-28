package ch.batbern.events.controller;

import ch.batbern.events.media.api.generated.EventPhotosApi;
import ch.batbern.events.media.dto.generated.EventPhotoConfirmRequest;
import ch.batbern.events.media.dto.generated.EventPhotoResponse;
import ch.batbern.events.media.dto.generated.EventPhotoUploadRequest;
import ch.batbern.events.media.dto.generated.EventPhotoUploadResponse;
import ch.batbern.events.security.SecurityContextHelper;
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
 * Mixed auth: public GET endpoints + ORGANIZER-only write endpoints.
 * Note: /events/recent-photos (static) is registered before /events/{eventCode}/photos
 * (path variable) — Spring MVC resolves static segments first, so no conflict.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class EventPhotoController implements EventPhotosApi {

    private final EventPhotoService photoService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * Public: recent photos from last N events (homepage marquee). AC5.
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
     * Organizer: request presigned PUT URL for photo upload (phase 1 of 3). AC2.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventPhotoUploadResponse> requestEventPhotoUploadUrl(
            String eventCode,
            EventPhotoUploadRequest eventPhotoUploadRequest) {
        String username = resolveUsername();
        return ResponseEntity.ok(photoService.requestUploadUrl(eventCode, eventPhotoUploadRequest, username));
    }

    /**
     * Organizer: confirm upload — verify S3 presence and persist record (phase 3 of 3). AC2.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventPhotoResponse> confirmEventPhotoUpload(
            String eventCode,
            EventPhotoConfirmRequest eventPhotoConfirmRequest) {
        String username = resolveUsername();
        return ResponseEntity.ok(photoService.confirmUpload(eventCode, eventPhotoConfirmRequest, username));
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

    /**
     * The uploader's CANONICAL username (ADR-003), or {@code "system"} when unauthenticated.
     * Uses {@link SecurityContextHelper#getCurrentUsernameOrNull()} (custom:username claim +
     * Pattern 3b twin DB fallback) rather than {@code authentication.getName()}, which returns
     * the Cognito {@code sub} (a UUID) and would mis-attribute uploaded photos.
     */
    private String resolveUsername() {
        String username = securityContextHelper.getCurrentUsernameOrNull();
        return username != null ? username : "system";
    }
}
