package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for submitting speaker content (Story 5.5 AC6-10).
 *
 * Used when an organizer submits presentation materials for an accepted speaker.
 * Creates a session with the presentation details and links the speaker.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitContentRequest {

    /**
     * Presentation title (required, becomes session.title).
     * AC7: Creates session with this title
     */
    @NotBlank(message = "Presentation title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String presentationTitle;

    /**
     * Presentation abstract (required, becomes session.description).
     * AC6: Max 1000 characters with character counter
     */
    @NotBlank(message = "Presentation abstract is required")
    @Size(max = 1000, message = "Abstract must not exceed 1000 characters")
    private String presentationAbstract;

    /**
     * Speaker username (if existing user selected).
     * AC2: Auto-populated when user selected from search
     * AC5: Stored in speaker_pool and session_users
     */
    private String username;

    /**
     * Speaker name (if creating new user).
     * AC3: Required when username is null
     */
    private String speakerName;

    /**
     * Speaker email (if creating new user).
     * AC3: Required when username is null
     * AC30: Used for duplicate prevention
     */
    private String email;

    /**
     * Speaker company (optional).
     * Stored in user profile via users-service
     */
    private String company;

    /**
     * Speaker bio (optional).
     * Stored in user profile via users-service
     */
    private String bio;
}
