package ch.batbern.events.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request DTO for sending/resending an invitation email to a speaker.
 * Story 6.1b: Speaker Invitation System (AC3)
 *
 * @param responseDeadline Deadline for speaker to respond (required, must be in the future)
 * @param contentDeadline Deadline for speaker to submit content (optional, must be after responseDeadline)
 * @param locale Preferred language for the email (optional, defaults to German)
 */
public record SendInvitationRequest(
        @NotNull(message = "Response deadline is required")
        @Future(message = "Response deadline must be in the future")
        LocalDate responseDeadline,

        LocalDate contentDeadline,

        String locale
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
