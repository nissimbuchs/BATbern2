package ch.batbern.events.dto;

import ch.batbern.shared.types.SpeakerWorkflowState;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for speaker invitation.
 * Story 6.1b: Speaker Invitation System (AC1)
 *
 * @param speakerPoolId The ID of the created/found SpeakerPool entry
 * @param username The username of the speaker (existing or newly created)
 * @param email The speaker's email address
 * @param speakerName The display name of the speaker
 * @param status The current workflow status
 * @param created True if the SpeakerPool entry was newly created, false if existing
 * @param userCreated True if the User was newly created, false if existing
 * @param createdAt When the SpeakerPool entry was created
 */
public record InviteSpeakerResponse(
        UUID speakerPoolId,
        String username,
        String email,
        String speakerName,
        SpeakerWorkflowState status,
        boolean created,
        boolean userCreated,
        Instant createdAt
) {
    /**
     * Factory method for a newly created speaker invitation.
     */
    public static InviteSpeakerResponse created(
            UUID speakerPoolId,
            String username,
            String email,
            String speakerName,
            boolean userCreated,
            Instant createdAt
    ) {
        return new InviteSpeakerResponse(
                speakerPoolId,
                username,
                email,
                speakerName,
                SpeakerWorkflowState.IDENTIFIED,
                true,
                userCreated,
                createdAt
        );
    }

    /**
     * Factory method for an existing speaker found by email.
     */
    public static InviteSpeakerResponse existing(
            UUID speakerPoolId,
            String username,
            String email,
            String speakerName,
            SpeakerWorkflowState status,
            Instant createdAt
    ) {
        return new InviteSpeakerResponse(
                speakerPoolId,
                username,
                email,
                speakerName,
                status,
                false,
                false,
                createdAt
        );
    }
}
