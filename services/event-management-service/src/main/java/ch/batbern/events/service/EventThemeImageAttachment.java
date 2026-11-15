package ch.batbern.events.service;

import ch.batbern.events.domain.EventManagementUpload;
import ch.batbern.shared.upload.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The theme image of an event on the upload contract, media module: a CONFIRMED
 * {@code event-theme-image} upload of this service (Story 17.11), moved from {@code EventController}.
 */
@Component
@RequiredArgsConstructor
public class EventThemeImageAttachment implements EventThemeAttachment {

    private final UploadService<EventManagementUpload> uploadService;

    @Override
    public void requireAttachable(String uploadId, String currentUploadId) {
        if (uploadId != null && !uploadId.isBlank() && !uploadId.equals(currentUploadId)) {
            uploadService.requireConfirmed(EventThemeImageUploadHandler.PURPOSE, uploadId);
        }
    }

    @Override
    public String attach(String uploadId, String eventCode) {
        return uploadService.associate(EventThemeImageUploadHandler.PURPOSE, uploadId, eventCode).getFileUrl();
    }
}
