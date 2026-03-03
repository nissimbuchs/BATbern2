package ch.batbern.events.service;

import ch.batbern.events.domain.EventTeaserImage;
import ch.batbern.events.dto.generated.TeaserImageItem;
import ch.batbern.events.dto.generated.TeaserImageUploadUrlResponse;
import ch.batbern.events.exception.TeaserImageLimitExceededException;
import ch.batbern.events.exception.TeaserImageNotFoundException;
import ch.batbern.events.repository.EventTeaserImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * Service for event teaser image management (upload, confirm, delete).
 * <p>
 * Story 10.22: Event Teaser Images for Moderator Presentation Page
 * <p>
 * Uses the 3-phase presigned PUT pattern:
 * 1. generateUploadUrl — generate presigned S3 URL
 * 2. Client PUT directly to S3 (no backend involved)
 * 3. confirmUpload — verify S3 presence, persist EventTeaserImage record
 * <p>
 * Pattern follows SpeakerProfilePhotoService and EventPhotoService.
 * ADR-002: Entity-specific service (not GenericLogoService) because teaser images
 * always target an EXISTING event — no circular dependency problem.
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
     * AC2 — Upload flow; H1 — content type validation
     */
    public TeaserImageUploadUrlResponse generateUploadUrl(String eventCode,
                                                          String contentType,
                                                          String fileName) {
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported content type: " + contentType
                    + ". Allowed: image/jpeg, image/png, image/webp, image/svg+xml");
        }
        String ext = resolveExtension(fileName, contentType);
        String s3Key = String.format("events/%s/teaser/%s.%s", eventCode, UUID.randomUUID(), ext);

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
     * AC2 — Upload flow; AC6 — max-limit guard; M1 — pessimistic lock prevents race conditions.
     *
     * A single SELECT ... FOR UPDATE replaces the separate countByEventCode +
     * findMaxDisplayOrderByEventCode calls, ensuring that concurrent confirms for the
     * same event serialize correctly (no over-limit bypass, no duplicate displayOrder).
     */
    public TeaserImageItem confirmUpload(String eventCode, String s3Key) {
        // Lock existing teaser-image rows for this event so concurrent confirms serialize.
        List<EventTeaserImage> existing = teaserImageRepository.findByEventCodeForUpdate(eventCode);

        // AC6: max-limit guard (check BEFORE S3 headObject)
        if (existing.size() >= MAX_TEASER_IMAGES) {
            throw new TeaserImageLimitExceededException(eventCode, MAX_TEASER_IMAGES);
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
        log.info("Teaser image {} confirmed for event {}, displayOrder={}", saved.getId(), eventCode, displayOrder);

        return toItem(saved);
    }

    /**
     * Delete a teaser image: remove DB record and S3 object.
     * S3 delete is best-effort — failures are logged but do not block DB delete.
     * AC3 — Delete
     */
    public void deleteTeaserImage(String eventCode, UUID imageId) {
        EventTeaserImage image = teaserImageRepository.findByIdAndEventCode(imageId, eventCode)
                .orElseThrow(() -> new TeaserImageNotFoundException(imageId.toString()));

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(image.getS3Key())
                    .build());
        } catch (Exception e) {
            log.warn("S3 delete failed for teaser image key {}: {}", image.getS3Key(), e.getMessage());
        }

        teaserImageRepository.delete(image);
        log.info("Teaser image {} deleted from event {}", imageId, eventCode);
    }

    /**
     * List all teaser images for an event, ordered by displayOrder ascending.
     * Used by EventMapper enrichment (AC4 — teaserImages in EventResponse).
     */
    @Transactional(readOnly = true)
    public List<TeaserImageItem> listByEventCode(String eventCode) {
        return teaserImageRepository.findByEventCodeOrderByDisplayOrderAsc(eventCode)
                .stream()
                .map(this::toItem)
                .toList();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private TeaserImageItem toItem(EventTeaserImage entity) {
        return new TeaserImageItem(
                entity.getId(),
                URI.create(entity.getImageUrl()),
                entity.getDisplayOrder()
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
