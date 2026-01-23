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

    /**
     * Constructor for speaker not found by username in event.
     * Story 6.1b: Speaker Invitation System
     *
     * @param username the speaker's username
     * @param eventCode the event code
     */
    public SpeakerNotFoundException(String username, String eventCode) {
        super("Speaker with username '" + username + "' not found in event '" + eventCode + "'");
    }
}
