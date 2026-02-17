package ch.batbern.events.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Story 9.3 Task 5.2: Request DTO for speaker magic link re-send.
 * M4 fix: @Email prevents injection into Cognito ListUsers filter via findUserByEmail.
 *
 * @param email Speaker's email address (validated RFC 5322)
 */
public record SpeakerMagicLinkRequestDto(
        @NotBlank @Email String email
) {
}
