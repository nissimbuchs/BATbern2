package ch.batbern.events.exception;

import ch.batbern.shared.upload.UploadException;
import org.springframework.http.HttpStatus;

/**
 * The event (or the global set) already has the maximum of teaser images (10, Story 10.22 AC6).
 * Thrown by the {@code teaser-image} upload on create and on confirm (Story 17.11c), so it answers
 * as the upload contract does for a full target: 409 with this message.
 */
public class TeaserImageLimitExceededException extends UploadException {

    public TeaserImageLimitExceededException(String eventCode, int maxImages) {
        super(HttpStatus.CONFLICT, "Maximum of " + maxImages + " teaser images reached for event: " + eventCode);
    }

    public TeaserImageLimitExceededException(int maxImages) {
        super(HttpStatus.CONFLICT, "Maximum of " + maxImages + " global teaser images reached");
    }
}
