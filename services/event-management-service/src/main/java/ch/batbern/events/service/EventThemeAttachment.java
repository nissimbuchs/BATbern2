package ch.batbern.events.service;

/**
 * The theme image of an event (event core SPI), implemented by the media module on the upload
 * contract (Story 17.11).
 */
public interface EventThemeAttachment {

    /**
     * Checks a new theme image upload before the event is saved: 404 unknown, expired or of another
     * purpose, 409 not CONFIRMED. A blank id (remove) and the unchanged {@code currentUploadId} pass.
     */
    void requireAttachable(String uploadId, String currentUploadId);

    /**
     * Attaches the upload to the event and returns the image URL. Throws when storage fails; the
     * caller decides whether that fails the write.
     */
    String attach(String uploadId, String eventCode);
}
