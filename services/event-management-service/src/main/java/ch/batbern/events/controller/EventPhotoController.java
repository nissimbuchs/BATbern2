package ch.batbern.events.controller;

import ch.batbern.events.dto.EventPhotoConfirmRequestDto;
import ch.batbern.events.dto.EventPhotoResponseDto;
import ch.batbern.events.dto.EventPhotoUploadRequestDto;
import ch.batbern.events.dto.EventPhotoUploadResponseDto;
import ch.batbern.events.service.EventPhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for event photo management.
 * <p>
 * Story 10.21: Event Photos Gallery
 * <p>
 * Mixed auth: public GET endpoints + ORGANIZER-only write endpoints.
 * Note: /events/recent-photos (static) is registered before /events/{eventCode}/photos
 * (path variable) — Spring MVC resolves static segments first, so no conflict.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class EventPhotoController {

    private final EventPhotoService photoService;

    /**
     * Public: recent photos from last N events (homepage marquee).
     * Static path /events/recent-photos takes precedence over /events/{eventCode}/photos.
     * AC5
     */
    @GetMapping("/events/recent-photos")
    public ResponseEntity<List<EventPhotoResponseDto>> getRecentPhotos(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "5") int lastNEvents) {
        return ResponseEntity.ok(photoService.getRecentPhotos(limit, lastNEvents));
    }

    /**
     * Public: list photos for an event (archive detail page).
     * AC4
     */
    @GetMapping("/events/{eventCode}/photos")
    public ResponseEntity<List<EventPhotoResponseDto>> listPhotos(
            @PathVariable String eventCode) {
        return ResponseEntity.ok(photoService.listPhotos(eventCode));
    }

    /**
     * Organizer: request presigned PUT URL for photo upload (phase 1 of 3).
     * AC2
     */
    @PostMapping("/events/{eventCode}/photos/upload-url")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventPhotoUploadResponseDto> requestUploadUrl(
            @PathVariable String eventCode,
            @Valid @RequestBody EventPhotoUploadRequestDto request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(photoService.requestUploadUrl(eventCode, request, username));
    }

    /**
     * Organizer: confirm upload — verify S3 presence and persist record (phase 3 of 3).
     * AC2
     */
    @PostMapping("/events/{eventCode}/photos/confirm")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventPhotoResponseDto> confirmUpload(
            @PathVariable String eventCode,
            @Valid @RequestBody EventPhotoConfirmRequestDto request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        return ResponseEntity.ok(photoService.confirmUpload(eventCode, request, username));
    }

    /**
     * Organizer: delete a photo (DB record + S3 object, best-effort).
     * AC3
     */
    @DeleteMapping("/events/{eventCode}/photos/{photoId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deletePhoto(
            @PathVariable String eventCode,
            @PathVariable UUID photoId) {
        photoService.deletePhoto(eventCode, photoId);
        return ResponseEntity.noContent().build();
    }
}
