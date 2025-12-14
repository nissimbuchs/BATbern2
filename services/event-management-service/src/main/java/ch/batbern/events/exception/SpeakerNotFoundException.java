package ch.batbern.events.exception;

import ch.batbern.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Exception thrown when a speaker is not found in the speaker pool.
 * Story 5.3: Speaker Outreach Tracking
 *
 * Extends shared-kernel NotFoundException for consistent error handling across services.
 */
public class SpeakerNotFoundException extends NotFoundException {

    public SpeakerNotFoundException(UUID speakerId) {
        super("Speaker", speakerId.toString());
    }

    public SpeakerNotFoundException(String message) {
        super(message);
    }
}
