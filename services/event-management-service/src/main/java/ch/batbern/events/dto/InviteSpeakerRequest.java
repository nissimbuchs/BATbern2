package ch.batbern.events.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Request DTO for inviting a speaker to an event.
 * Story 6.1b: Speaker Invitation System (AC1)
 *
 * @param email Speaker's email address (required)
 * @param firstName Speaker's first name (optional - used for user creation if not found)
 * @param lastName Speaker's last name (optional - used for user creation if not found)
 * @param company Speaker's company name (optional)
 * @param sessionId Session to assign speaker to (optional)
 * @param notes Notes about the speaker (optional)
 */
public record InviteSpeakerRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        String firstName,

        String lastName,

        String company,

        UUID sessionId,

        String notes
) {
    /**
     * Get display name for speaker, falling back to email if no name provided.
     */
    public String getDisplayName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        }
        return email;
    }
}
