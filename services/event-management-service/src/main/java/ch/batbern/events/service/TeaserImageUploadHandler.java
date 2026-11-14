package ch.batbern.events.service;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.upload.UploadException;
import ch.batbern.shared.upload.UploadPurposeHandler;
import ch.batbern.shared.upload.UploadRecord;
import ch.batbern.shared.upload.UploadRules;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Purpose {@code teaser-image} (Story 17.11c): a teaser image of an event's moderator presentation,
 * or of the global set shown on every event ({@code eventCode} {@value EventTeaserImageService#GLOBAL_CODE}).
 * Organizers only; the target {@code eventCode} is required. At most 10 per scope, checked on create
 * and again when the confirm attaches. The confirm with the target creates the {@code event_teaser_images} row.
 * It replaces {@code POST /events/{eventCode}/teaser-images/upload-url} and {@code …/confirm}, where
 * the client sent the S3 key back, no size was signed and nothing was pending. Listing, position and
 * deleting stay in {@link EventTeaserImageService}.
 */
@Component
@RequiredArgsConstructor
public class TeaserImageUploadHandler implements UploadPurposeHandler {

    public static final String PURPOSE = "teaser-image";
    public static final String TARGET_TYPE = "EVENT_TEASER";

    /**
     * JPEG, PNG, WebP and SVG as before Story 17.11c. The 10 MB are new: the former presign signed no
     * size; it is the event photo limit.
     */
    private static final UploadRules RULES = new UploadRules(10L * 1024 * 1024,
            Set.of("image/jpeg", "image/png", "image/webp", "image/svg+xml"),
            Set.of("jpg", "jpeg", "png", "webp", "svg"));

    private final EventRepository eventRepository;
    private final EventTeaserImageService teaserImageService;
    private final CacheManager cacheManager;

    @Override
    public String purpose() {
        return PURPOSE;
    }

    @Override
    public UploadRules rules() {
        return RULES;
    }

    @Override
    public String targetKey() {
        return "eventCode";
    }

    @Override
    public String targetType() {
        return TARGET_TYPE;
    }

    @Override
    public boolean requiresTarget() {
        return true;
    }

    @Override
    public void checkMayUpload() {
        if (!UploadPurposeHandler.callerHasRole("ORGANIZER")) {
            throw UploadException.forbidden("Only organizers upload teaser images");
        }
    }

    /** The event exists, or the target is the global set. */
    @Override
    public void checkTarget(String eventCode) {
        String scope = EventTeaserImageService.scopeOf(eventCode);
        if (scope != null && eventRepository.findByEventCode(scope).isEmpty()) {
            throw UploadException.notFound("Event not found: " + eventCode);
        }
    }

    /**
     * The scope has room (409 otherwise), on reserve. The confirm checks it again in {@link #attach},
     * under the scope's lock, after the contract's own answers (404 unknown or other target, 409
     * attached).
     */
    @Override
    public void checkCanReserve(String eventCode) {
        teaserImageService.checkRoomFor(EventTeaserImageService.scopeOf(eventCode));
    }

    @Override
    public String tempKey(String uploadId, String extension) {
        return String.format("events/temp/%s/teaser-%s.%s", uploadId, uploadId, extension);
    }

    /**
     * {@code events/{eventCode}/teaser/{uploadId}.{ext}}, or {@code global/teaser/{uploadId}.{ext}}
     * for the global set: the layout of the teaser images stored before, {@code .jpg} for a
     * {@code .jpeg} file included.
     */
    @Override
    public String finalKey(UploadRecord upload, String eventCode) {
        String scope = EventTeaserImageService.scopeOf(eventCode);
        String extension = EventPhotoUploadHandler.keyExtension(upload);
        return scope == null
                ? String.format("global/teaser/%s.%s", upload.getUploadId(), extension)
                : String.format("events/%s/teaser/%s.%s", scope, upload.getUploadId(), extension);
    }

    /**
     * Creates the teaser image, last in display order, under the scope's lock (the limit again; a
     * refusal rolls the confirm back and the copied final object is deleted). Its {@code imageUrl} is
     * the upload's CDN URL. The event cache holds teaser images, so it is cleared after the commit.
     */
    @Override
    public void attach(UploadRecord upload, String eventCode) {
        teaserImageService.addUploaded(EventTeaserImageService.scopeOf(eventCode), upload.getS3Key(),
                upload.getFileUrl());
        CacheConfig.clearAfterCommit(cacheManager, CacheConfig.EVENT_WITH_INCLUDES_CACHE);
    }
}
