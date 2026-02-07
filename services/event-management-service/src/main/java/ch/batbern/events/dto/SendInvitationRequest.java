package ch.batbern.events.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request DTO for sending/resending an invitation email to a speaker.
 * Story 6.1b: Speaker Invitation System (AC3)
 * Story 6.1c: Added email field for speakers without email in database
 *
 * @param responseDeadline Deadline for speaker to respond (required, must be in the future)
 * @param contentDeadline Deadline for speaker to submit content (optional, must be after responseDeadline)
 * @param locale Preferred language for the email (optional, defaults to German)
 * @param email Email address to use (optional, for speakers without email in database)
 */
public record SendInvitationRequest(
        @NotNull(message = "Response deadline is required")
        @Future(message = "Response deadline must be in the future")
        LocalDate responseDeadline,

        LocalDate contentDeadline,

        String locale,

        @jakarta.validation.constraints.Email(message = "Invalid email format")
        String email
) {
    /**
     * Validates that content deadline is after response deadline if provided.
     *
     * @return true if deadlines are valid
     */
    public boolean areDeadlinesValid() {
        if (contentDeadline == null) {
            return true;
        }
        return contentDeadline.isAfter(responseDeadline);
    }
}
