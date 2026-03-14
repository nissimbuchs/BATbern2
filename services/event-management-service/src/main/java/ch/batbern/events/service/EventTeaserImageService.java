package ch.batbern.events.service;

import ch.batbern.events.domain.EventTeaserImage;
import ch.batbern.events.dto.generated.TeaserImageItem;
import ch.batbern.events.dto.generated.TeaserImagePresentationPosition;
import ch.batbern.events.dto.generated.TeaserImageUploadUrlResponse;
import ch.batbern.events.exception.TeaserImageLimitExceededException;
import ch.batbern.events.exception.TeaserImageNotFoundException;
import ch.batbern.events.repository.EventTeaserImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for teaser image management (upload, confirm, delete).
 * <p>
 * Handles both event-specific images (eventCode != null) and global images
 * (eventCode == null, shown on all event presentations).
 * <p>
 * Uses the 3-phase presigned PUT pattern:
 * 1. generateUploadUrl — generate presigned S3 URL
 * 2. Client PUT directly to S3 (no backend involved)
 * 3. confirmUpload — verify S3 presence, persist EventTeaserImage record
 */
@Service
@Transactional
@Slf4j
public class EventTeaserImageService {

    private static final int MAX_TEASER_IMAGES = 10;
    private static final int PRESIGNED_URL_EXPIRY_SECONDS = 900; // 15 min
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/svg+xml");

    private final EventTeaserImageRepository teaserImageRepository;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final String bucketName;
    private final String cloudFrontDomain;

    public EventTeaserImageService(
            EventTeaserImageRepository teaserImageRepository,
            S3Presigner s3Presigner,
            S3Client s3Client,
            @Value("${aws.s3.bucket-name:batbern-development-company-logos}") String bucketName,
            @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}") String cloudFrontDomain) {
        this.teaserImageRepository = teaserImageRepository;
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.cloudFrontDomain = cloudFrontDomain;
    }

    /**
     * Phase 1: Generate presigned PUT URL for direct S3 upload.
     * @param eventCode event code, or null for global images
     */
    public TeaserImageUploadUrlResponse generateUploadUrl(@Nullable String eventCode,
                                                          String contentType,
                                                          String fileName) {
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported content type: " + contentType
                    + ". Allowed: image/jpeg, image/png, image/webp, image/svg+xml");
        }
        String ext = resolveExtension(fileName, contentType);
        String s3Key = eventCode != null
                ? String.format("events/%s/teaser/%s.%s", eventCode, UUID.randomUUID(), ext)
                : String.format("global/teaser/%s.%s", UUID.randomUUID(), ext);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(PRESIGNED_URL_EXPIRY_SECONDS))
                .putObjectRequest(putRequest)
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

        return new TeaserImageUploadUrlResponse(
                URI.create(uploadUrl),
                s3Key,
                PRESIGNED_URL_EXPIRY_SECONDS
        );
    }

    /**
     * Phase 3: Confirm upload — verify S3 presence, persist EventTeaserImage.
     * @param eventCode event code, or null for global images
     */
    public TeaserImageItem confirmUpload(@Nullable String eventCode, String s3Key) {
        // Validate s3Key prefix matches the scope
        String expectedPrefix = eventCode != null
                ? String.format("events/%s/teaser/", eventCode)
                : "global/teaser/";
        if (s3Key == null || !s3Key.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("s3Key must start with " + expectedPrefix);
        }

        // Lock existing rows so concurrent confirms serialize
        List<EventTeaserImage> existing = eventCode != null
                ? teaserImageRepository.findByEventCodeForUpdate(eventCode)
                : teaserImageRepository.findGlobalForUpdate();

        if (existing.size() >= MAX_TEASER_IMAGES) {
            if (eventCode != null) {
                throw new TeaserImageLimitExceededException(eventCode, MAX_TEASER_IMAGES);
            } else {
                throw new TeaserImageLimitExceededException(MAX_TEASER_IMAGES);
            }
        }

        // Verify S3 presence
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new IllegalStateException("Uploaded file not found in S3: " + s3Key);
        }

        String imageUrl = cloudFrontDomain + "/" + s3Key;
        int displayOrder = existing.stream()
                .mapToInt(EventTeaserImage::getDisplayOrder)
                .max()
                .orElse(-1) + 1;

        EventTeaserImage entity = new EventTeaserImage();
        entity.setEventCode(eventCode);
        entity.setS3Key(s3Key);
        entity.setImageUrl(imageUrl);
        entity.setDisplayOrder(displayOrder);

        EventTeaserImage saved = teaserImageRepository.save(entity);
        log.info("Teaser image {} confirmed for {}, displayOrder={}",
                saved.getId(), eventCode != null ? "event " + eventCode : "global", displayOrder);

        return toItem(saved);
    }

    /**
     * Delete a teaser image: remove DB record and S3 object (best-effort).
     * @param eventCode event code, or null for global images
     */
    public void deleteTeaserImage(@Nullable String eventCode, UUID imageId) {
        EventTeaserImage image = findImage(eventCode, imageId);

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(image.getS3Key())
                    .build());
        } catch (Exception e) {
            log.warn("S3 delete failed for teaser image key {}: {}", image.getS3Key(), e.getMessage());
        }

        teaserImageRepository.delete(image);
        log.info("Teaser image {} deleted from {}", imageId,
                eventCode != null ? "event " + eventCode : "global");
    }

    /**
     * List teaser images ordered by displayOrder ascending.
     * @param eventCode event code, or null for global images
     */
    @Transactional(readOnly = true)
    public List<TeaserImageItem> listByEventCode(@Nullable String eventCode) {
        List<EventTeaserImage> images = eventCode != null
                ? teaserImageRepository.findByEventCodeOrderByDisplayOrderAsc(eventCode)
                : teaserImageRepository.findByEventCodeIsNullOrderByDisplayOrderAsc();
        return images.stream().map(this::toItem).toList();
    }

    /**
     * Update the presentation position of a teaser image.
     * @param eventCode event code, or null for global images
     */
    public TeaserImageItem updatePresentationPosition(@Nullable String eventCode, UUID imageId,
                                                      TeaserImagePresentationPosition position) {
        EventTeaserImage image = findImage(eventCode, imageId);
        image.setPresentationPosition(position.getValue());
        EventTeaserImage saved = teaserImageRepository.save(image);
        log.info("Teaser image {} position updated to {} for {}",
                imageId, position.getValue(), eventCode != null ? "event " + eventCode : "global");
        return toItem(saved);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private EventTeaserImage findImage(@Nullable String eventCode, UUID imageId) {
        return (eventCode != null
                ? teaserImageRepository.findByIdAndEventCode(imageId, eventCode)
                : teaserImageRepository.findByIdAndEventCodeIsNull(imageId))
                .orElseThrow(() -> new TeaserImageNotFoundException(imageId.toString()));
    }

    private TeaserImageItem toItem(EventTeaserImage entity) {
        return new TeaserImageItem(
                entity.getId(),
                URI.create(entity.getImageUrl()),
                entity.getDisplayOrder(),
                TeaserImagePresentationPosition.fromValue(entity.getPresentationPosition())
        );
    }

    private String resolveExtension(String fileName, String contentType) {
        if (fileName != null && fileName.contains(".")) {
            String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            if (Set.of("jpg", "jpeg", "png", "webp", "svg").contains(ext)) {
                return ext.equals("jpeg") ? "jpg" : ext;
            }
        }
        return switch (contentType != null ? contentType : "") {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            default -> "jpg";
        };
    }
}
