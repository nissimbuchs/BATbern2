package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.EventPhoto;
import ch.batbern.events.dto.EventPhotoConfirmRequestDto;
import ch.batbern.events.dto.EventPhotoResponseDto;
import ch.batbern.events.dto.EventPhotoUploadRequestDto;
import ch.batbern.events.dto.EventPhotoUploadResponseDto;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.exception.EventPhotoNotFoundException;
import ch.batbern.events.exception.InvalidFileTypeException;
import ch.batbern.events.exception.PhotoUploadNotFoundException;
import ch.batbern.events.repository.EventPhotoRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.utils.CloudFrontUrlBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for event photo management (upload, confirm, delete, list).
 * <p>
 * Story 10.21: Event Photos Gallery
 * <p>
 * Uses the 3-phase presigned PUT pattern:
 * 1. requestUploadUrl — generate presigned S3 URL
 * 2. Client PUT directly to S3 (no backend involved)
 * 3. confirmUpload — verify S3 presence, persist EventPhoto record
 */
@Service
@Transactional
@Slf4j
public class EventPhotoService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final int PRESIGNED_URL_EXPIRY_SECONDS = 900; // 15 min
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    private final EventPhotoRepository photoRepository;
    private final EventRepository eventRepository;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final String bucketName;
    private final String cloudFrontDomain;

    public EventPhotoService(
            EventPhotoRepository photoRepository,
            EventRepository eventRepository,
            S3Presigner s3Presigner,
            S3Client s3Client,
            @Value("${aws.s3.bucket-name:batbern-development-company-logos}") String bucketName,
            @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}") String cloudFrontDomain) {
        this.photoRepository = photoRepository;
        this.eventRepository = eventRepository;
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.cloudFrontDomain = cloudFrontDomain;
    }

    /**
     * Phase 1: Generate presigned PUT URL for direct S3 upload.
     * AC2 — Upload flow
     */
    public EventPhotoUploadResponseDto requestUploadUrl(String eventCode,
                                                        EventPhotoUploadRequestDto request,
                                                        String uploaderUsername) {
        eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));

        String contentType = request.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new InvalidFileTypeException(
                    "Allowed content types: image/jpeg, image/png, image/webp");
        }

        if (request.getFileSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFileTypeException(
                    "File size exceeds maximum allowed size of 10 MB");
        }

        UUID photoId = UUID.randomUUID();
        String ext = resolveExtension(request.getFilename(), request.getContentType());
        String s3Key = String.format("events/%s/photos/%s.%s", eventCode, photoId, ext);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(request.getContentType())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(PRESIGNED_URL_EXPIRY_SECONDS))
                .putObjectRequest(putRequest)
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

        return EventPhotoUploadResponseDto.builder()
                .photoId(photoId)
                .uploadUrl(uploadUrl)
                .s3Key(s3Key)
                .expiresIn(PRESIGNED_URL_EXPIRY_SECONDS)
                .build();
    }

    /**
     * Phase 3: Confirm upload — verify S3 presence, persist EventPhoto record.
     * AC2 — Upload flow
     */
    public EventPhotoResponseDto confirmUpload(String eventCode,
                                               EventPhotoConfirmRequestDto request,
                                               String uploaderUsername) {
        String expectedPrefix = String.format("events/%s/photos/", eventCode);
        if (request.getS3Key() == null || !request.getS3Key().startsWith(expectedPrefix)) {
            throw new InvalidFileTypeException(
                    "Invalid s3Key: must belong to event " + eventCode);
        }

        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(request.getS3Key())
                    .build());
        } catch (NoSuchKeyException e) {
            throw new PhotoUploadNotFoundException(request.getPhotoId().toString());
        }

        String displayUrl = CloudFrontUrlBuilder.buildUrl(
                cloudFrontDomain, bucketName, request.getS3Key());
        String filename = request.getS3Key()
                .substring(request.getS3Key().lastIndexOf('/') + 1);

        EventPhoto photo = EventPhoto.builder()
                .id(request.getPhotoId())
                .eventCode(eventCode)
                .s3Key(request.getS3Key())
                .displayUrl(displayUrl)
                .filename(filename)
                .uploadedAt(Instant.now())
                .uploadedBy(uploaderUsername)
                .sortOrder(0)
                .build();

        return toResponseDto(photoRepository.save(photo));
    }

    /**
     * Delete a photo: remove DB record and S3 object.
     * S3 delete is best-effort — failures are logged but do not block DB delete.
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
        log.info("Photo {} deleted from event {}", photoId, eventCode);
    }

    /**
     * List all photos for an event, ordered by sort_order then upload time.
     * AC4 — Public photo listing (no auth required)
     */
    @Transactional(readOnly = true)
    public List<EventPhotoResponseDto> listPhotos(String eventCode) {
        return photoRepository.findByEventCodeOrderBySortOrderAscUploadedAtAsc(eventCode)
                .stream()
                .map(this::toResponseDto)
                .toList();
    }

    /**
     * Return up to {@code limit} randomly-sampled photos from the last {@code lastNEvents} events.
     * AC5 — Recent photos endpoint (homepage use)
     */
    @Transactional(readOnly = true)
    public List<EventPhotoResponseDto> getRecentPhotos(int limit, int lastNEvents) {
        List<Event> recentEvents = eventRepository
                .findTopByOrderByDateDesc(PageRequest.of(0, lastNEvents));

        if (recentEvents.isEmpty()) {
            return List.of();
        }

        List<String> eventCodes = recentEvents.stream()
                .map(Event::getEventCode)
                .toList();

        List<EventPhoto> all = photoRepository.findByEventCodeIn(eventCodes);

        if (all.size() <= limit) {
            return all.stream().map(this::toResponseDto).toList();
        }

        List<EventPhoto> shuffled = new ArrayList<>(all);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, limit).stream().map(this::toResponseDto).toList();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private EventPhotoResponseDto toResponseDto(EventPhoto photo) {
        return EventPhotoResponseDto.builder()
                .id(photo.getId())
                .eventCode(photo.getEventCode())
                .displayUrl(photo.getDisplayUrl())
                .filename(photo.getFilename())
                .uploadedBy(photo.getUploadedBy())
                .uploadedAt(photo.getUploadedAt())
                .sortOrder(photo.getSortOrder())
                .build();
    }

    private String resolveExtension(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
            if (Set.of("jpg", "jpeg", "png", "webp").contains(ext)) {
                return ext.equals("jpeg") ? "jpg" : ext;
            }
        }
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
