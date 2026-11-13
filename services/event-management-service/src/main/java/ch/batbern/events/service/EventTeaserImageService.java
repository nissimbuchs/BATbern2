package ch.batbern.events.service;

import ch.batbern.events.core.dto.generated.TeaserImageItem;
import ch.batbern.events.core.dto.generated.TeaserImagePresentationPosition;
import ch.batbern.events.domain.EventTeaserImage;
import ch.batbern.events.exception.TeaserImageLimitExceededException;
import ch.batbern.events.exception.TeaserImageNotFoundException;
import ch.batbern.events.repository.EventManagementUploadRepository;
import ch.batbern.events.repository.EventTeaserImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Teaser images of an event (eventCode != null) or the global set (eventCode == null, shown on all
 * event presentations): list, presentation position, delete, and the row a confirmed upload creates.
 * <p>
 * Uploads go through the upload contract, purpose {@code teaser-image}
 * ({@link TeaserImageUploadHandler}, Story 17.11c), with the target {@code eventCode};
 * {@link #GLOBAL_CODE} names the global set there and in the paths.
 */
@Service
@Transactional
@Slf4j
public class EventTeaserImageService {

    /** The event code that names the global set, in the paths and as an upload target. */
    public static final String GLOBAL_CODE = "_global";

    private static final int MAX_TEASER_IMAGES = 10;

    private final EventTeaserImageRepository teaserImageRepository;
    private final EventManagementUploadRepository uploadRepository;
    private final S3Client s3Client;
    private final String bucketName;

    public EventTeaserImageService(
            EventTeaserImageRepository teaserImageRepository,
            EventManagementUploadRepository uploadRepository,
            S3Client s3Client,
            @Value("${aws.s3.bucket-name:batbern-development-company-logos}") String bucketName) {
        this.teaserImageRepository = teaserImageRepository;
        this.uploadRepository = uploadRepository;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    /** The stored event code of a path or target value: null for {@link #GLOBAL_CODE}. */
    @Nullable
    public static String scopeOf(String eventCode) {
        return GLOBAL_CODE.equals(eventCode) ? null : eventCode;
    }

    /**
     * Refuses a new teaser image when the scope already has {@value #MAX_TEASER_IMAGES} (409). Checked
     * when the upload is reserved; {@link #addUploaded} checks again under the scope's lock.
     * @param eventCode event code, or null for global images
     */
    @Transactional(readOnly = true)
    public void checkRoomFor(@Nullable String eventCode) {
        long count = eventCode != null
                ? teaserImageRepository.countByEventCode(eventCode)
                : teaserImageRepository.countByEventCodeIsNull();
        if (count >= MAX_TEASER_IMAGES) {
            throw limitExceeded(eventCode);
        }
    }

    /**
     * Creates the teaser image of a confirmed {@code teaser-image} upload, last in display order.
     * Takes the scope's transaction-scoped advisory lock first ({@code pg_advisory_xact_lock}, keyed
     * on the event code or {@link #GLOBAL_CODE}), so concurrent confirms for one scope run one after
     * the other and each counts the rows the one before committed: the limit and a unique display
     * order hold, also for an empty scope. The lock is released when the confirm transaction ends.
     * @param eventCode event code, or null for global images
     */
    public EventTeaserImage addUploaded(@Nullable String eventCode, String s3Key, String imageUrl) {
        teaserImageRepository.lockScope(eventCode != null ? eventCode : GLOBAL_CODE);
        List<EventTeaserImage> existing = eventCode != null
                ? teaserImageRepository.findByEventCodeOrderByDisplayOrderAsc(eventCode)
                : teaserImageRepository.findByEventCodeIsNullOrderByDisplayOrderAsc();
        if (existing.size() >= MAX_TEASER_IMAGES) {
            throw limitExceeded(eventCode);
        }
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
        log.info("Teaser image {} added for {}, displayOrder={}",
                saved.getId(), eventCode != null ? "event " + eventCode : "global", displayOrder);
        return saved;
    }

    private static TeaserImageLimitExceededException limitExceeded(@Nullable String eventCode) {
        return eventCode != null
                ? new TeaserImageLimitExceededException(eventCode, MAX_TEASER_IMAGES)
                : new TeaserImageLimitExceededException(MAX_TEASER_IMAGES);
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
        // The upload row of an image added on the upload contract (Story 17.11c): ASSOCIATED rows
        // never expire, so it would stay forever. Images added before 17.11c have none.
        uploadRepository.findByPurposeAndS3Key(TeaserImageUploadHandler.PURPOSE, image.getS3Key())
                .ifPresent(uploadRepository::delete);
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
}
