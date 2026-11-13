package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventPhoto;
import ch.batbern.events.exception.EventPhotoNotFoundException;
import ch.batbern.events.media.dto.generated.EventPhotoResponse;
import ch.batbern.events.repository.EventManagementUploadRepository;
import ch.batbern.events.repository.EventPhotoRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.api.DeterministicPaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Service for event photo management (list, delete, homepage sample).
 * <p>
 * Story 10.21: Event Photos Gallery. Uploads go through the upload contract, purpose
 * {@code event-photo} ({@link EventPhotoUploadHandler}, Story 17.11b).
 */
@Service
@Transactional
@Slf4j
public class EventPhotoService {

    private final EventPhotoRepository photoRepository;
    private final EventRepository eventRepository;
    private final EventManagementUploadRepository uploadRepository;
    private final S3Client s3Client;
    private final String bucketName;

    public EventPhotoService(
            EventPhotoRepository photoRepository,
            EventRepository eventRepository,
            EventManagementUploadRepository uploadRepository,
            S3Client s3Client,
            @Value("${aws.s3.bucket-name:batbern-development-company-logos}") String bucketName) {
        this.photoRepository = photoRepository;
        this.eventRepository = eventRepository;
        this.uploadRepository = uploadRepository;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    /**
     * Delete a photo: remove DB record and S3 object.
     * S3 delete is best-effort — failures are logged but do not block DB delete.
     * The photo's upload row (uploadId = photo id, Story 17.11b) goes with it: ASSOCIATED rows never
     * expire, so it would stay forever. Photos stored before 17.11b have none.
     * AC3 — Delete
     */
    public void deletePhoto(String eventCode, UUID photoId) {
        EventPhoto photo = photoRepository.findByIdAndEventCode(photoId, eventCode)
                .orElseThrow(() -> new EventPhotoNotFoundException(photoId.toString()));

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(photo.getS3Key())
                    .build());
        } catch (Exception e) {
            log.warn("S3 delete failed for key {}: {}", photo.getS3Key(), e.getMessage());
        }

        photoRepository.delete(photo);
        uploadRepository.findByUploadId(photoId.toString())
                .filter(upload -> EventPhotoUploadHandler.PURPOSE.equals(upload.getPurpose()))
                .ifPresent(uploadRepository::delete);
        log.info("Photo {} deleted from event {}", photoId, eventCode);
    }

    /**
     * List all photos for an event, ordered by sort_order then upload time.
     * AC4 — Public photo listing (no auth required)
     */
    @Transactional(readOnly = true)
    public List<EventPhotoResponse> listPhotos(String eventCode) {
        return photoRepository.findByEventCodeOrderBySortOrderAscUploadedAtAsc(eventCode)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Return up to {@code limit} randomly-sampled photos from the last {@code lastNEvents} events
     * that have already taken place. AC5 — Recent photos endpoint (homepage use)
     */
    @Transactional(readOnly = true)
    public List<EventPhotoResponse> getRecentPhotos(int limit, int lastNEvents) {
        List<Event> recentEvents = eventRepository
                .findByDateBeforeOrderByDateDesc(Instant.now(), DeterministicPaging.orderedByQuery(0, lastNEvents));

        if (recentEvents.isEmpty()) {
            return List.of();
        }

        List<String> eventCodes = recentEvents.stream()
                .map(Event::getEventCode)
                .toList();

        List<EventPhoto> all = photoRepository.findByEventCodeIn(eventCodes);

        if (all.size() <= limit) {
            return all.stream().map(this::toResponse).toList();
        }

        List<EventPhoto> shuffled = new ArrayList<>(all);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, limit).stream().map(this::toResponse).toList();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private EventPhotoResponse toResponse(EventPhoto photo) {
        OffsetDateTime uploadedAt = photo.getUploadedAt() != null
                ? photo.getUploadedAt().atOffset(ZoneOffset.UTC)
                : null;
        return EventPhotoResponse.builder()
                .id(photo.getId())
                .eventCode(photo.getEventCode())
                .displayUrl(photo.getDisplayUrl())
                .filename(photo.getFilename())
                .uploadedBy(photo.getUploadedBy())
                .uploadedAt(uploadedAt)
                .sortOrder(photo.getSortOrder())
                .build();
    }

}
