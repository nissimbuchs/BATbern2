package ch.batbern.events.service;

import ch.batbern.events.domain.EventPhoto;
import ch.batbern.events.repository.EventPhotoRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.shared.upload.UploadException;
import ch.batbern.shared.upload.UploadPurposeHandler;
import ch.batbern.shared.upload.UploadRecord;
import ch.batbern.shared.upload.UploadRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Purpose {@code event-photo} (Story 17.11b): a photo of the event's gallery, organizers only. The
 * target {@code eventCode} is required, as the event always exists; the confirm with it creates the
 * {@code event_photos} row. It replaces {@code POST /events/{eventCode}/photos/upload-url} and
 * {@code …/photos/confirm}, where the client sent the S3 key back and nothing was pending.
 * Listing and deleting stay in {@link EventPhotoService}.
 */
@Component
@RequiredArgsConstructor
public class EventPhotoUploadHandler implements UploadPurposeHandler {

    public static final String PURPOSE = "event-photo";
    public static final String TARGET_TYPE = "EVENT";

    /** As before Story 17.11b: JPEG, PNG and WebP up to 10 MB. */
    private static final UploadRules RULES = new UploadRules(10L * 1024 * 1024,
            Set.of("image/jpeg", "image/png", "image/webp"), Set.of("jpg", "jpeg", "png", "webp"));

    private final EventRepository eventRepository;
    private final EventPhotoRepository photoRepository;
    private final SecurityContextHelper securityContextHelper;

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
            throw UploadException.forbidden("Only organizers upload event photos");
        }
    }

    @Override
    public void checkTarget(String eventCode) {
        if (eventRepository.findByEventCode(eventCode).isEmpty()) {
            throw UploadException.notFound("Event not found: " + eventCode);
        }
    }

    @Override
    public String tempKey(String uploadId, String extension) {
        return String.format("events/temp/%s/photo-%s.%s", uploadId, uploadId, extension);
    }

    /**
     * {@code events/{eventCode}/photos/{uploadId}.{ext}}: the layout of the photos stored before,
     * including their {@code .jpg} for a {@code .jpeg} file.
     */
    @Override
    public String finalKey(UploadRecord upload, String eventCode) {
        return String.format("events/%s/photos/%s.%s", eventCode, upload.getUploadId(), keyExtension(upload));
    }

    /** As before Story 17.11b: {@code jpeg} is stored as {@code jpg}. */
    static String keyExtension(UploadRecord upload) {
        String extension = upload.getFileExtension();
        return "jpeg".equals(extension) ? "jpg" : extension;
    }

    /**
     * Creates the gallery row; the photo's id is the upload's. The file name is {@code photo.{ext}}
     * (the original name is not kept, Story 17.11d adds it). The uploader is the confirming organizer
     * by username (ADR-003); {@code createdBy} of the upload is the JWT subject.
     */
    @Override
    public void attach(UploadRecord upload, String eventCode) {
        String username = securityContextHelper.getCurrentUsernameOrNull();
        photoRepository.save(EventPhoto.builder()
                .id(UUID.fromString(upload.getUploadId()))
                .newEntity(true)
                .eventCode(eventCode)
                .s3Key(upload.getS3Key())
                .displayUrl(upload.getFileUrl())
                .filename("photo." + keyExtension(upload))
                .uploadedAt(Instant.now())
                .uploadedBy(username != null ? username : "system")
                .sortOrder(0)
                .build());
    }
}
